package de.danoeh.antennapod.storage.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.preferences.UserPreferences.EnqueueLocation;
import de.danoeh.antennapod.model.playback.Playable;

/**
 * Determine the positions of the new {@link FeedItem} in the queue.
 */
public class ItemEnqueuePositionCalculator {

    @NonNull
    private final EnqueueLocation enqueueLocation;

    public ItemEnqueuePositionCalculator(@NonNull EnqueueLocation enqueueLocation) {
        this.enqueueLocation = enqueueLocation;
    }

    /**
     * Determine the position (0-based) that the item(s) should be inserted to the named queue.
     *
     * @param curQueue           the queue to which the item is to be inserted
     * @param currentPlaying     the currently playing media
     */
    public int calcPosition(@NonNull List<FeedItem> curQueue, @NonNull FeedItem item, @Nullable Playable currentPlaying) {
        switch (enqueueLocation) {
            case PRIORITY:
                return calcPriorityPosition(curQueue, item);
            case BACK:
                return curQueue.size();
            case FRONT:
                // Return not necessarily 0, so that when a list of items are downloaded and enqueued
                // in succession of calls (e.g., users manually tapping download one by one),
                // the items enqueued are kept the same order.
                // Simply returning 0 will reverse the order.
                return getPositionOfFirstNonDownloadingItem(0, curQueue);
            case AFTER_CURRENTLY_PLAYING:
                int currentlyPlayingPosition = getCurrentlyPlayingPosition(curQueue, currentPlaying);
                return getPositionOfFirstNonDownloadingItem(
                        currentlyPlayingPosition + 1, curQueue);
            case RANDOM:
                Random random = new Random();
                return random.nextInt(curQueue.size() + 1);
            default:
                throw new AssertionError("calcPosition() : unrecognized enqueueLocation option: " + enqueueLocation);
        }
    }

    public int calcPriorityPosition(@NonNull List<FeedItem> queue, @NonNull FeedItem newItem) {
        if (queue.isEmpty() || newItem.getFeed() == null) {
            return queue.size();
        }

        long targetFeedId = newItem.getFeedId();

        List<Integer> sameFeedIndices = new ArrayList<>();
        for (int i = 0; i < queue.size(); i++) {
            FeedItem item = queue.get(i);
            if (item.getFeed() != null && item.getFeedId() == targetFeedId) {
                sameFeedIndices.add(i);
            }
        }

        // Items from the same feed exist in the queue.
        // Find the right date to add this one before (or after)
        if (!sameFeedIndices.isEmpty()) {
            for (int index : sameFeedIndices) {
                FeedItem existingItem = queue.get(index);

                if (newItem.getFeed().getPreferences().getPlaybackOrder() == FeedPreferences.PlaybackOrderSetting.OLDEST_FIRST) {
                    if (isAfter(existingItem.getPubDate(), newItem.getPubDate())) {
                        return index;
                    }
                } else {
                    // NEWEST_FIRSTe
                    if (isBefore(existingItem.getPubDate(), newItem.getPubDate())) {
                        return index;
                    }
                }
            }

            return sameFeedIndices.get(sameFeedIndices.size() - 1) + 1;
        }

        // No items from this feed exist in the queue
        for (int i = 0; i < queue.size(); i++) {
            FeedItem existingItem = queue.get(i);
            // if newItem has higher priority feed, insert now.
            if (comparePriorityFeeds(newItem.getFeed(), existingItem.getFeed()) < 0) {
                return i;
            }
        }

        // If newItem has lower priority than all current feeds in the queue, append to back
        return queue.size();
    }

    /**
     * Compares two feeds based on sorting hierarchy: Priority -> Title -> ID.
     */
    private int comparePriorityFeeds(Feed f1, Feed f2) {
        if (f1 == f2 || f1.getId() == f2.getId()) {
            return 0;
        }

        int p1 = (f1.getPreferences() != null) ? f1.getPreferences().getPriority() : Integer.MAX_VALUE;
        int p2 = (f2.getPreferences() != null) ? f2.getPreferences().getPriority() : Integer.MAX_VALUE;
        int priorityCompare = Integer.compare(p1, p2);
        if (priorityCompare != 0) {
            return priorityCompare;
        }

        String t1 = (f1.getTitle() != null) ? f1.getTitle() : "";
        String t2 = (f2.getTitle() != null) ? f2.getTitle() : "";
        int titleCompare = t1.compareToIgnoreCase(t2);
        if (titleCompare != 0) {
            return titleCompare;
        }

        return Long.compare(f1.getId(), f2.getId());
    }

    private boolean isAfter(Date d1, Date d2) {
        if (d1 == null || d2 == null) return false;
        return d1.after(d2);
    }

    private boolean isBefore(Date d1, Date d2) {
        if (d1 == null || d2 == null) return false;
        return d1.before(d2);
    }


    private int getPositionOfFirstNonDownloadingItem(int startPosition, List<FeedItem> curQueue) {
        final int curQueueSize = curQueue.size();
        for (int i = startPosition; i < curQueueSize; i++) {
            if (!isItemAtPositionDownloading(i, curQueue)) {
                return i;
            } // else continue to search;
        }
        return curQueueSize;
    }

    private boolean isItemAtPositionDownloading(int position, List<FeedItem> curQueue) {
        FeedItem curItem;
        try {
            curItem = curQueue.get(position);
        } catch (IndexOutOfBoundsException e) {
            curItem = null;
        }
        return curItem != null
                && curItem.getMedia() != null
                && DownloadServiceInterface.get().isDownloadingEpisode(curItem.getMedia().getDownloadUrl());
    }

    private static int getCurrentlyPlayingPosition(@NonNull List<FeedItem> curQueue,
                                                   @Nullable Playable currentPlaying) {
        if (!(currentPlaying instanceof FeedMedia)) {
            return -1;
        }
        final long curPlayingItemId = ((FeedMedia) currentPlaying).getItem().getId();
        for (int i = 0; i < curQueue.size(); i++) {
            if (curPlayingItemId == curQueue.get(i).getId()) {
                return i;
            }
        }
        return -1;
    }
}
