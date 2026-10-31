package com.cleanhome.tv;

import android.service.notification.NotificationListenerService;

/**
 * Does nothing itself: being an enabled notification listener is what lets the launcher
 * read other apps' media sessions (Spotify's now-playing info). Enabled once over adb:
 * cmd notification allow_listener com.cleanhome.tv/com.cleanhome.tv.MediaListener
 */
public class MediaListener extends NotificationListenerService {
}
