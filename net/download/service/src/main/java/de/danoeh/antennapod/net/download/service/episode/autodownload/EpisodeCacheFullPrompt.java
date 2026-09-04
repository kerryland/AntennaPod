package de.danoeh.antennapod.net.download.service.episode.autodownload;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.greenrobot.eventbus.EventBus;

import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.net.download.service.R;
import de.danoeh.antennapod.ui.notifications.NotificationUtils;

public class EpisodeCacheFullPrompt {
    private static final String TAG = "EpisodeCacheFullPrompt";
    private static final long COOLDOWN_MS = 15 * 60 * 1000;
    private static long lastPrompt = 0;

    private EpisodeCacheFullPrompt() {
    }

    /**
     * Informs the user that auto-download is blocked because the episode cache is full.
     * Shows a snackbar when the app is in the foreground and a system notification otherwise.
     * Repeated prompts are throttled.
     */
    public static void notifyCacheFull(Context context) {
        long now = System.currentTimeMillis();
        if (now - lastPrompt < COOLDOWN_MS) {
            return;
        }
        lastPrompt = now;

        String message = context.getString(R.string.episode_cache_full_notification_message);
        if (EventBus.getDefault().hasSubscriberForEvent(MessageEvent.class)) {
            EventBus.getDefault().post(new MessageEvent(message));
            return;
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Notification permission not granted, skipping prompt");
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                NotificationUtils.CHANNEL_ID_USER_ACTION)
                .setContentTitle(context.getString(R.string.episode_cache_full_notification_title))
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_notification_sync)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        NotificationManagerCompat.from(context).notify(R.id.notification_episode_cache_full, builder.build());
    }
}
