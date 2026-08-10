package de.danoeh.antennapod.ui.episodeslist;

import android.content.Context;
import android.util.Log;

import androidx.media3.common.MediaItem;

import java.util.List;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.playback.base.MediaItemAdapter;
import de.danoeh.antennapod.playback.service.PlaybackController;

public class MenuItemAssistant {
    private static String TAG = "MenuItemAssistant";

    public static void skipIfPlaying(Context context, FeedItem selectedItem, Runnable callback) {
        PlaybackController.bindToMedia3Service(context, controller -> {
            MediaItem currentMediaItem = controller.getCurrentMediaItem();
            if (currentMediaItem != null && selectedItem.getMedia() != null) {
                String currentMediaId = currentMediaItem.mediaId;
                String selectedMediaId = Long.toString(selectedItem.getMedia().getId());
                Log.d(TAG, "Currently playing media: " + currentMediaId + " selected media " + selectedMediaId);
                if (currentMediaId.equals(selectedMediaId)) {
                    Log.d(TAG, "Play next track");
                    controller.seekToNextMediaItem();
                } else {
                    Log.d(TAG, "Should not play next track");
                }
            }
            if (callback != null) {
                callback.run();
            }
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
                    if ((MediaItemAdapter.fromPlayableStub(feedItem.getMedia()).mediaId).equals(controller.getCurrentMediaItem().mediaId)) {
                        callback.onCurrentPosition(element);
                        break;
                    }
                }
            }
        });
    }
}
