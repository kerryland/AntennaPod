package de.danoeh.antennapod.ui.appstartintent;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

public abstract class MediaButtonStarter {
    private static final String INTENT = "de.danoeh.antennapod.NOTIFY_BUTTON_RECEIVER";

    public static Intent createIntent(Context context, int eventCode) {
        // CHANGED: Explicitly target PlaybackService, NOT MediaButtonReceiver
        Intent intent = new Intent();
        intent.setClassName(context, "de.danoeh.antennapod.playback.service.Media3PlaybackService");
        intent.setAction(Intent.ACTION_MEDIA_BUTTON);

        KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, eventCode);
        intent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);

        return intent;
    }

    public static PendingIntent createPendingIntent(Context context, int eventCode) {
        // CHANGED: Use getForegroundService instead of getBroadcast
        return PendingIntent.getForegroundService(
                context,
                eventCode,
                createIntent(context, eventCode),
                PendingIntent.FLAG_IMMUTABLE
        );
    }
}
