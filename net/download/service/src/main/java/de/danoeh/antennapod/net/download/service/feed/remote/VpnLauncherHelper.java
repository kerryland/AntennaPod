package de.danoeh.antennapod.net.download.service.feed.remote;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter;

public class VpnLauncherHelper {

    private static boolean disconnectPending = false;

    private static long nexttDisconnectPromptTime = 0;

    private static final String TAG = "VpnLauncherHelper";

    /**
     * Launches VPN Settings and automatically brings MainActivity back to the front
     * when a VPN connection occurs.
     */
    public static void launchVpnAndReturnOnConnect(Context context, long timeoutMillis) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 1. Launch System VPN Settings
            context.startActivity(vpnSettingsIntent());

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
            context.startActivity(vpnSettingsIntent());
            disconnectPending = true;

            VpnMonitor.getInstance(context).onVpnDisconnect(timeoutMillis, success -> {
                if (success) {
                    disconnectPending = false;
                    bringAppToFront(context);
                }
            });
        }
    }


    /**
     * Reprompts the user to disconnect from the VPN if a disconnect was requested
     * (e.g. after bulk downloads completed) but they have not disconnected yet.
     * Intended to be called when the app is brought back to the foreground.
     */
    public static void repromptToDisconnectIfNeeded(Context context) {
        if (System.currentTimeMillis() > nexttDisconnectPromptTime && disconnectPending && VpnMonitor.getInstance(context).isVpnConnected()
                && DownloadServiceInterface.get().getNumberOfActiveDownloads(context) == 0) {
            nexttDisconnectPromptTime = System.currentTimeMillis() + 120000; // prompt again in 2 minutes
            launchVpnAndReturnOnDisconnect(context, 0);
        }
    }

    /**
     * Reorders the app's task stack to place MainActivity back on top.
     */
    public static void bringAppToFront(Context context) {
        Intent intent = new MainActivityStarter(context).getIntent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        context.startActivity(intent);
    }

    private static Intent vpnSettingsIntent() {
        Intent intent = new Intent(Settings.ACTION_VPN_SETTINGS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
}
