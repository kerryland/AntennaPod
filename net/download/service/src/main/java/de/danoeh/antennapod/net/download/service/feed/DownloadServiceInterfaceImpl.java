package de.danoeh.antennapod.net.download.service.feed;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import de.danoeh.antennapod.net.download.service.episode.EpisodeDownloadWorker;
import de.danoeh.antennapod.net.download.service.feed.remote.VpnMonitor;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class DownloadServiceInterfaceImpl extends DownloadServiceInterface {

    private boolean downloadStarted = false;
    private static String TAG = "DownloadServiceInterfaceImpl";

    public void downloadNow(Context context, FeedItem item, boolean ignoreConstraints) {
        OneTimeWorkRequest.Builder workRequest = createDownloadRequest(context, item);
        workRequest.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);
        if (ignoreConstraints) {
            workRequest.setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build());
        }

        enqueueDownloadRequest(context, item, workRequest);
    }

    public void download(Context context, FeedItem item) {
        if (item.isDownloaded()) {
            return;
        }
        OneTimeWorkRequest.Builder workRequest = createDownloadRequest(context, item);
        enqueueDownloadRequest(context, item, workRequest);
    }

    @Override
    public int downloadAll(Context context, List<FeedItem> items) {
        if (UserPreferences.isVpnDownload() && !VpnMonitor.getInstance(context).isVpnConnected()) {
            return 0;
        }
        int queued = 0;
        for (FeedItem item : items) {
            if (item.hasMedia() && (!item.isDownloaded() || !item.getMedia().fileExists())) {
                download(context, item);
                queued++;
            }
        }
        return queued;
    }

    private static OneTimeWorkRequest.Builder createDownloadRequest(Context context, FeedItem item) {
        OneTimeWorkRequest.Builder workRequest = new OneTimeWorkRequest.Builder(EpisodeDownloadWorker.class)
                .setInitialDelay(0L, TimeUnit.MILLISECONDS)
                .addTag(DownloadServiceInterface.WORK_TAG)
                .addTag(DownloadServiceInterface.WORK_TAG_EPISODE_URL + item.getMedia().getDownloadUrl());
        if (!item.isTagged(FeedItem.TAG_QUEUE) && UserPreferences.enqueueDownloadedEpisodes()) {
            DBWriter.addQueueItem(context, item);
            workRequest.addTag(DownloadServiceInterface.WORK_DATA_WAS_QUEUED);
        }
        workRequest.setInputData(new Data.Builder().putLong(WORK_DATA_MEDIA_ID, item.getMedia().getId()).build());
        workRequest.setConstraints(getConstraints(context));
        return workRequest;
    }

    private void enqueueDownloadRequest(Context context, FeedItem item, OneTimeWorkRequest.Builder workRequest) {
        WorkManager.getInstance(context).enqueueUniqueWork(item.getMedia().getDownloadUrl(),
                ExistingWorkPolicy.REPLACE, workRequest.build());
        downloadStarted = true;
    }

    private static Constraints getConstraints(Context context) {
        Constraints.Builder constraints = new Constraints.Builder();
        boolean vpnActive = UserPreferences.isVpnDownload()
                && VpnMonitor.getInstance(context).isVpnConnected();
        if (UserPreferences.isAllowMobileEpisodeDownload() || vpnActive) {
            constraints.setRequiredNetworkType(NetworkType.CONNECTED);
        } else {
            constraints.setRequiredNetworkType(NetworkType.UNMETERED);
        }
        return constraints.build();
    }

    @Override
    public void cancel(Context context, FeedMedia media) {
        // This needs to be done here, not in the worker. Reason: The worker might or might not be running.
        if (media.fileExists()) {
            DBWriter.deleteFeedMediaOfItem(context, media); // Remove partially downloaded file
        }
        String tag = WORK_TAG_EPISODE_URL + media.getDownloadUrl();
        Future<List<WorkInfo>> future = WorkManager.getInstance(context).getWorkInfosByTag(tag);
        Observable.fromFuture(future)
                .subscribeOn(Schedulers.computation())
                .observeOn(Schedulers.computation())
                .subscribe(
                        workInfos -> {
                            for (WorkInfo info : workInfos) {
                                if (info.getTags().contains(DownloadServiceInterface.WORK_DATA_WAS_QUEUED)) {
                                    DBWriter.removeQueueItem(media.getItem());
                                }
                            }
                            WorkManager.getInstance(context).cancelAllWorkByTag(tag);
                        }, exception -> {
                            WorkManager.getInstance(context).cancelAllWorkByTag(tag);
                            exception.printStackTrace();
                        });
    }

    @Override
    public void cancelAll(Context context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG);
    }

    @Override
    public int getNumberOfActiveDownloads(Context context) {
        try {
            List<WorkInfo> workInfos = WorkManager.getInstance(context)
                    .getWorkInfosByTag(DownloadServiceInterface.WORK_TAG).get();

            Integer activeCount = countActiveDownloads(workInfos);
            return activeCount == null ? 0 : activeCount;

        } catch (ExecutionException | InterruptedException e) {
            return 0;
        }
    }

    @Override
    public void notifyDownloadsComplete(Context context, Runnable onComplete) {
        // Post to the Main Thread Handler
        new Handler(Looper.getMainLooper()).post(() -> {
            LiveData<List<WorkInfo>> liveData = WorkManager.getInstance(context)
                    .getWorkInfosByTagLiveData(DownloadServiceInterface.WORK_TAG);

            liveData.observe(ProcessLifecycleOwner.get(), new Observer<List<WorkInfo>>() {
                @Override
                public void onChanged(List<WorkInfo> workInfos) {
                    Integer activeCount = countActiveDownloads(workInfos);
                    Log.d(TAG, "Active download count = " + activeCount);
                    if (activeCount == null) {
                        return;
                    }

                    if (activeCount == 0 && downloadStarted) {
                        //  liveData.removeObserver(this);
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    }
                }
            });
        });
    }

    @Nullable
    private static Integer countActiveDownloads(List<WorkInfo> workInfos) {
        if (workInfos == null) {
            return null;
        }

        int activeCount = 0;
        for (WorkInfo info : workInfos) {
            if (info.getState() == WorkInfo.State.RUNNING
                    || info.getState() == WorkInfo.State.ENQUEUED
                    || info.getState() == WorkInfo.State.BLOCKED) {
                activeCount++;
            }
        }
        return activeCount;
    }
}
