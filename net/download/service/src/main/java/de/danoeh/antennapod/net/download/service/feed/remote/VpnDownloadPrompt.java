package de.danoeh.antennapod.net.download.service.feed.remote;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.greenrobot.eventbus.EventBus;

import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.net.download.service.R;
import de.danoeh.antennapod.ui.notifications.NotificationUtils;

public class VpnDownloadPrompt {
    private static final String TAG = "VpnDownloadPrompt";
    private static final long COOLDOWN_MS = 15 * 60 * 1000;
    private static final long VPN_WAIT_TIMEOUT_MS = 60000;
    private static long lastPrompt = 0;
    private static boolean resumeArmed = false;

    private VpnDownloadPrompt() {
    }

    /**
     * Informs the user that downloads are waiting for a VPN connection and makes sure the
     * automatic download resumes as soon as a VPN is available.
     * Shows a snackbar when the app is in the foreground and a system notification otherwise.
     * Repeated prompts are throttled.
     *
     * @param callback code to execute once a VPN connection is available
     */
    public static void notifyVpnRequired(Context context, Runnable callback) {
        awaitVpnConnection(context, callback);

        long now = System.currentTimeMillis();
        if (now - lastPrompt < COOLDOWN_MS) {
            return;
        }
        lastPrompt = now;

        String message = context.getString(R.string.vpn_required_download_message);
        if (EventBus.getDefault().hasSubscriberForEvent(MessageEvent.class)) {
            EventBus.getDefault().post(new MessageEvent(message,
                    VpnDownloadPrompt::connectVpnAndReturnToApp,
                    context.getString(R.string.connect_vpn_label), true));
            return;
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Notification permission not granted, skipping prompt");
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                NotificationUtils.CHANNEL_ID_USER_ACTION)
                .setContentTitle(context.getString(R.string.vpn_required_download_title))
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(R.drawable.ic_notification_sync)
                .setContentIntent(createVpnSettingsPendingIntent(context))
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        NotificationManagerCompat.from(context).notify(R.id.notification_vpn_required, builder.build());
    }

    private static void connectVpnAndReturnToApp(Context context) {
        VpnLauncherHelper.launchVpnAndReturnOnConnect(context, VPN_WAIT_TIMEOUT_MS);
    }

    /**
     * Executes the callback once a VPN connection becomes available, even if the
     * user never interacts with the prompt. Only one such watch is active at a time.
     */
    private static void awaitVpnConnection(Context context, Runnable callback) {
        if (resumeArmed) {
            return;
        }
        resumeArmed = true;
        VpnMonitor.getInstance(context).onVpnConnect(0, success -> {
            resumeArmed = false;
            if (success) {
                NotificationManagerCompat.from(context).cancel(R.id.notification_vpn_required);
                callback.run();
            }
        });
    }

    private static PendingIntent createVpnSettingsPendingIntent(Context context) {
        return PendingIntent.getActivity(context, R.id.notification_vpn_required,
                createVpnSettingsIntent(),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Intent createVpnSettingsIntent() {
        Intent intent = new Intent(Settings.ACTION_VPN_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
}
