package de.danoeh.antennapod.ui.episodeslist;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.playback.service.PlaybackStatus;

public class MenuItemAssistant {
    private static final String TAG = "MenuItemAssistant";
    private static final long SKIP_TIMEOUT_MS = 2000;

    /**
     * Do something {@code callback} after we have found a podcast to play if the currently playing one
     * is in {@code feedItems}
     *
     * If the currently playing item is in {@code feedItems}, it is skipped first and the
     * {@code callback} is only invoked once the player has actually transitioned to the next item.
     * Waiting for the transition is important because the player determines the next queue item
     * asynchronously via {@code DBReader.getNextInQueue}, so mutating the queue (e.g. deleting the
     * item) before that read completes can cause playback to stop instead of continuing.
     */
    @OptIn(markerClass = UnstableApi.class)
    public static void skipIfPlaying(Context context, List<FeedItem> feedItems, Runnable callback) {
        PlaybackController.bindToMedia3ServiceKeepAlive(context, controller -> {
            Set<Long> knownMediaItems = new HashSet<>();
            for (FeedItem feedItem : feedItems) {
                if (feedItem.getMedia() != null) {
                    knownMediaItems.add(feedItem.getMedia().getId());
                }
            }

            final long mediaIdToSkip= getPlayingMediaId(controller);
            if (mediaIdToSkip == -1 || !knownMediaItems.contains(mediaIdToSkip)) {
                // Nothing relevant is playing, nothing to skip
                if (callback != null) {
                    callback.run();
                }
                controller.release();
                return;
            }

            Log.d(TAG, "skipIfPlaying: found current podcast -- now seekToNextMediaItem");

            Handler waitForNextMediaItem = new Handler(Looper.getMainLooper());
            Runnable skipIfPlayingFinished = skipIfPlayingFinished(callback, controller, waitForNextMediaItem);

            // Check if 'seekToNextMediaItem' worked by polling because I could not
            // get Player.Listener.onMediaItemTransition to fire.
            waitForNextMediaItem.postDelayed(new Runnable() {
                @Override
                public void run() {
                    long currentId = getPlayingMediaId(controller);
                    if (currentId != mediaIdToSkip) {
                        // We've moved! Now check if we're on a removed item or not
                        if (!knownMediaItems.contains(currentId)) {
                            skipIfPlayingFinished.run();
                        } else {
                            // We're on another removed item, skip again
                            controller.seekToNextMediaItem();
                            waitForNextMediaItem.postDelayed(this, 100);
                        }
                    } else {
                        // Still on same item, check again
                        waitForNextMediaItem.postDelayed(this, 100);
                    }
                }
            }, 100);

            controller.seekToNextMediaItem();

            // Fire the callack after the timeout, just in case
            waitForNextMediaItem.postDelayed(skipIfPlayingFinished, SKIP_TIMEOUT_MS);
        });
    }

    private static long getPlayingMediaId(MediaController controller) {
        MediaItem current = controller.getCurrentMediaItem();
        long currentId;
        try {
            currentId = current == null ? -1 : Long.parseLong(current.mediaId);
        } catch (NumberFormatException e) {
            currentId = -1;
        }
        return currentId;
    }

    @NonNull
    private static Runnable skipIfPlayingFinished(Runnable callback, MediaController controller, Handler handler) {
        AtomicBoolean finished = new AtomicBoolean(false);

        Runnable finish = () -> {
            Log.d(TAG, "skipIfPlayingFinished");
            // Make sure we only 'finish' once
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            // Cleanup
            handler.removeCallbacksAndMessages(null);
            controller.release();

            Log.d(TAG, "skipIfPlaying: media item changed, running callback");
            if (callback != null) {
                callback.run();
            }
        };
        return finish;
    }

    public interface CurrentPositionCallback {
        void onCurrentPosition(int position);
    }

    public static void findCurrentlyPlayingPosition(Context context, List<FeedItem> queue, final CurrentPositionCallback callback) {
        PlaybackController.bindToMedia3Service(context, controller -> {
            for (int element = 0; element < queue.size(); element++) {
                FeedItem feedItem = queue.get(element);
                if (PlaybackStatus.isPlaying(feedItem.getMedia())) {
                    callback.onCurrentPosition(element);
                    break;
                }
            }
        });
    }
}
