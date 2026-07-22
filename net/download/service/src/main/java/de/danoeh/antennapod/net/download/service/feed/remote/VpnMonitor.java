package de.danoeh.antennapod.net.download.service.feed.remote;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class VpnMonitor {

    private static String TAG = "VpnMonitor";

    /**
     * Custom interface to pass the boolean result back to the caller.
     */
    public interface VpnCallback {
        void onResult(boolean success);
    }

    /**
     * Internal wrapper to pair a callback with its delayed timeout task
     * so we can cancel the timeout if the VPN connects/disconnects in time.
     */
    private static class PendingRequest {
        final VpnCallback callback;
        final Runnable timeoutTask;

        PendingRequest(VpnCallback callback, Runnable timeoutTask) {
            this.callback = callback;
            this.timeoutTask = timeoutTask;
        }
    }

    private static volatile VpnMonitor instance;
    private final ConnectivityManager connectivityManager;
    private final Handler mainHandler;

    // Thread-safe lists to hold pending one-shot requests
    private final List<PendingRequest> pendingConnects = new CopyOnWriteArrayList<>();
    private final List<PendingRequest> pendingDisconnects = new CopyOnWriteArrayList<>();

    private boolean isCurrentlyConnected;

    private VpnMonitor(Context context) {
        connectivityManager = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        mainHandler = new Handler(Looper.getMainLooper());

        isCurrentlyConnected = checkVpnLive();
        registerNetworkCallback();
    }

    public static VpnMonitor getInstance(Context context) {
        if (instance == null) {
            synchronized (VpnMonitor.class) {
                if (instance == null) {
                    instance = new VpnMonitor(context);
                }
            }
        }
        return instance;
    }

    /**
     * Waits for a VPN connection.
     * Returns true immediately if already connected.
     * Returns false if the timeout expires before a connection occurs.
     */
    public void onVpnConnect(long timeoutMillis, VpnCallback callback) {
        if (isVpnConnected()) {
            mainHandler.post(() -> callback.onResult(true));
            return;
        }

        Runnable timeoutTask = () -> {
            removePendingRequest(pendingConnects, callback);
            callback.onResult(false);
        };

        pendingConnects.add(new PendingRequest(callback, timeoutTask));

        if (timeoutMillis > 0) {
            mainHandler.postDelayed(timeoutTask, timeoutMillis);
        }
    }

    /**
     * Waits for a VPN disconnection.
     * Returns true immediately if already disconnected.
     * Returns false if the timeout expires before a disconnection occurs.
     */
    public void onVpnDisconnect(long timeoutMillis, VpnCallback callback) {
        if (!isVpnConnected()) {
            mainHandler.post(() -> callback.onResult(true));
            return;
        }

        Runnable timeoutTask = () -> {
            removePendingRequest(pendingDisconnects, callback);
            callback.onResult(false);
        };

        pendingDisconnects.add(new PendingRequest(callback, timeoutTask));

        if (timeoutMillis > 0) {
            mainHandler.postDelayed(timeoutTask, timeoutMillis);
        }
    }

    public boolean isVpnConnected() {
        return checkVpnLive();
    }

    private void removePendingRequest(List<PendingRequest> list, VpnCallback callback) {
        for (PendingRequest req : list) {
            if (req.callback == callback) {
                list.remove(req);
                break;
            }
        }
    }

    private void registerNetworkCallback() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();

        connectivityManager.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                if (!isCurrentlyConnected) {
                    if (checkVpnLive()) {
                        isCurrentlyConnected = true;
                        notifyConnect();
                    }
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                if (isCurrentlyConnected && !checkVpnLive()) {
                    isCurrentlyConnected = false;
                    notifyDisconnect();
                }
            }
        });
    }

    private void notifyConnect() {
        // Iterate, trigger success, and clear to ensure one-shot behavior
        for (PendingRequest req : pendingConnects) {
            mainHandler.removeCallbacks(req.timeoutTask); // Cancel the timeout
            mainHandler.post(() -> req.callback.onResult(true));
        }
        pendingConnects.clear();
    }

    private void notifyDisconnect() {
        for (PendingRequest req : pendingDisconnects) {
            mainHandler.removeCallbacks(req.timeoutTask); // Cancel the timeout
            mainHandler.post(() -> req.callback.onResult(true));
        }
        pendingDisconnects.clear();
    }

    private boolean checkVpnLive() {
        String iface = "";
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (networkInterface.isUp()) {
                    iface = networkInterface.getName();
                    if ( iface.startsWith("tun") || iface.startsWith("ppp") || iface.startsWith("pptp")) {
                        return true;
                    }
                }
            }
        } catch (SocketException e1) {
            Log.d(TAG, "Unable to check networks", e1);
        }

        return false;
    }
}