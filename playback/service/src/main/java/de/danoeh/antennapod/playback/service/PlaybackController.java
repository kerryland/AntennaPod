package de.danoeh.antennapod.playback.service;

import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import androidx.core.util.Consumer;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import de.danoeh.antennapod.event.playback.PlaybackServiceEvent;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.ui.episodes.PlaybackSpeedUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.concurrent.ExecutionException;

/**
 * Communicates with the playback service. GUI classes should use this class to
 * control playback instead of communicating with the PlaybackService directly.
 */
public class PlaybackController {

    private static final String TAG = "PlaybackController";

    private Playable media;

    private boolean eventsRegistered = false;

    public PlaybackController() {
    }

    /**
     * Creates a new connection to the playbackService.
     */
    public synchronized void init() {
        if (!eventsRegistered) {
            EventBus.getDefault().register(this);
            eventsRegistered = true;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PlaybackServiceEvent event) {
        if (event.action == PlaybackServiceEvent.Action.SERVICE_STARTED) {
            init();
        }
    }

    /**
     * Should be called if the PlaybackController is no longer needed, for
     * example in the activity's onStop() method.
     */
    public void release() {
        Log.d(TAG, "Releasing PlaybackController");
        media = null;

        if (eventsRegistered) {
            EventBus.getDefault().unregister(this);
            eventsRegistered = false;
        }
    }

    public Playable getMedia() {
        if (media == null) {
            media = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
        }
        return media;
    }

    public float getCurrentPlaybackSpeedMultiplier() {
        return PlaybackSpeedUtils.getCurrentPlaybackSpeed(getMedia());
    }

    public boolean getCurrentPlaybackSkipSilence() {
        return PlaybackSpeedUtils.getCurrentSkipSilencePreference(getMedia())
                == FeedPreferences.SkipSilence.AGGRESSIVE;
    }

    public static void bindToMedia3Service(Context context, Consumer<MediaController> consumer) {
        bindToMedia3Service(context, consumer, true);
    }

    /**
     * Like {@link #bindToMedia3Service(Context, Consumer)} but keeps the connection alive after the
     * callback returns, so the callback can observe asynchronous state changes such as media item
     * transitions. The callback is responsible for calling {@link MediaController#release()}.
     */
    public static void bindToMedia3ServiceKeepAlive(Context context, Consumer<MediaController> consumer) {
        bindToMedia3Service(context, consumer, false);
    }

    private static void bindToMedia3Service(Context context, Consumer<MediaController> consumer,
                                            boolean releaseAfterCallback) {
        SessionToken sessionToken = new SessionToken(context,
                new ComponentName(context, Media3PlaybackService.class));
        ListenableFuture<MediaController> controllerFuture =
                new MediaController.Builder(context, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            MediaController controller = null;
            try {
                controller = controllerFuture.get();
                consumer.accept(controller);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            } finally {
                if (releaseAfterCallback && controller != null) {
                    controller.release();
                }
            }
        }, MoreExecutors.directExecutor());

    }
}
