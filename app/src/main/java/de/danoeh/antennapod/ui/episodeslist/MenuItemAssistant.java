package de.danoeh.antennapod.ui.episodeslist;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.playback.base.MediaItemAdapter;
import de.danoeh.antennapod.playback.service.PlaybackController;

public class MenuItemAssistant {
    private static String TAG = "MenuItemAssistant";
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

            MediaItem currentMediaItem = controller.getCurrentMediaItem();
            long currentMediaId;
            try {
                currentMediaId = currentMediaItem == null ? -1 : Long.parseLong(currentMediaItem.mediaId);
            } catch (NumberFormatException e) {
                currentMediaId = -1;
            }

            if (currentMediaId == -1 || !knownMediaItems.contains(currentMediaId)) {
                // Nothing relevant is playing, nothing to skip
                if (callback != null) {
                    callback.run();
                }
                controller.release();
                return;
            }

            Log.d(TAG, "skipIfPlaying: found current podcast -- now seekToNextMediaItem");

            Handler handler = new Handler(Looper.getMainLooper());
            AtomicBoolean finished = new AtomicBoolean(false);
            final Player.Listener[] listenerHolder = new Player.Listener[1];
            Runnable finish = () -> {
                if (!finished.compareAndSet(false, true)) {
                    return;
                }
                handler.removeCallbacksAndMessages(null);
                if (listenerHolder[0] != null) {
                    controller.removeListener(listenerHolder[0]);
                }
                controller.release();
                Log.d(TAG, "skipIfPlaying: media item changed, running callback");
                if (callback != null) {
                    callback.run();
                }
            };

            Player.Listener transitionListener = new Player.Listener() {
                @Override
                public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                    long newMediaId;
                    try {
                        newMediaId = mediaItem == null ? -1 : Long.parseLong(mediaItem.mediaId);
                    } catch (NumberFormatException e) {
                        newMediaId = -1;
                    }
                    if (knownMediaItems.contains(newMediaId)) {
                        // The next item is also being removed, keep skipping
                        Log.d(TAG, "skipIfPlaying: next item is also removed, skipping again");
                        handler.removeCallbacksAndMessages(null);
                        handler.postDelayed(finish, SKIP_TIMEOUT_MS);
                        controller.seekToNextMediaItem();
                        return;
                    }
                    finish.run();
                }
            };
            listenerHolder[0] = transitionListener;
            controller.addListener(transitionListener);
            // Safety net in case no transition happens (e.g. no next item exists)
            handler.postDelayed(finish, SKIP_TIMEOUT_MS);
            controller.seekToNextMediaItem();
        });
    }

    public interface CurrentPositionCallback {
        void onCurrentPosition(int position);
    }

    public static void findCurrentlyPlayingPosition(Context context, List<FeedItem> queue, final CurrentPositionCallback callback) {
        PlaybackController.bindToMedia3Service(context, controller -> {
            for (int element = 0; element < queue.size(); element++) {
                FeedItem feedItem = queue.get(element);
                if (controller.getCurrentMediaItem() != null) {
                    if (MediaItemAdapter.fromMediaIdStub(feedItem.getMedia().getId()).mediaId.equals(controller.getCurrentMediaItem().mediaId)) {
                        callback.onCurrentPosition(element);
                        break;
                    }
                }
            }
        });
    }
}
