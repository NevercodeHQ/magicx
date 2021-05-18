package com.nevercode.triagemagic.notification

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType

class TriagemagicNotificationManager {

    companion object {
        private val balloonGroup = NotificationGroup("triagemagic.balloon",
            NotificationDisplayType.BALLOON, false, null, AllIcons.Toolwindows.InfoEvents)

        fun showNotification(title: String, message: String) {
            balloonGroup .createNotification(title, message, NotificationType.INFORMATION)
                .notify(null)
        }
    }
}