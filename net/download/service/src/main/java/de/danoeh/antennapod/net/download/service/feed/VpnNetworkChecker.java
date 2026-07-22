package de.danoeh.antennapod.net.download.service.feed;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import android.content.Intent;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;

import de.danoeh.antennapod.net.download.service.R;


public class VpnNetworkChecker {
    private static final String TAG = "VpnNetworkChecker";

    public interface VpnListener {
        void onDone();

        void onTimeout();
    }

    public static boolean isVpnConnected(Context context) {
        Log.d(TAG, "Checking vpn connected");
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }

        Network network = cm.getActiveNetwork();
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps != null) {
            // Check if transport is explicitly VPN OR if it lacks the "NOT_VPN" capability
            boolean isVpnTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
            boolean isNotVpn = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);

            if (isVpnTransport || !isNotVpn) {
                Log.d(TAG, "Network is VPN");
                return true;
            }
        }

        Log.d(TAG, "No VPN detected");
        return false;
    }

    public static void launchVpnSelector(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent intent = new Intent(Settings.ACTION_VPN_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    public static void waitForVpnDisconnect(Context context) {
        Log.d("KJS", "notifyDownloadsComplete - launch selector");
        Toast.makeText(context, context.getString(R.string.vpn_download_complete), Toast.LENGTH_LONG).show();
        VpnNetworkChecker.launchVpnSelector(context.getApplicationContext());
        // TODO: This should detect vpn disconnect, and then restore our app to front
    }

    /**
     * Waits asynchronously for a VPN connection to be established.
     *
     * @param context   Application/Activity context
     * @param timeoutMs Maximum wait time in milliseconds (e.g., 10000 for 10s)
     * @param listener  Callback invoked when connected or timed out
     */
    public static void waitForVpnConnect(@NonNull Context context, long timeoutMs, @NonNull VpnListener listener) {
        if (isVpnConnected(context)) {
            listener.onDone();
            return;
        }

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            listener.onTimeout();
            return;
        }

        Handler mainHandler = new Handler(Looper.getMainLooper());
        AtomicBoolean isTriggered = new AtomicBoolean(false);

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) // Double negative means "VPNs only". Without this VPNs are not returned
                .build();

        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                super.onAvailable(network);

                Log.d(TAG, "New network available " + network);

                if (isVpnConnected(context)) {
                    // Ensure this runs only once
                    if (isTriggered.compareAndSet(false, true)) {
//                        mainHandler.removeCallbacksAndMessages(null);
//                        safeUnregister(cm, this);

                        // Notify listener on UI Thread
                        mainHandler.post(listener::onDone);
                    }
                } else {
                    Log.d(TAG, "New network not detected as a vpn");
                }
            }

            // TODO: This needs to work properly
            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                Log.d(TAG, "Vpn disconnected");

                mainHandler.removeCallbacksAndMessages(null);
                safeUnregister(cm, this);

                // Notify listener on UI Thread
              //  mainHandler.post(listener::onVpnDisconnected);
            }
        };

        // 4. Set up Timeout Safety Net
        Runnable timeoutRunnable = () -> {
            if (isTriggered.compareAndSet(false, true)) {
                Log.d(TAG, "VPN failed to connect -- timeout");
                safeUnregister(cm, callback);
                listener.onTimeout();
            }
        };

        mainHandler.postDelayed(timeoutRunnable, timeoutMs);

        // 5. Register listener directly on the Main Handler thread
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "Registering network callback");
                cm.registerNetworkCallback(request, callback, mainHandler);
            } else {
                Log.e(TAG, "WILL I SEE THIS");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to register network callback", e);
            mainHandler.removeCallbacks(timeoutRunnable);
            listener.onTimeout();
        }
    }

    private static void safeUnregister(ConnectivityManager cm, ConnectivityManager.NetworkCallback callback) {
        try {
            cm.unregisterNetworkCallback(callback);
        } catch (IllegalArgumentException ignored) {
            // Callback was already unregistered
        }
    }

}