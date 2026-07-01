package de.danoeh.antennapod.net.download.service.feed;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import androidx.annotation.NonNull;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;


public class VpnNetworkChecker {

    public static boolean isVpnConnected(Context context) {
        // 1. Get the system connectivity manager
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return false;
        }

        // 2. Retrieve the currently active data network
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }

        // 3. Fetch the capabilities for this network
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) {
            return false;
        }

        // 4. Check if the network transport relies on a VPN
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }

    public static void launchVpnSelector(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent intent = new Intent(Settings.ACTION_VPN_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    public static void monitorVpnConnection(Context context) {
        // 1. Get the system connectivity manager with an explicit Java cast
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return;
        }

        // 2. Define a request looking specifically for VPN transports
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) // Ensures we only get VPNs
                .build();

        // 3. Register the callback using an anonymous inner class
        connectivityManager.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                // Triggered when a VPN connects
                System.out.println("VPN Connected!");
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                // Triggered when the VPN disconnects
                System.out.println("VPN Disconnected!");
            }
        });
    }
}