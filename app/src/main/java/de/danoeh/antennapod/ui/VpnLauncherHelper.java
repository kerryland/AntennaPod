package de.danoeh.antennapod.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.net.download.service.feed.remote.VpnMonitor;

public class VpnLauncherHelper {

    /**
     * Launches VPN Settings and automatically brings MainActivity back to the front
     * when a VPN connection occurs.
     */
    public static void launchVpnAndReturnOnConnect(Context context, long timeoutMillis) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 1. Launch System VPN Settings
            Intent intent = new Intent(Settings.ACTION_VPN_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            // 2. Monitor for VPN connection
            VpnMonitor.getInstance(context).onVpnConnect(timeoutMillis, success -> {
                if (success) {
                    bringAppToFront(context);
                }
            });
        }
    }

    /**
     * Launches VPN Settings and automatically brings MainActivity back to the front
     * when a VPN disconnect occurs.
     */
    public static void launchVpnAndReturnOnDisconnect(Context context, long timeoutMillis) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent intent = new Intent(Settings.ACTION_VPN_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            VpnMonitor.getInstance(context).onVpnDisconnect(timeoutMillis, success -> {
                if (success) {
                    bringAppToFront(context);
                }
            });
        }
    }

    /**
     * Reorders the app's task stack to place MainActivity back on top.
     */
    public static void bringAppToFront(Context context) {
        Intent intent = new Intent(context, MainActivity.class); // Replace with your target Activity
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}