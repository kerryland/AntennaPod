package de.danoeh.antennapod.net.download.service.feed;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;


public class DestinationSelector {
    private static String TAG = "DestinationSelector";

    /**
     * Decide where to put feed items, based on Feed Priority, Playback Order, and Max Episodes
     */
    public static void populateInboxOrQueue(Context context, List<Feed> feeds) {
        if (feeds.isEmpty()) {
            return;
        }
        List<FeedItem> queueAdditions = new ArrayList<>();
        List<FeedItem> queueRemovals = new ArrayList<>();

        Set<FeedItem> currentQueue = new HashSet<>(DBReader.getQueue());
        List<FeedItem> inboxStateChanges = new ArrayList<>();

        for (Feed feed : feeds) {
            if (feed.getState() != Feed.STATE_SUBSCRIBED) {
                continue;
            }

            boolean feedIsOldestFirst = feed.getPreferences().getPlaybackOrder()
                    == FeedPreferences.PlaybackOrderSetting.OLDEST_FIRST;
            SortOrder sortOrder = feedIsOldestFirst
                    ? SortOrder.DATE_OLD_NEW
                    : SortOrder.DATE_NEW_OLD;

            List<FeedItem> feedItems = DBReader.getFeedItemList(feed,
                    new FeedItemFilter(FeedItemFilter.UNPLAYED, FeedItemFilter.NEW,
                            FeedItemFilter.EXCLUDE_REMOVED),
                    sortOrder, 0, Integer.MAX_VALUE
            );

            FeedPreferences.NewEpisodesAction episodeDestination = feed.getPreferences().getNewEpisodesAction();
            if (episodeDestination == FeedPreferences.NewEpisodesAction.GLOBAL) {
                episodeDestination = UserPreferences.getNewEpisodesAction();
            }

            int maxEpisodes = feed.getPreferences().getMaxEpisodes();
            int addCount = 0;

            if (feedIsOldestFirst) {
                for (FeedItem feedItem : feedItems) {
                    if (feedItem.isTagged(FeedItem.TAG_QUEUE_PERMANENT)) {
                        maxEpisodes--;
                    }
                }
            }

            for (FeedItem feedItem : feedItems) {
                if (feedItem.isTagged(FeedItem.TAG_QUEUE_PERMANENT)) {
                    continue;
                }

                boolean isInQueue = currentQueue.contains(feedItem);

                if (addCount < maxEpisodes) {
                    addCount++;

                    if (isInQueue) {
                        continue;
                    }

                    if (episodeDestination == FeedPreferences.NewEpisodesAction.ADD_TO_INBOX) {
                        if (!feedItem.isNew()) {
                            feedItem.setNew();
                            feedItem.setAddedToInboxOrQueue(new Date());
                            inboxStateChanges.add(feedItem);
                        }
                    } else if (episodeDestination == FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE) {
                        feedItem.setAddedToInboxOrQueue(new Date());
                        queueAdditions.add(feedItem);
                    }
                } else { // Limit exceeded
                    if (isInQueue) {
                        queueRemovals.add(feedItem);
                    }

                    if (feedItem.isNew()) {
                        feedItem.setPlayed(false);
                        inboxStateChanges.add(feedItem);
                    }
                }
            }
        }

        if (!inboxStateChanges.isEmpty()) {
            DBWriter.setItemList(inboxStateChanges);
        }
        if (!queueAdditions.isEmpty()) {
            DBWriter.setItemList(queueAdditions);
        }

        long[] removeFromQueueItemIds = new long[queueRemovals.size()];
        for (int i = 0; i < queueRemovals.size(); i++) {
            removeFromQueueItemIds[i] = queueRemovals.get(i).getId();
            if (UserPreferences.isAutoDelete()) {
                Log.d(TAG, "Deleting media because removing from queue");
                DBWriter.deleteFeedMediaOfItem(context, queueRemovals.get(i).getMedia());
            }
        }
        DBWriter.removeQueueItem(removeFromQueueItemIds);
        DBWriter.addQueueItem(context, queueAdditions.toArray(new FeedItem[0]));

        Log.d(TAG, "Inbox changes: " + inboxStateChanges.size()
                + ". Queue changes: "  + (queueRemovals.size() + queueAdditions.size()));
    }
}
