package com.flx_apps.digitaldetox.ui.screens.about

import android.app.Activity
import androidx.compose.foundation.lazy.LazyListScope

/**
 * FOSS: there is no app store to review DetoxDroid on, so this contributes nothing to the About
 * list. The sibling `src/googlePlay/…/AboutFlavorItems.kt` (private overlay, gitignored) adds a
 * "Rate DetoxDroid" tile under the same fully-qualified name.
 */
@Suppress("UNUSED_PARAMETER", "unused")
fun LazyListScope.storeReviewAboutItem(activity: Activity?) {}
