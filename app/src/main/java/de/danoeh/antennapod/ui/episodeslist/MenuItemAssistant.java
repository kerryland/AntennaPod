package de.danoeh.antennapod.ui.episodeslist;

import android.content.Context;
import android.util.Log;

import androidx.media3.common.MediaItem;

import de.danoeh.antennapod.model.feed.FeedItem;
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
}
