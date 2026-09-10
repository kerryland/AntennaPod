package de.danoeh.antennapod.ui.episodeslist;

import android.app.Activity;
import android.util.Log;

import androidx.fragment.app.FragmentActivity;

import androidx.annotation.PluralsRes;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.playback.service.PlaybackServiceInterface;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.ui.BulkDownloader;
import de.danoeh.antennapod.ui.common.IntentUtils;
import de.danoeh.antennapod.ui.share.ShareDialog;
import de.danoeh.antennapod.ui.view.LocalDeleteModal;
import de.danoeh.antennapod.usecase.QueueUseCase;

import org.greenrobot.eventbus.EventBus;

public class EpisodeMultiSelectActionHandler {
    private static final String TAG = "EpisodeSelectHandler";
    private final Activity activity;
    private final int actionId;
    private int totalNumItems = 0;

    private boolean queueAdditionsArePermanent = true;

    public EpisodeMultiSelectActionHandler(Activity activity, int actionId) {
        this.activity = activity;
        this.actionId = actionId;
    }

    public void setQueueAdditionsArePermanent(boolean queueAdditionsArePermanent) {
        this.queueAdditionsArePermanent = queueAdditionsArePermanent;
    }

    public boolean isHandlingAction() {
        return actionId == R.id.add_to_queue_item
                || actionId == R.id.add_to_queue_play_next_item
                || actionId == R.id.remove_from_queue_item
                || actionId == R.id.remove_inbox_item
                || actionId == R.id.mark_read_item
                || actionId == R.id.mark_unread_item
                || actionId == R.id.download_item
                || actionId == R.id.remove_item
                || actionId == R.id.add_to_favorites_item
                || actionId == R.id.remove_from_favorites_item
                || actionId == R.id.reset_position
                || actionId == R.id.share_item
                || actionId == R.id.move_to_top_item
                || actionId == R.id.move_to_bottom_item
                || actionId == R.id.move_to_play_next_item;
    }

    private void skipIfPlaying(List<FeedItem> items, Runnable callback) {
        MenuItemAssistant.skipIfPlaying(activity, items, callback);
    }

    public void handleAction(List<FeedItem> items) {
        if (actionId == R.id.add_to_queue_item) {
            queueChecked(items, queueAdditionsArePermanent, false);
        } else if (actionId == R.id.add_to_queue_play_next_item) {
            queueChecked(items, queueAdditionsArePermanent, true);
        } else if (actionId == R.id.remove_from_queue_item) {
            skipIfPlaying(items, () ->
                    removeFromQueueChecked(items));
        } else if (actionId == R.id.remove_inbox_item) {
            removeFromInboxChecked(items);
        } else if (actionId == R.id.mark_read_item) {
            skipIfPlaying(items, () ->
                    markedCheckedPlayed(items));
        } else if (actionId == R.id.mark_unread_item) {
            markedCheckedUnplayed(items);
        } else if (actionId == R.id.download_item) {
            downloadChecked(items);
        } else if (actionId == R.id.remove_item) {
            skipIfPlaying(items, () ->
                    LocalDeleteModal.showLocalFeedDeleteWarningIfNecessary(activity, items,
                            () -> deleteChecked(items)));
        } else if (actionId == R.id.add_to_favorites_item) {
            addToFavoritesChecked(items);
        } else if (actionId == R.id.remove_from_favorites_item) {
            removeFromFavoritesChecked(items);
        } else if (actionId == R.id.reset_position) {
            resetPositionChecked(items);
        } else if (actionId == R.id.share_item) {
            shareChecked(items);
        } else if (actionId == R.id.move_to_top_item) {
            moveToTopChecked(items);
        } else if (actionId == R.id.move_to_bottom_item) {
            moveToBottomChecked(items);
        } else if (actionId == R.id.move_to_play_next_item) {
            moveToPlayNext(items);

        } else {
            Log.e(TAG, "Unrecognized speed dial action item. Do nothing. id=" + actionId);
        }
    }

    private void moveToPlayNext(List<FeedItem> items) {
        QueueUseCase.getInstance().moveToPlayNext(activity, items, null);
    }

    private void queueChecked(List<FeedItem> items, boolean permanent, boolean playNext) {
        // Count here to give accurate number in snackbar
        List<FeedItem> toQueue = new ArrayList<>();
        for (FeedItem episode : items) {
            if (episode.hasMedia() && !episode.isTagged(FeedItem.TAG_QUEUE)) {
                toQueue.add(episode);
            }
        }
        DBWriter.addQueueItem(activity, permanent, toQueue.toArray(new FeedItem[0]));

        if (playNext) {
            moveToPlayNext(toQueue);
        } else {
            showMessage(R.plurals.added_to_queue_message, toQueue.size());
        }
    }

    private void removeFromQueueChecked(List<FeedItem> items) {
        long[] checkedIds = getSelectedIds(items);
        DBWriter.removeQueueItem(checkedIds);
        for (FeedItem item : items) {
            DBWriter.deleteFeedMediaIfAutoDeleteEnabled(activity, item);
        }
        showMessage(R.plurals.removed_from_queue_message, checkedIds.length);
    }

    private void removeFromInboxChecked(List<FeedItem> items) {
        List<FeedItem> markUnplayed = new ArrayList<>();
        for (FeedItem episode : items) {
            if (episode.isNew()) {
                markUnplayed.add(episode);
                episode.setRemoved(true);
            }
        }
        DBWriter.markItemsPlayed(FeedItem.UNPLAYED, false, markUnplayed);
        showMessage(R.plurals.removed_from_inbox_batch_label, markUnplayed.size());
    }

    private void markedCheckedPlayed(List<FeedItem> items) {
        for (FeedItem item : items) {
            item.setRemoved(true);
        }
        DBWriter.markItemsPlayed(FeedItem.PLAYED, true, items);
        DBWriter.removeQueueItem(getSelectedIds(items));

        for (FeedItem item : items) {
            if (!item.getFeed().isLocalFeed() && item.getFeed().getState() != Feed.STATE_NOT_SUBSCRIBED
                    && SynchronizationSettings.isProviderConnected()) {
                FeedMedia media = item.getMedia();
                // not all items have media, Gpodder only cares about those that do
                if (media != null) {
                    EpisodeAction actionPlay = new EpisodeAction.Builder(item, EpisodeAction.PLAY)
                            .currentTimestamp()
                            .started(media.getDuration() / 1000)
                            .position(media.getDuration() / 1000)
                            .total(media.getDuration() / 1000)
                            .build();
                    SynchronizationQueue.getInstance().enqueueEpisodeAction(actionPlay);
                }
            }
        }
        showMessage(R.plurals.marked_as_played_message, items.size());
    }

    private void markedCheckedUnplayed(List<FeedItem> items) {
        DBWriter.markItemsPlayed(FeedItem.UNPLAYED, false, items);
        for (FeedItem item : items) {
            if (!item.getFeed().isLocalFeed() && item.getMedia() != null
                    && item.getFeed().getState() != Feed.STATE_NOT_SUBSCRIBED) {
                SynchronizationQueue.getInstance().enqueueEpisodeAction(
                        new EpisodeAction.Builder(item, EpisodeAction.NEW)
                                .currentTimestamp()
                                .build());
            }
        }
        showMessage(R.plurals.marked_as_unplayed_message, items.size());
    }

    private void downloadChecked(List<FeedItem> items) {
        BulkDownloader.getInstance(activity).downloadAll(activity, items);
        // download the check episodes in the same order as they are currently displayed
        int downloaded = 0;
        for (FeedItem episode : items) {
            if (episode.hasMedia() && !episode.isDownloaded()) {
                downloaded++;
            }
        }
        showMessage(R.plurals.downloading_episodes_message, downloaded);
    }

    private void deleteChecked(List<FeedItem> items) {
        int countHasMedia = 0;
        for (FeedItem feedItem : items) {
            if (!feedItem.hasMedia()) {
                continue;
            }
            if (feedItem.getMedia().isDownloaded()) {
                countHasMedia++;
                DBWriter.deleteFeedMediaOfItem(activity, feedItem.getMedia());
            } else if (DownloadServiceInterface.get().isDownloadingEpisode(feedItem.getMedia().getDownloadUrl())) {
                countHasMedia++;
                DownloadServiceInterface.get().cancel(activity, feedItem.getMedia());
            }
        }
        showMessage(R.plurals.deleted_episode_message, countHasMedia);
    }

    private void addToFavoritesChecked(List<FeedItem> items) {
        int count = 0;
        for (FeedItem episode : items) {
            if (!episode.isTagged(FeedItem.TAG_FAVORITE)) {
                count++;
            }
        }
        DBWriter.addFavoriteItems(items);
        showMessage(R.plurals.added_to_favorites_message, count);
    }

    private void removeFromFavoritesChecked(List<FeedItem> items) {
        int count = 0;
        for (FeedItem episode : items) {
            if (episode.isTagged(FeedItem.TAG_FAVORITE)) {
                count++;
            }
        }
        DBWriter.removeFavoriteItems(items);
        showMessage(R.plurals.removed_from_favorites_message, count);
    }

    private void resetPositionChecked(List<FeedItem> items) {
        List<FeedItem> toReset = new ArrayList<>();
        for (FeedItem episode : items) {
            if (!episode.hasMedia() || episode.getMedia().getPosition() == 0) {
                continue;
            }
            episode.getMedia().setPosition(0);
            if (PlaybackPreferences.getCurrentlyPlayingFeedMediaId() == episode.getMedia().getId()) {
                PlaybackPreferences.writeNoMediaPlaying();
                IntentUtils.sendLocalBroadcast(activity, PlaybackServiceInterface.ACTION_SHUTDOWN_PLAYBACK_SERVICE);
            }
            toReset.add(episode);
        }
        DBWriter.markItemsPlayed(FeedItem.UNPLAYED, true, toReset);
        showMessage(R.plurals.reset_position_message, toReset.size());
    }

    private void shareChecked(List<FeedItem> items) {
        if (items.isEmpty() || !(activity instanceof FragmentActivity)) {
            return;
        }
        FeedItem item = items.get(0);
        activity.runOnUiThread(() -> {
            ShareDialog shareDialog = ShareDialog.newInstance(item);
            shareDialog.show(((FragmentActivity) activity).getSupportFragmentManager(), "ShareEpisodeDialog");
        });
    }

    private void moveToTopChecked(List<FeedItem> items) {
        DBWriter.moveQueueItemsToTop(items);
        showMessage(R.plurals.move_to_top_message, items.size());
    }

    private void moveToBottomChecked(List<FeedItem> items) {
        DBWriter.moveQueueItemsToBottom(items);
        showMessage(R.plurals.move_to_bottom_message, items.size());
    }

    private void showMessage(@PluralsRes int msgId, int numItems) {
        if (numItems == 1) {
            return;
        }
        totalNumItems += numItems;
        activity.runOnUiThread(() -> {
            String text = activity.getResources().getQuantityString(msgId, totalNumItems, totalNumItems);
            EventBus.getDefault().post(new MessageEvent(text));
        });
    }

    private long[] getSelectedIds(List<FeedItem> items) {
        long[] checkedIds = new long[items.size()];
        for (int i = 0; i < items.size(); ++i) {
            checkedIds[i] = items.get(i).getId();
        }
        return checkedIds;
    }
}
