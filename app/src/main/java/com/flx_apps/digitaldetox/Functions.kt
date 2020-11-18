package com.flx_apps.digitaldetox

import android.content.res.Resources
import java.util.logging.Level
import java.util.logging.Logger

fun log(message: String) = Logger.getLogger("DetoxDroid").log(Level.INFO, message)

fun Int.toPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()
fun Int.toDp(): Int = (this / Resources.getSystem().displayMetrics.density).toInt()
