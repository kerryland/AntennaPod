package de.danoeh.antennapod.net.download.service.feed;

import static org.junit.Assert.*;

import static de.danoeh.antennapod.model.feed.FeedPreferences.SPEED_USE_GLOBAL;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Future;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedFilter;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

@RunWith(RobolectricTestRunner.class)
public class DestinationSelectorTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();
        SynchronizationQueue.setInstance(new SynchronizationQueueStub());
    }

    enum FeedItemLocation {
        QUEUE_PLAYED,
        QUEUE_UNPLAYED,
        INBOX,
        PLAYED,
        UNPLAYED
    }

    private Feed createFeed() {
        Feed feed = new Feed("url", null, null);
        feed.setPreferences(new FeedPreferences(feed.getId(), FeedPreferences.AutoDownloadSetting.DISABLED,
                true, FeedPreferences.AutoDeleteAction.ALWAYS, VolumeAdaptionSetting.OFF, null, null,
                new FeedFilter(), SPEED_USE_GLOBAL, 0, 0, FeedPreferences.SkipSilence.GLOBAL,
                false, FeedPreferences.NewEpisodesAction.GLOBAL, 0,
                FeedPreferences.PlaybackOrderSetting.NEWEST_FIRST, 3, new HashSet<>()));

        feed.getPreferences().setPlaybackOrder(FeedPreferences.PlaybackOrderSetting.NEWEST_FIRST);
        feed.setItems(new ArrayList<>());
        return feed;
    }

    @Test
    public void testQueuePopulated_Newest_First_Max_3_Episodes_Into_Inbox() {

        List<FeedItem> feedItems = new ArrayList<>();
        List<FeedItem> queueItems = new ArrayList<>();

        // Given some episodes, and a user who wants PRIORITY order
        UserPreferences.setEnqueueLocation(UserPreferences.EnqueueLocation.PRIORITY);

        feedItems.add(makeTestFeedItem(17, FeedItemLocation.QUEUE_UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(18, FeedItemLocation.UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(19, FeedItemLocation.PLAYED, queueItems));
        feedItems.add(makeTestFeedItem(20, FeedItemLocation.INBOX, queueItems));

        Feed feed = prepareTestData(FeedPreferences.NewEpisodesAction.ADD_TO_INBOX,
                FeedPreferences.PlaybackOrderSetting.NEWEST_FIRST,
                3, feedItems, queueItems);

        FeedDatabaseWriter.updateFeed(context, feed, false);

        // When we update the inbox
        DestinationSelector.populateInboxOrQueue(context, Collections.singletonList(feed));
        DBWriter.waitForDatabase(); // Make sure the database is updated

        // Then check inbox
        List<FeedItem> inbox = DBReader.getFeedItemList(feed, new FeedItemFilter(FeedItemFilter.NEW), SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE);
        assertEquals(2, inbox.size());
        assertEquals("EPISODE 20", inbox.get(0).getTitle());
        assertEquals("EPISODE 18", inbox.get(1).getTitle());

        // and check the queue
        List<FeedItem> queue = DBReader.getQueue();
        assertEquals(1, queue.size());
        assertEquals("EPISODE 17", queue.get(0).getTitle());
    }

    @Test
    public void testQueuePopulated_Newest_First_Max_3_Episodes_Into_Queue() {
        UserPreferences.setEnqueueLocation(UserPreferences.EnqueueLocation.PRIORITY);

        List<FeedItem> feedItems = new ArrayList<>();
        List<FeedItem> queueItems = new ArrayList<>();

        feedItems.add(makeTestFeedItem(17, FeedItemLocation.QUEUE_UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(18, FeedItemLocation.UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(19, FeedItemLocation.PLAYED, queueItems));
        feedItems.add(makeTestFeedItem(20, FeedItemLocation.INBOX, queueItems));

        Feed feed = prepareTestData(FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE,
                FeedPreferences.PlaybackOrderSetting.NEWEST_FIRST,
                3, feedItems, queueItems);

        FeedDatabaseWriter.updateFeed(context, feed, false);

        //----------------------------------------------------------------------
        DestinationSelector.populateInboxOrQueue(context, Collections.singletonList(feed));
        //----------------------------------------------------------------------

        DBWriter.waitForDatabase(); // Make sure the database is updated

        // Check inbox
        List<FeedItem> inbox = DBReader.getFeedItemList(feed, new FeedItemFilter(FeedItemFilter.NEW), SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE);
        assertEquals(0, inbox.size());

        // Check queue

        List<FeedItem> queue = DBReader.getQueue();
        assertEquals(3, queue.size());
        assertEquals("EPISODE 20", queue.get(0).getTitle());
        assertEquals("EPISODE 18", queue.get(1).getTitle());
        assertEquals("EPISODE 17", queue.get(2).getTitle());
    }

    @Test
    public void testQueuePopulated_Oldest_First_Max_2_Episodes_Into_Inbox() {
        List<FeedItem> feedItems = new ArrayList<>();
        List<FeedItem> queueItems = new ArrayList<>();

        feedItems.add(makeTestFeedItem(17, FeedItemLocation.PLAYED, queueItems));
        feedItems.add(makeTestFeedItem(18, FeedItemLocation.PLAYED, queueItems));
        feedItems.add(makeTestFeedItem(19, FeedItemLocation.QUEUE_UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(20, FeedItemLocation.UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(21, FeedItemLocation.UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(22, FeedItemLocation.INBOX, queueItems));

        Feed feed = prepareTestData(FeedPreferences.NewEpisodesAction.ADD_TO_INBOX,
                FeedPreferences.PlaybackOrderSetting.OLDEST_FIRST,
                2, feedItems, queueItems);

        FeedDatabaseWriter.updateFeed(context, feed, false);

        //----------------------------------------------------------------------
        DestinationSelector.populateInboxOrQueue(context, Collections.singletonList(feed));
        //----------------------------------------------------------------------

        DBWriter.waitForDatabase(); // Make sure the database is updated

        // Check inbox
        List<FeedItem> inbox = DBReader.getFeedItemList(feed, new FeedItemFilter(FeedItemFilter.NEW), SortOrder.DATE_OLD_NEW, 0, Integer.MAX_VALUE);
        assertEquals(1, inbox.size());
        assertEquals("EPISODE 20", inbox.get(0).getTitle());

        // Check queue
        List<FeedItem> queue = DBReader.getQueue();
        assertEquals(1, queue.size());
        assertEquals("EPISODE 19", queue.get(0).getTitle());
    }

    @Test
    public void testQueuePopulated_Oldest_First_Max_3_Episodes_Into_Queue() {
        List<FeedItem> feedItems = new ArrayList<>();
        List<FeedItem> queueItems = new ArrayList<>();

        feedItems.add(makeTestFeedItem(17, FeedItemLocation.PLAYED, queueItems));
        feedItems.add(makeTestFeedItem(18, FeedItemLocation.PLAYED, queueItems));
        feedItems.add(makeTestFeedItem(19, FeedItemLocation.QUEUE_UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(20, FeedItemLocation.UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(21, FeedItemLocation.UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(22, FeedItemLocation.INBOX, queueItems));

        Feed feed = prepareTestData(FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE,
                FeedPreferences.PlaybackOrderSetting.OLDEST_FIRST,
                3, feedItems, queueItems);

        FeedDatabaseWriter.updateFeed(context, feed, false);

        //----------------------------------------------------------------------
        DestinationSelector.populateInboxOrQueue(context, Collections.singletonList(feed));
        //----------------------------------------------------------------------
        DBWriter.waitForDatabase(); // Make sure the database is updated

        // Check inbox
        List<FeedItem> inbox = DBReader.getFeedItemList(feed, new FeedItemFilter(FeedItemFilter.NEW), SortOrder.DATE_OLD_NEW, 0, Integer.MAX_VALUE);
        assertEquals(0, inbox.size());

        // Check queue
        List<FeedItem> queue = DBReader.getQueue();
        assertEquals(3, queue.size());
        assertEquals("EPISODE 19", queue.get(0).getTitle());
        assertEquals("EPISODE 20", queue.get(1).getTitle());
        assertEquals("EPISODE 21", queue.get(2).getTitle());
    }

    @Test
    // "permanent" items in the queue should not be removed when counting "max episodes"
    public void testQueuePopulated_Oldest_First_Max_2_Episodes_Into_Queue_Ignores_Permanent() {
        List<FeedItem> feedItems = new ArrayList<>();
        List<FeedItem> queueItems = new ArrayList<>();

        // When we have 6 unplayed podcasts
        feedItems.add(makeTestFeedItem(17, FeedItemLocation.UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(18, FeedItemLocation.QUEUE_UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(19, FeedItemLocation.QUEUE_UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(20, FeedItemLocation.QUEUE_UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(21, FeedItemLocation.UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(22, FeedItemLocation.INBOX, queueItems));

        // And three of the podcasts are already "permanent" in the queue
        getFeedItem(feedItems, 18).addTag(FeedItem.TAG_QUEUE_PERMANENT);
        getFeedItem(feedItems, 19).addTag(FeedItem.TAG_QUEUE_PERMANENT);
        getFeedItem(feedItems, 20).addTag(FeedItem.TAG_QUEUE_PERMANENT);

        Feed feed = prepareTestData(FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE,
                FeedPreferences.PlaybackOrderSetting.OLDEST_FIRST,
                2, feedItems, queueItems);

        FeedDatabaseWriter.updateFeed(context, feed, false);

        // When we populate the queue or inbox with "max 2" episodes
        //----------------------------------------------------------------------
        DestinationSelector.populateInboxOrQueue(context, Collections.singletonList(feed));
        //----------------------------------------------------------------------
        DBWriter.waitForDatabase(); // Make sure the database is updated

        // Then the inbox should have no episodes
        List<FeedItem> inbox = DBReader.getFeedItemList(feed, new FeedItemFilter(FeedItemFilter.NEW), SortOrder.DATE_OLD_NEW, 0, Integer.MAX_VALUE);
        assertEquals(0, inbox.size());

        // and the queue should still have THREE episodes (ignoring max 2)
        List<FeedItem> queue = DBReader.getQueue();
        assertEquals(3, queue.size());
        assertEquals("EPISODE 18", queue.get(0).getTitle());
        assertEquals("EPISODE 19", queue.get(1).getTitle());
        assertEquals("EPISODE 20", queue.get(2).getTitle());

        // Now run it again to make sure nothing changes
        DestinationSelector.populateInboxOrQueue(context, Collections.singletonList(feed));
        DBWriter.waitForDatabase(); // Make sure the database is updated

        inbox = DBReader.getFeedItemList(feed, new FeedItemFilter(FeedItemFilter.NEW), SortOrder.DATE_OLD_NEW, 0, Integer.MAX_VALUE);
        assertEquals(0, inbox.size());
        queue = DBReader.getQueue();
        assertEquals(3, queue.size());
    }

    @Test
    // "permanent" items in the queue should not be removed when counting "max episodes"
    // but new ones should be added for NEWEST_FIRST
    public void testQueuePopulated_Newest_First_Max_2_Episodes_Into_Queue_Ignores_Permanent() {
        List<FeedItem> feedItems = new ArrayList<>();
        List<FeedItem> queueItems = new ArrayList<>();

        // When we have 6 unplayed podcasts
        feedItems.add(makeTestFeedItem(17, FeedItemLocation.UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(18, FeedItemLocation.QUEUE_UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(19, FeedItemLocation.QUEUE_UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(20, FeedItemLocation.QUEUE_UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(21, FeedItemLocation.UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(22, FeedItemLocation.INBOX, queueItems));

        // And three of the podcasts are already "permanent" in the queue
        getFeedItem(feedItems, 18).addTag(FeedItem.TAG_QUEUE_PERMANENT);
        getFeedItem(feedItems, 19).addTag(FeedItem.TAG_QUEUE_PERMANENT);
        getFeedItem(feedItems, 20).addTag(FeedItem.TAG_QUEUE_PERMANENT);

        Feed feed = prepareTestData(FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE,
                FeedPreferences.PlaybackOrderSetting.NEWEST_FIRST,
                1, feedItems, queueItems);

        FeedDatabaseWriter.updateFeed(context, feed, false);

        // When we populate the queue or inbox with "max 2" episodes
        //----------------------------------------------------------------------
        DestinationSelector.populateInboxOrQueue(context, Collections.singletonList(feed));
        //----------------------------------------------------------------------
        DBWriter.waitForDatabase(); // Make sure the database is updated

        // Then the inbox should have no episodes
        List<FeedItem> inbox = DBReader.getFeedItemList(feed, new FeedItemFilter(FeedItemFilter.NEW), SortOrder.DATE_OLD_NEW, 0, Integer.MAX_VALUE);
        assertEquals(0, inbox.size());

        // and the queue should still have and extra episodes (ignoring max 1)
        List<FeedItem> queue = DBReader.getQueue();
        assertEquals(4, queue.size());
        assertEquals("EPISODE 18", queue.get(0).getTitle());
        assertEquals("EPISODE 19", queue.get(1).getTitle());
        assertEquals("EPISODE 20", queue.get(2).getTitle());
        assertEquals("EPISODE 22", queue.get(3).getTitle()); // was in inbox

        // Now run it again to make sure nothing changes
        DestinationSelector.populateInboxOrQueue(context, Collections.singletonList(feed));
        DBWriter.waitForDatabase(); // Make sure the database is updated

        inbox = DBReader.getFeedItemList(feed, new FeedItemFilter(FeedItemFilter.NEW), SortOrder.DATE_OLD_NEW, 0, Integer.MAX_VALUE);
        assertEquals(0, inbox.size());
        queue = DBReader.getQueue();

        for (FeedItem feedItem : queue) {
            System.out.println(feedItem.getTitle());
        }

        assertEquals(4, queue.size());

        // When an even newer item, and it should replace the previous "most new" item
        feed = DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE);
        feed.getItems().add(makeTestFeedItem(23, FeedItemLocation.INBOX, queueItems));
        FeedDatabaseWriter.updateFeed(context, feed, false);

        // and repopulate
        DestinationSelector.populateInboxOrQueue(context, Collections.singletonList(feed));
        DBWriter.waitForDatabase(); // Make sure the database is updated

        // Then episode 22 should be replaced by episode 23
        inbox = DBReader.getFeedItemList(feed, new FeedItemFilter(FeedItemFilter.NEW), SortOrder.DATE_OLD_NEW, 0, Integer.MAX_VALUE);
        assertEquals(0, inbox.size());
        queue = DBReader.getQueue();
        assertEquals(4, queue.size());
        assertEquals("EPISODE 18", queue.get(0).getTitle());
        assertEquals("EPISODE 19", queue.get(1).getTitle());
        assertEquals("EPISODE 20", queue.get(2).getTitle());
        assertEquals("EPISODE 23", queue.get(3).getTitle());

    }


    private FeedItem getFeedItem(List<FeedItem> feedItems, int day) {
        for (FeedItem feedItem : feedItems) {
            LocalDate date = feedItem.getPubDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            if (date.getDayOfMonth() == day) {
                return feedItem;
            }
        }
        throw new IllegalArgumentException("No feeditem with day " + day + " found");
    }

    @Test
    // Make sure "OLDEST_FIRST" feed items that are 'removed' via 'remove from inbox' still count toward maxEpisodes
    public void testInboxPopulated_Oldest_First_Removed_Counts_Toward_MaxEpisodes() {
        List<FeedItem> feedItems = new ArrayList<>();
        List<FeedItem> queueItems = new ArrayList<>();

        // Given we have items (20 and 22) that were explicitly removed from the inbox
        feedItems.add(makeTestFeedItem(17, FeedItemLocation.PLAYED, queueItems));
        feedItems.add(makeTestFeedItem(18, FeedItemLocation.UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(19, FeedItemLocation.PLAYED, queueItems));
        feedItems.add(makeTestFeedItem(20, FeedItemLocation.UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(21, FeedItemLocation.UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(22, FeedItemLocation.UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(23, FeedItemLocation.UNPLAYED, queueItems));

        getFeedItem(feedItems, 20).setRemoved(true);
        getFeedItem(feedItems, 22).setRemoved(true);

        Feed feed = prepareTestData(FeedPreferences.NewEpisodesAction.ADD_TO_INBOX,
                FeedPreferences.PlaybackOrderSetting.OLDEST_FIRST,
                2, feedItems, queueItems);

        // When we refresh the feed
        FeedDatabaseWriter.updateFeed(context, feed, false);
        DestinationSelector.populateInboxOrQueue(context, Collections.singletonList(feed));

        DBWriter.waitForDatabase(); // Make sure the database is updated

        // Then removed items (20, 22) count toward maxEpisodes=2, so only episode 18 is added
        List<FeedItem> inbox = DBReader.getFeedItemList(feed, new FeedItemFilter(FeedItemFilter.NEW), SortOrder.DATE_OLD_NEW, 0, Integer.MAX_VALUE);
        assertEquals(1, inbox.size());
        assertEquals("EPISODE 18", inbox.get(0).getTitle());
    }

    @Test
    // Make sure "NEWEST_FIRST" feed items that are 'removed' via 'remove from inbox' still count toward maxEpisodes
    public void testInboxPopulated_Newest_First_Removed_Counts_Toward_MaxEpisodes() {
        List<FeedItem> feedItems = new ArrayList<>();
        List<FeedItem> queueItems = new ArrayList<>();

        // Given we have items (20 and 22) that were explicitly removed from the inbox
        feedItems.add(makeTestFeedItem(17, FeedItemLocation.PLAYED, queueItems));
        feedItems.add(makeTestFeedItem(18, FeedItemLocation.UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(19, FeedItemLocation.PLAYED, queueItems));
        FeedItem episode20 = makeTestFeedItem(20, FeedItemLocation.UNPLAYED, queueItems);
        episode20.setRemoved(true);
        feedItems.add(episode20);
        feedItems.add(makeTestFeedItem(21, FeedItemLocation.UNPLAYED, queueItems));
        FeedItem episode22 = makeTestFeedItem(22, FeedItemLocation.UNPLAYED, queueItems);
        episode22.setRemoved(true);
        feedItems.add(episode22);
        feedItems.add(makeTestFeedItem(23, FeedItemLocation.UNPLAYED, queueItems));


        Feed feed = prepareTestData(FeedPreferences.NewEpisodesAction.ADD_TO_INBOX,
                FeedPreferences.PlaybackOrderSetting.NEWEST_FIRST,
                2, feedItems, queueItems);

        // When we refresh the feed
        FeedDatabaseWriter.updateFeed(context, feed, false);
        DestinationSelector.populateInboxOrQueue(context, Collections.singletonList(feed));

        DBWriter.waitForDatabase(); // Make sure the database is updated

        // Then removed items (20, 22) count toward maxEpisodes=2, so only episode 23 is added
        List<FeedItem> inbox = DBReader.getFeedItemList(feed, new FeedItemFilter(FeedItemFilter.NEW), SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE);
        assertEquals(1, inbox.size());
        assertEquals("EPISODE 23", inbox.get(0).getTitle());
    }

    private Feed prepareTestData(FeedPreferences.NewEpisodesAction newEpisodesAction,
                                 FeedPreferences.PlaybackOrderSetting playbackOrderSetting,
                                 int maxEpisodes,
                                 List<FeedItem> feedItems, List<FeedItem> queueItems) {

        AutoDownloadManager.setInstance(new AutoDownloadManager() {
            @Override
            public Future<?> autodownloadUndownloadedItems(Context context) {
                return null;
            }

            @Override
            public void performAutoCleanup(Context context) {

            }
        });

        PlaybackPreferences.init(context);
        PlaybackPreferences.writeNoMediaPlaying();

        final Feed feed = createFeed();
        feed.getPreferences().setNewEpisodesAction(newEpisodesAction);
        feed.getPreferences().setPlaybackOrder(playbackOrderSetting);
        feed.getPreferences().setMaxEpisodes(maxEpisodes);

        feed.setItems(feedItems);

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.setQueue(queueItems);
        adapter.close();
        return feed;
    }

    private FeedItem makeTestFeedItem(int dayOfMonth, FeedItemLocation location, List<FeedItem> queueItems) {
        FeedItem feedItem = new FeedItem();
        feedItem.setId(0); // force an insert
        feedItem.setTitle("EPISODE " + dayOfMonth);
        feedItem.setMedia(new FeedMedia(feedItem, "http://example.com/blah.mp3", 1, ""));

        LocalDate localDate = LocalDate.of(2026, 7, dayOfMonth);
        feedItem.setPubDate(Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));

        if (location == FeedItemLocation.INBOX) {
            feedItem.setNew();
        } else if (location == FeedItemLocation.UNPLAYED || location == FeedItemLocation.QUEUE_UNPLAYED) {
            feedItem.setPlayed(false);
        } else if (location == FeedItemLocation.PLAYED || location == FeedItemLocation.QUEUE_PLAYED) {
            feedItem.setPlayed(true);
        }
        if (location == FeedItemLocation.QUEUE_PLAYED || location == FeedItemLocation.QUEUE_UNPLAYED) {
            feedItem.addTag(FeedItem.TAG_QUEUE);
            queueItems.add(feedItem);
        }
        return feedItem;
    }
}