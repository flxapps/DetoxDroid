package com.flx_apps.digitaldetox.ui.screens.about

import android.app.Activity
import androidx.compose.runtime.Composable

/**
 * FOSS: there is no app store to review DetoxDroid on, so this contributes nothing to the About
 * list. The sibling `src/googlePlay/…/AboutFlavorItems.kt` (private overlay, gitignored) adds a
 * "Rate DetoxDroid" tile under the same fully-qualified name.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun StoreReviewAboutTile(activity: Activity?) {}
