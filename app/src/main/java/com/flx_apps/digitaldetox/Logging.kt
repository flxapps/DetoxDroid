package com.flx_apps.digitaldetox

import java.util.logging.Level
import java.util.logging.Logger

fun log(message: String) = Logger.getLogger("DetoxDroid").log(Level.INFO, message)
