package com.flx_apps.digitaldetox.system_integration

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import timber.log.Timber

/**
 * How the [ScreenFilterOverlay] should currently look.
 * @param washAlpha How strongly the gray wash is composited over the screen (0..1).
 * @param blurDp Radius of the blur applied to everything behind the overlay, in dp. 0 disables it.
 */
data class ScreenFilterSpec(
    val washAlpha: Float, val blurDp: Float
)

/**
 * A passive full-screen layer that makes whatever is below it duller: a gray wash plus, on
 * Android 12 and newer, a blur of everything behind it.
 *
 * This is what runs instead of the real grayscale filter when DetoxDroid does not hold
 * WRITE_SECURE_SETTINGS. It cannot desaturate the screen the way the system daltonizer does: an
 * overlay window is only ever composited *onto* the screen, so the most it can do is pull every
 * pixel towards one color. Mixing in a neutral gray at alpha a scales the distance between the
 * color channels by (1 - a), which drains color and contrast at the same rate. Colors survive at
 * usable alphas (a red badge at a = 0.45 is still recognisably red), the screen just looks flat
 * and dead, which is the point.
 *
 * The wash and the blur live in two separate windows, and the blur one is a [Dialog]. Both of
 * those are worked around rather than chosen.
 *
 * [WindowManager.LayoutParams.FLAG_BLUR_BEHIND] is the obvious way to blur what is behind a
 * window, and it cannot be used here: the window manager implements it by inserting a dim layer of
 * its own above the app, that layer belongs to the system (uid 1000) and reports
 * `mode=BLOCK_UNTRUSTED`, and that mode makes the input dispatcher treat it as fully obscuring no
 * matter which opacity it carries. Every touch meant for the app underneath is then dropped, and
 * nothing an app sets on its own window reaches that layer. `Window.setBackgroundBlurRadius()`
 * takes the other route, straight onto the window's own surface with no dim layer in between, so
 * the blur needs a window that comes with a [Window] object.
 *
 * Both windows are attached to the accessibility service whenever it runs, which means they need
 * no permission of their own and the system tears them down together with the service. That also
 * makes them trusted overlays, which is why the input dispatcher never counts them as obscuring;
 * the opacity cap below still matters for the [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY]
 * fallback, which is not trusted.
 */
object ScreenFilterOverlay {
    /**
     * Upper bound for [ScreenFilterSpec.washAlpha]. From 0.8 up Android treats the window as
     * obscuring and drops every touch meant for the app below (see
     * `maximum_obscuring_opacity_for_touch`), and anything near that is unreadable anyway.
     */
    const val MAX_WASH_ALPHA = 0.75f


    /**
     * The wash color, painted opaque. A mid-dark neutral gray: light grays fog up dark-mode apps
     * until they look broken, and a tinted wash reads as a photo filter rather than as "this app
     * is boring now". How much of it ends up on screen is the window alpha.
     */
    private const val WASH_COLOR = 0xFF4D4D4D.toInt()

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Added first, so the flat wash on top of it is not itself blurred. */
    private val blurWindow = BlurWindow()
    private val washWindow = FilterWindow(WASH_COLOR)

    /**
     * Shows, updates or (with a null [spec]) removes the filter. Safe to call from any thread and
     * a no-op when nothing changed, so it can be called on every window event.
     */
    fun apply(context: Context, spec: ScreenFilterSpec?) {
        mainHandler.post {
            val host = if (spec == null) null else host(context)
            if (spec == null || host == null) {
                blurWindow.remove()
                washWindow.remove()
                return@post
            }
            val (hostContext, windowType) = host
            val blurRadius = blurRadiusPx(hostContext, spec)
            if (blurRadius > 0) {
                blurWindow.show(hostContext, windowType, blurRadius)
            } else {
                blurWindow.remove()
            }
            val washAlpha = spec.washAlpha.coerceIn(0f, MAX_WASH_ALPHA)
            if (washAlpha > 0f) {
                washWindow.show(hostContext, windowType, washAlpha)
            } else {
                washWindow.remove()
            }
        }
    }

    /**
     * The context whose window token carries the overlay, and the window type that goes with it.
     * An accessibility service may add [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY]
     * windows without holding SYSTEM_ALERT_WINDOW, so as long as DetoxDroid runs at all, the
     * filter needs nothing granted. Returns null when neither path is available.
     */
    private fun host(context: Context): Pair<Context, Int>? {
        DetoxDroidAccessibilityService.instance?.let {
            return it to WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        }
        if (Settings.canDrawOverlays(context)) {
            return context to WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        return null
    }

    /**
     * The blur radius in pixels, or 0 where the device cannot do cross-window blurs: below
     * Android 12, on devices that never enable them, and temporarily while battery saver is on.
     * The wash then carries the effect alone.
     */
    private fun blurRadiusPx(context: Context, spec: ScreenFilterSpec): Int {
        if (spec.blurDp <= 0f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (!manager.isCrossWindowBlurEnabled) return 0
        return (spec.blurDp * context.resources.displayMetrics.density).toInt()
    }

    /**
     * The blur layer. A [Dialog] only because that is the one public way to reach
     * `Window.setBackgroundBlurRadius()`, which blurs the backdrop on the window's own surface
     * instead of through a dim layer the app cannot configure. It shows nothing itself: fully
     * transparent background, no content, no focus, no touches.
     */
    private class BlurWindow {
        private var dialog: Dialog? = null
        private var currentRadius = 0

        fun show(hostContext: Context, windowType: Int, radius: Int) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
            dialog?.let { existing ->
                if (currentRadius == radius) return
                kotlin.runCatching { existing.window?.setBackgroundBlurRadius(radius) }
                    .onSuccess { currentRadius = radius }
                    .onFailure { Timber.e(it, "Could not update the screen filter blur") }
                return
            }

            // the theme goes through the constructor on purpose: Dialog(Context) ignores the theme
            // of the context it is handed and resolves `dialogTheme` from it, which is a floating
            // one. generateLayout() then resets the window to WRAP_CONTENT and it measures to the
            // dialog width, 95 % of the screen in portrait, leaving a strip at the edge unblurred.
            val newDialog = Dialog(hostContext, android.R.style.Theme_Translucent_NoTitleBar)
            val window = newDialog.window ?: return
            kotlin.runCatching {
                window.setType(windowType)
                window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                // the wash window appears without an animation, and a blur fading in beside it
                // reads as a glitch
                window.setWindowAnimations(0)
                // a dialog dims behind itself by default, and that dim layer is exactly the thing
                // that swallows every touch meant for the app below
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                )
                window.setDimAmount(0f)
                window.attributes = window.attributes.apply {
                    gravity = Gravity.TOP or Gravity.START
                    // an accessibility overlay is a trusted window, so its opacity is not held
                    // against it; the untrusted fallback has to stay under the obscuring threshold
                    alpha =
                        if (windowType == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) 1f
                        else MAX_WASH_ALPHA
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
                newDialog.setCancelable(false)
                newDialog.setContentView(View(newDialog.context))
                // sized only after the content, because installing it runs generateLayout(), which
                // is where a window sized by its theme would be resized again
                window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT
                )
                newDialog.show()
                // the radius goes straight to the decor view, which exists only once the dialog has
                // content, and the blur needs the window surface, which exists only once it is shown
                window.setBackgroundBlurRadius(radius)
            }.onSuccess {
                dialog = newDialog
                currentRadius = radius
            }.onFailure {
                Timber.e(it, "Could not attach the screen filter blur")
                kotlin.runCatching { newDialog.dismiss() }
            }
        }

        fun remove() {
            kotlin.runCatching { dialog?.dismiss() }
            dialog = null
            currentRadius = 0
        }
    }

    /**
     * The wash layer. Holds on to its window so a changed spec updates it in place instead of
     * tearing it down and building it again, which would flicker on every app switch.
     */
    private class FilterWindow(private val color: Int) {
        private var view: View? = null
        private var params: WindowManager.LayoutParams? = null
        private var windowManager: WindowManager? = null
        private var currentAlpha = 0f

        fun show(hostContext: Context, windowType: Int, alpha: Float) {
            val existingView = view
            val existingParams = params
            val existingManager = windowManager
            if (existingView != null && existingParams != null && existingManager != null) {
                if (currentAlpha == alpha) return
                existingParams.alpha = alpha
                kotlin.runCatching {
                    existingManager.updateViewLayout(existingView, existingParams)
                }.onSuccess {
                    currentAlpha = alpha
                }.onFailure {
                    Timber.e(it, "Could not update the screen filter")
                    remove()
                }
                return
            }

            val manager = hostContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val newParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            )
            newParams.gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // cover the cutout area too, so the filter does not stop short of the display edges
                newParams.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            // the wash is the window's own alpha rather than a translucent background color: the
            // system reads that alpha to decide whether touches may pass through to the app below,
            // and a window painting 45 % gray while declaring alpha 1.0 counts as fully obscuring
            newParams.alpha = alpha

            val newView = View(hostContext).apply { setBackgroundColor(color) }
            kotlin.runCatching { manager.addView(newView, newParams) }.onSuccess {
                view = newView
                params = newParams
                windowManager = manager
                currentAlpha = alpha
            }.onFailure {
                // e.g. the overlay permission was revoked, or the service token died between the
                // event and this callback — running without the filter beats crashing the service
                Timber.e(it, "Could not attach the screen filter")
            }
        }

        fun remove() {
            val currentView = view ?: return
            kotlin.runCatching { windowManager?.removeView(currentView) }
            view = null
            params = null
            windowManager = null
            currentAlpha = 0f
        }
    }
}
