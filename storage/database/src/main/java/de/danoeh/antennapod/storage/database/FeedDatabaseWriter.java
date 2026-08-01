package de.danoeh.antennapod.storage.database;

import android.content.Context;
import android.util.Log;
import de.danoeh.antennapod.event.FeedListUpdateEvent;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Creates and updates feeds in the database.
 */
public abstract class FeedDatabaseWriter {
    private static final String TAG = "FeedDbWriter";

    private static Feed searchFeedByIdentifyingValueOrID(Feed feed) {
        if (feed.getId() != 0) {
            return DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE);
        } else {
            List<Feed> feeds = DBReader.getFeedList();
            for (Feed f : feeds) {
                if (f.getIdentifyingValue().equals(feed.getIdentifyingValue())) {
                    f.setItems(DBReader.getFeedItemList(f, FeedItemFilter.unfiltered(),
                            SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE));
                    return f;
                }
            }
        }
        return null;
    }

    /**
     * Adds new Feeds to the database or updates the old versions if they already exists. If another Feed with the same
     * identifying value already exists, this method will add new FeedItems from the new Feed to the existing Feed.
     * These FeedItems will be marked as unread with the exception of the most recent FeedItem.
     *
     * @param context Used for accessing the DB.
     * @param newFeed The new Feed object.
     * @param removeUnlistedItems The item list in the new Feed object is considered to be exhaustive.
     *                            I.e. items are removed from the database if they are not in this item list.
     * @return The updated Feed from the database if it already existed, or the new Feed from the parameters otherwise.
     */
    public static synchronized Feed updateFeed(Context context, Feed newFeed, boolean removeUnlistedItems) {
        Feed resultFeed;
        List<FeedItem> unlistedItems = new ArrayList<>();

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();

        // Look up feed in the feedslist
        final Feed savedFeed = searchFeedByIdentifyingValueOrID(newFeed);
        if (savedFeed == null) {
            Log.d(TAG, "Found no existing Feed with title "
                            + newFeed.getTitle() + ". Adding as new one.");

            resultFeed = newFeed;
        } else {
            Log.d(TAG, "Feed with title " + newFeed.getTitle()
                        + " already exists. Syncing new with existing one.");

            Collections.sort(newFeed.getItems(), new FeedItemPubdateComparator());

            FeedItemDuplicateGuesserPool newFeedDuplicateGuesser = new FeedItemDuplicateGuesserPool(newFeed.getItems());
            FeedItemDuplicateGuesserPool savedFeedDuplicateGuesser
                    = new FeedItemDuplicateGuesserPool(savedFeed.getItems());

            if (newFeed.getPageNr() == savedFeed.getPageNr()) {
                savedFeed.updateFromOther(newFeed);
                savedFeed.getPreferences().updateFromOther(newFeed.getPreferences());
            } else {
                Log.d(TAG, "New feed has a higher page number.");
                savedFeed.setNextPageLink(newFeed.getNextPageLink());
            }

            // get the most recent date now, before we start changing the list
            FeedItem priorMostRecent = savedFeed.getMostRecentItem();
            Date priorMostRecentDate = new Date();
            if (priorMostRecent != null) {
                priorMostRecentDate = priorMostRecent.getPubDate();
            }

            // Look for new or updated Items
            for (int idx = 0; idx < newFeed.getItems().size(); idx++) {
                final FeedItem item = newFeed.getItems().get(idx);

                FeedItem possibleDuplicate = newFeedDuplicateGuesser.guessDuplicate(item);
                if (!newFeed.isLocalFeed() && possibleDuplicate != null && item != possibleDuplicate) {
                    // Canonical episode is the first one returned (usually oldest)
                    DBWriter.addDownloadStatus(new DownloadResult(item.getTitle(),
                            savedFeed.getId(), Feed.FEEDFILETYPE_FEED, false,
                            DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE,
                            "The podcast host appears to have added the same episode twice. "
                                    + "AntennaPod still refreshed the feed and attempted to repair it."
                                    + "\n\nOriginal episode:\n" + duplicateEpisodeDetails(item)
                                    + "\n\nSecond episode that is also in the feed:\n"
                                    + duplicateEpisodeDetails(possibleDuplicate)));
                    continue;
                }

                FeedItem oldItem = savedFeedDuplicateGuesser.findById(item);
                if (!newFeed.isLocalFeed() && oldItem == null) {
                    oldItem = savedFeedDuplicateGuesser.guessDuplicate(item);
                    if (oldItem != null) {
                        Log.d(TAG, "Repaired duplicate: " + oldItem + ", " + item);
                        DBWriter.addDownloadStatus(new DownloadResult(item.getTitle(),
                                savedFeed.getId(), Feed.FEEDFILETYPE_FEED, false,
                                DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE,
                                "The podcast host changed the ID of an existing episode instead of just "
                                        + "updating the episode itself. AntennaPod still refreshed the feed and "
                                        + "attempted to repair it."
                                        + "\n\nOriginal episode:\n" + duplicateEpisodeDetails(oldItem)
                                        + "\n\nNow the feed contains:\n" + duplicateEpisodeDetails(item)));
                        oldItem.setItemIdentifier(item.getItemIdentifier());

                        if (oldItem.isPlayed() && oldItem.getMedia() != null
                                && savedFeed.getState() != Feed.STATE_NOT_SUBSCRIBED) {
                            EpisodeAction action = new EpisodeAction.Builder(oldItem, EpisodeAction.PLAY)
                                    .currentTimestamp()
                                    .started(oldItem.getMedia().getDuration() / 1000)
                                    .position(oldItem.getMedia().getDuration() / 1000)
                                    .total(oldItem.getMedia().getDuration() / 1000)
                                    .build();
                            SynchronizationQueue.getInstance().enqueueEpisodeAction(action);
                        }
                    }
                }

                if (oldItem != null) {
                    oldItem.updateFromOther(item);
                } else {
                    Log.d(TAG, "Found new item: " + item.getTitle());
                    item.setFeed(savedFeed);

                    if (idx >= savedFeed.getItems().size()) {
                        savedFeed.getItems().add(item);
                    } else {
                        savedFeed.getItems().add(idx, item);
                    }
                    savedFeedDuplicateGuesser.add(item);
                }
            }

            // identify items to be removed
            if (removeUnlistedItems) {
                Iterator<FeedItem> it = savedFeed.getItems().iterator();
                while (it.hasNext()) {
                    FeedItem feedItem = it.next();
                    if (newFeedDuplicateGuesser.findById(feedItem) == null) {
                        unlistedItems.add(feedItem);
                        it.remove();
                    }
                }
            }

            // update attributes
            savedFeed.setLastModified(newFeed.getLastModified());
            savedFeed.setType(newFeed.getType());
            savedFeed.setLastUpdateFailed(false);

            resultFeed = savedFeed;
        }

        try {
            if (savedFeed == null) {
                DBWriter.addNewFeed(context, newFeed).get();
                // Update with default values that are set in database
                resultFeed = searchFeedByIdentifyingValueOrID(newFeed);
            } else {
                DBWriter.setCompleteFeed(savedFeed).get();
            }
            if (removeUnlistedItems) {
                DBWriter.deleteFeedItems(context, unlistedItems).get();
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        // We need to add to queue after items are saved to database
        List<FeedItem> addToQueue = new ArrayList<>();
        List<FeedItem> removeFromQueue = new ArrayList<>();

        chooseFeedItemsForInboxOrQueue(addToQueue, removeFromQueue);

        for (FeedItem removeFeedItem : removeFromQueue) {
            DBWriter.removeQueueItem(context, false, removeFeedItem);
        }
        DBWriter.addQueueItem(context, addToQueue.toArray(new FeedItem[0]));
        adapter.close();

        if (savedFeed != null) {
            EventBus.getDefault().post(new FeedListUpdateEvent(savedFeed));
        } else {
            EventBus.getDefault().post(new FeedListUpdateEvent(Collections.emptyList()));
        }

        return resultFeed;
    }

    private static void chooseFeedItemsForInboxOrQueue(List<FeedItem> itemsToAddToQueue, List<FeedItem> removedFromQueue) {
        List<Feed> feeds = DBReader.getFeedList();

        List<FeedItem> queue = DBReader.getQueue();

        for (Feed feed : feeds) {
            if (feed.getState() != Feed.STATE_SUBSCRIBED) {
                continue;
            }
            int addCount = 0;
            Optional<FeedItem> mostRecent = Optional.empty();

            List<FeedItem> feedItems;
            switch (feed.getPreferences().getPlaybackOrder()) {
                case FeedPreferences.PlaybackOrderSetting.NEWEST_FIRST:
                    feedItems = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                            SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE);
                    if (!feedItems.isEmpty()) {
                        mostRecent = Optional.of(feedItems.get(0));
                    }
                    break;
                case FeedPreferences.PlaybackOrderSetting.OLDEST_FIRST:
                    feedItems = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                            SortOrder.DATE_OLD_NEW, 0, Integer.MAX_VALUE);
                    if (!feedItems.isEmpty()) {
                        mostRecent = Optional.of(feedItems.get(feedItems.size()-1));
                    }
                    break;
                default:
                    throw new IllegalStateException();
            }
            List<FeedItem> unplayedEpisodes = new ArrayList<>();

            // Where does user want new episodes? In the inbox, or queue, or nowhere.
            FeedPreferences.NewEpisodesAction episodeDestination = feed.getPreferences().getNewEpisodesAction();
            if (episodeDestination == FeedPreferences.NewEpisodesAction.GLOBAL) {
                episodeDestination = UserPreferences.getNewEpisodesAction();
            }

            for (FeedItem feedItem : feedItems) {
                if (feedItem.isPlayed()) {
                    continue;
                }
                if (mostRecent.isEmpty()) {
                    mostRecent = Optional.of(feedItem);
                }
                if (!feedItem.isPlayed()) { // unplayed or new
                    unplayedEpisodes.add(feedItem);
                }
                if (addCount < feed.getPreferences().getMaxEpisodes()) {
                    addCount++;

                    if (queue.contains(feedItem)) {
                        // Do nothing
                    } else if (episodeDestination == FeedPreferences.NewEpisodesAction.ADD_TO_INBOX) {
                        feedItem.setNew();
                        DBWriter.setItemList(List.of(feedItem));
                    } else if (episodeDestination == FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE) {
                        if (!queue.contains(feedItem)) {
                            itemsToAddToQueue.add(feedItem);
                        }
                    }
                } else {
                    if (queue.contains(feedItem)) {
                        removedFromQueue.add(feedItem);
                    }
                    if (feedItem.isNew()) {
                        feedItem.setPlayed(false);
                    }
                }
            }
        }
    }

    private static String duplicateEpisodeDetails(FeedItem item) {
        return "Title: " + item.getTitle()
                + "\nID: " + item.getItemIdentifier()
                + ((item.getMedia() == null) ? "" : "\nURL: " + item.getMedia().getDownloadUrl());
    }
}
