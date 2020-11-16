package com.flx_apps.digitaldetox

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Creation Date: 11/9/20
 * @author felix
 */
class DigitalDetoxNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
//        if (!UserSettings(this).disableNotifications?.or(false)!!) {
//            return
//        }
//        cancelNotification(sbn?.key)
    }
}