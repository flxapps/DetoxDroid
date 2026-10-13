package com.flx_apps.digitaldetox.ui.widgets

import androidx.compose.runtime.Composable

/**
 * FOSS: DetoxDroid needs no answer of its own before the accessibility service goes on, so it
 * always has the go-ahead and [AccessibilityConsent.ask] runs its action straight away.
 * The sibling `src/googlePlay/…/AccessibilityDisclosure.kt` (private overlay, gitignored) puts a
 * disclosure with Agree and Decline in front of it under the same fully-qualified names.
 */
object AccessibilityDisclosureSupport {
    /** Whether DetoxDroid may switch the accessibility service on by itself. Always here. */
    const val isAccepted: Boolean = true
}

/** Runs whatever switches the accessibility service on, see [AccessibilityDisclosureSupport]. */
object AccessibilityConsent {
    @Suppress("UNUSED_PARAMETER")
    fun ask(onDeclined: () -> Unit = {}, onAgreed: () -> Unit) = onAgreed()
}

@Composable
fun rememberAccessibilityConsent(): AccessibilityConsent = AccessibilityConsent
