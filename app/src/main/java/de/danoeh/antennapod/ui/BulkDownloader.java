package de.danoeh.antennapod.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.net.download.service.feed.remote.VpnMonitor;
import de.danoeh.antennapod.net.download.service.feed.remote.VpnLauncherHelper;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.common.ConfirmationDialog;


public class BulkDownloader {
    private static BulkDownloader instance;

    private static final long VPN_DIALOG_WAIT_TIMEOUT_MS = 60000;

    private BulkDownloader(Context context) {
        // Disconnect from VPN when downloads complete
        DownloadServiceInterface.get().notifyDownloadsComplete(context.getApplicationContext(), () -> {
            Toast.makeText(context, context.getString(R.string.bulk_downloads_completed), Toast.LENGTH_LONG).show();
            VpnMonitor vpnMonitor = VpnMonitor.getInstance(context);

            if (UserPreferences.isVpnDownload() && vpnMonitor.isVpnConnected()) {
                VpnLauncherHelper.launchVpnAndReturnOnDisconnect(context, 60000);
            }
        });
    }

    private static final String TAG = "BulkDownloader";

    public static synchronized BulkDownloader getInstance(Context context) {
        if (instance == null) {
            instance = new BulkDownloader(context);
        }
        return instance;
    }
    private void actuallyDownload(Context context, List<FeedItem> downloadList) {
        DownloadServiceInterface.get().downloadAll(context, downloadList);
    }

    public void downloadAll(Context context, List<FeedItem> episodes) {
        Log.d(TAG, "in downloadAll.");
        boolean needDownload = false;
        for (FeedItem episode : episodes) {
            if (episode.hasMedia() && !episode.isDownloaded()) {
                needDownload = true;
                break;
            }
        }

        if (!needDownload) {
            Toast.makeText(context, context.getString(R.string.bulk_downloads_completed), Toast.LENGTH_LONG).show();
            return;
        }

        VpnMonitor vpnMonitor = VpnMonitor.getInstance(context);
        if (UserPreferences.isVpnDownload() && !vpnMonitor.isVpnConnected()) {
            VpnLauncherHelper.launchVpnAndReturnOnConnect(context, VPN_DIALOG_WAIT_TIMEOUT_MS);

            vpnMonitor.onVpnConnect(VPN_DIALOG_WAIT_TIMEOUT_MS, new VpnMonitor.VpnCallback() {
                @Override
                public void onResult(boolean success) {
                    if (success) {
                        Log.d(TAG, "vpn connected -- is it really? " + vpnMonitor.isVpnConnected());
                        confirmAndDownload(context, episodes);
                    } else {
                        Log.d(TAG, "vpn timeout happened");
                        Toast.makeText(context, context.getString(R.string.vpn_download_timeout, VPN_DIALOG_WAIT_TIMEOUT_MS / 1000), Toast.LENGTH_LONG).show();
                    }
                }
            });
        } else {
            confirmAndDownload(context, episodes);
        }
    }

    private void confirmAndDownload(Context context, List<FeedItem> episodes) {
        List<FeedItem> downloadList = new ArrayList<>();
        for (FeedItem episode : episodes) {
            if (episode.hasMedia() && !episode.isDownloaded()) {
                downloadList.add(episode);
            }
        }
        final int DOWNLOAD_WARN_LEVEL = 20;

        if (downloadList.size() > DOWNLOAD_WARN_LEVEL) {
            // make sure the user really wants to clear the queue
            ConfirmationDialog conDialog = new ConfirmationDialog(context,
                    R.string.download_all_label,
                    context.getString(R.string.download_all_confirmation_msg, downloadList.size())) {

                @Override
                public void onConfirmButtonPressed(
                        DialogInterface dialog) {
                    dialog.dismiss();
                    actuallyDownload(context, downloadList);
                }
            };
            conDialog.createNewDialog().show();
        } else {
            actuallyDownload(context, downloadList);
        }
    }
}
