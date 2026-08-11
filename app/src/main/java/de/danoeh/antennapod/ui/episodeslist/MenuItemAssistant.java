package de.danoeh.antennapod.ui.episodeslist;

import android.content.Context;

import androidx.media3.common.MediaItem;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.playback.base.MediaItemAdapter;
import de.danoeh.antennapod.playback.service.PlaybackController;

public class MenuItemAssistant {
    private static String TAG = "MenuItemAssistant";

    /**
     * Do something {@code callback} after we have found a podcast to play if the currently playing one
     * is in {@code feedItems}
     */
    public static void skipIfPlaying(Context context, List<FeedItem> feedItems, Runnable callback) {
        PlaybackController.bindToMedia3Service(context, controller -> {
            MediaItem currentMediaItem = controller.getCurrentMediaItem();
            if (currentMediaItem == null) {
                return;
            }

            Set<Long> knownMediaItems = new HashSet<>();
            for (FeedItem feedItem : feedItems) {
                if (feedItem.getMedia() != null) {
                    knownMediaItems.add(feedItem.getMedia().getId());
                }
            }

            long prevMediaId = -1L;
            for (int i = 0; i < knownMediaItems.size(); i++) {
                try {
                    long currentMediaId = Long.parseLong(currentMediaItem.mediaId);
                    if (prevMediaId == currentMediaId) {
                        break;
                    }

                    if (knownMediaItems.contains(currentMediaId)) {
                        controller.seekToNextMediaItem();
                    }
                    prevMediaId = currentMediaId;

                } catch (NumberFormatException e) {
                    break;
                }

                currentMediaItem = controller.getCurrentMediaItem();
                if (currentMediaItem == null) {
                    break;
                }
            }
        });

        if (callback != null) {
            callback.run();
        }
    }

    public interface CurrentPositionCallback {
        void onCurrentPosition(int position);
    }

    public static void findCurrentlyPlayingPosition(Context context, List<FeedItem> queue, final CurrentPositionCallback callback) {
        PlaybackController.bindToMedia3Service(context, controller -> {
            for (int element = 0; element < queue.size(); element++) {
                FeedItem feedItem = queue.get(element);
                if (controller.getCurrentMediaItem() != null) {
                    if ((MediaItemAdapter.fromPlayableStub(feedItem.getMedia()).mediaId).equals(controller.getCurrentMediaItem().mediaId)) {
                        callback.onCurrentPosition(element);
                        break;
                    }
                }
            }
        });
    }
}
