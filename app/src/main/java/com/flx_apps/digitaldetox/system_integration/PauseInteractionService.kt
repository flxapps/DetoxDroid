package com.flx_apps.digitaldetox.system_integration

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.flx_apps.digitaldetox.features.PauseButtonFeature
import timber.log.Timber

/**
 * The [PauseInteractionService] is used to launch pauses from the default digital
 * assistant shortcut (typically long-pressing the home button).
 */
class PauseInteractionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return PauseInteractionSession(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle pause action from notification
        if (intent?.action == "com.flx_apps.digitaldetox.ACTION_PAUSE") {
            Timber.d("Pause action triggered from notification")
            PauseButtonFeature.togglePause(this)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }
}

/**
 * A [VoiceInteractionSession] that just launches a pause.
 */
class PauseInteractionSession(context: Context) : VoiceInteractionSession(context) {
    override fun onHandleAssist(state: AssistState) {
        Timber.d("onHandleAssist")
        PauseButtonFeature.togglePause(context)
        super.onHandleAssist(state)
        hide()
    }
}