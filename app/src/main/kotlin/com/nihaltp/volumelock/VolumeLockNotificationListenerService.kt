package com.nihaltp.volumelock

import android.service.notification.NotificationListenerService

/**
 * Empty NotificationListenerService implementation required to satisfy Android's
 * security permission check. This service must be enabled by the user in system
 * settings so that the app has permission to query active Media Sessions.
 */
class VolumeLockNotificationListenerService : NotificationListenerService()
