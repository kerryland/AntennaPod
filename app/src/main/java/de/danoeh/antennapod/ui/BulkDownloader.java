package de.danoeh.antennapod.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.net.download.service.feed.VpnNetworkChecker;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.common.ConfirmationDialog;


public class BulkDownloader {
    private static BulkDownloader instance;

    // Private constructor enforcing Singleton pattern
    private BulkDownloader(Context context) {
        DownloadServiceInterface.get().notifyDownloadsComplete(context.getApplicationContext(), new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, context.getString(R.string.bulk_downloads_completed), Toast.LENGTH_LONG).show();

                if (UserPreferences.isVpnDownload() && VpnNetworkChecker.isVpnConnected(context.getApplicationContext())) {
                    VpnNetworkChecker.waitForVpnDisconnect(context.getApplicationContext());
                }
            }});
    }

    private static final String TAG = "BulkDownloader";

    public static synchronized BulkDownloader getInstance(Context context) {
        if (instance == null) {
            instance = new BulkDownloader(context);
        }
        return instance;
    }
    private void actuallyDownload(Context context, List<FeedItem> downloadList) {
        for (FeedItem episode : downloadList) {
            DownloadServiceInterface.get().download(context, episode);
        }
    }

    public void downloadAll(Context context, List<FeedItem> episodes) {
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

        if (UserPreferences.isVpnDownload() && !VpnNetworkChecker.isVpnConnected(context.getApplicationContext())) {
            VpnNetworkChecker.launchVpnSelector(context.getApplicationContext());

            final long TIMEOUT_MS = 60000;
            VpnNetworkChecker.waitForVpnConnect(context.getApplicationContext(), TIMEOUT_MS, new VpnNetworkChecker.VpnListener() {
                public void onDone() {
                    confirmAndDownload(context, episodes);
                }

                public void onTimeout() {
                    Toast.makeText(context, context.getString(R.string.vpn_download_timeout, TIMEOUT_MS / 1000), Toast.LENGTH_LONG).show();
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
        final int DOWNLOAD_WARN_LEVEL = 3;

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
