package de.danoeh.antennapod.storage.database;

import android.content.Context;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadResult;
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
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static de.danoeh.antennapod.model.feed.FeedPreferences.SPEED_USE_GLOBAL;

@RunWith(RobolectricTestRunner.class)
public class FeedDatabaseWriterTest {
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

    @Test
    public void testStoreNewFeed() {
        Feed feed = createFeed();
        for (int i = 0; i < 3; i++) {
            feed.getItems().add(createItem("item-" + i, "Item " + i, feed));
        }
        Feed updatedFeed = FeedDatabaseWriter.updateFeed(context, feed, false);
        List<FeedItem> storedItems = DBReader.getFeedItemList(updatedFeed, FeedItemFilter.unfiltered(),
                SortOrder.EPISODE_TITLE_A_Z, 0, Integer.MAX_VALUE);
        assertEquals(3, storedItems.size());
        for (int i = 0; i < 3; i++) {
            assertEquals("item-" + i, storedItems.get(i).getItemIdentifier());
        }
    }

    @Test
    public void testAddItemsToExistingFeed() {
        Feed feed = createFeed();
        for (int i = 0; i < 3; i++) {
            feed.getItems().add(createItem("item-" + i, "Item " + i, feed));
        }
        feed = FeedDatabaseWriter.updateFeed(context, feed, false);

        Feed updatedFeed = createFeed();
        updatedFeed.setId(feed.getId());
        for (int i = 3; i < 6; i++) {
            updatedFeed.getItems().add(createItem("item-" + i, "Item " + i, feed));
        }
        FeedDatabaseWriter.updateFeed(context, updatedFeed, false);

        List<FeedItem> dbItems = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                SortOrder.EPISODE_TITLE_A_Z, 0, Integer.MAX_VALUE);
        assertEquals(6, dbItems.size());
        for (int i = 0; i < 6; i++) {
            assertEquals("item-" + i, dbItems.get(i).getItemIdentifier());
        }
    }

    @Test
    public void testAddOrUpdateItems() throws ExecutionException, InterruptedException {
        Feed feed = createFeed();
        for (int i = 0; i < 3; i++) {
            feed.getItems().add(createItem("item-" + i, "Item " + i, feed));
        }
        feed = FeedDatabaseWriter.updateFeed(context, feed, false);
        DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                SortOrder.EPISODE_TITLE_A_Z, 0, Integer.MAX_VALUE);
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, Collections.singletonList(feed.getItems().get(2))).get();

        Feed updatedFeed = createFeed();
        updatedFeed.setId(feed.getId());
        for (int i = 2; i < 5; i++) {
            updatedFeed.getItems().add(createItem("item-" + i, "Item " + i, feed));
        }
        FeedDatabaseWriter.updateFeed(context, updatedFeed, false);

        List<FeedItem> dbItems = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                SortOrder.EPISODE_TITLE_A_Z, 0, Integer.MAX_VALUE);
        assertEquals(5, dbItems.size());
        for (int i = 0; i < 5; i++) {
            assertEquals("item-" + i, dbItems.get(i).getItemIdentifier());
        }
        assertEquals(FeedItem.PLAYED, dbItems.get(2).getPlayState());
    }

    @Test
    public void testDuplicateItemsInFeed() {
        Feed feed = createFeed();
        feed.getItems().add(createItem("id1", "Duplicate Title", feed));
        feed.getItems().add(createItem("id2", "Duplicate Title", feed));
        FeedDatabaseWriter.updateFeed(context, feed, false); // First update just takes the feed without complaining
        FeedDatabaseWriter.updateFeed(context, feed, false);

        List<DownloadResult> downloadLog = DBReader.getDownloadLog();
        assertEquals(1, downloadLog.size());
        assertEquals(DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE, downloadLog.get(0).getReason());
    }

    @Test
    public void testGuidUpdated() {
        Feed feed = createFeed();
        feed.getItems().add(createItem("old-id", "Unique Title", feed));
        FeedDatabaseWriter.updateFeed(context, feed, false);

        Feed newFeed = createFeed();
        newFeed.getItems().add(createItem("new-id", "Unique Title", newFeed));
        Feed stored = FeedDatabaseWriter.updateFeed(context, newFeed, false);

        assertEquals(1, stored.getItems().size());
        assertEquals("new-id", stored.getItems().get(0).getItemIdentifier());
    }

    @Test
    public void testUpdateFeedNewFeed() {
        final int numItems = 10;

        Feed feed = createFeed();
        for (int i = 0; i < numItems; i++) {
            feed.getItems().add(new FeedItem(0, "item " + i, "id " + i, "link " + i,
                    new Date(), FeedItem.UNPLAYED, feed));
        }
        Feed newFeed = FeedDatabaseWriter.updateFeed(context, feed, false);

        assertEquals(feed.getId(), newFeed.getId());
        assertTrue(feed.getId() != 0);
        for (FeedItem item : feed.getItems()) {
            assertFalse(item.isPlayed());
            assertTrue(item.getId() != 0);
        }
    }

    /** Two feeds with the same title, but different download URLs should be treated as different feeds. */
    @Test
    public void testUpdateFeedSameTitle() {
        Feed feed1 = createFeed();
        Feed feed2 = createFeed();
        feed2.setDownloadUrl("different url");

        Feed savedFeed1 = FeedDatabaseWriter.updateFeed(context, feed1, false);
        Feed savedFeed2 = FeedDatabaseWriter.updateFeed(context, feed2, false);

        assertTrue(savedFeed1.getId() != savedFeed2.getId());
    }

    @Test
    public void testUpdateFeedUpdatedFeed() {
        final int numItemsOld = 10;
        final int numItemsNew = 10;

        final Feed feed = createFeed();
        for (int i = 0; i < numItemsOld; i++) {
            feed.getItems().add(new FeedItem(0, "item " + i, "id " + i, "link " + i,
                    new Date(i), FeedItem.PLAYED, feed));
        }
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();

        // ensure that objects have been saved in db, then reset
        assertTrue(feed.getId() != 0);
        final long feedID = feed.getId();
        feed.setId(0);
        List<Long> itemIDs = new ArrayList<>();
        for (FeedItem item : feed.getItems()) {
            assertTrue(item.getId() != 0);
            itemIDs.add(item.getId());
            item.setId(0);
        }

        for (int i = numItemsOld; i < numItemsNew + numItemsOld; i++) {
            feed.getItems().add(0, new FeedItem(0, "item " + i, "id " + i, "link " + i,
                    new Date(i), FeedItem.UNPLAYED, feed));
        }

        final Feed newFeed = FeedDatabaseWriter.updateFeed(context, feed, false);
        assertNotSame(newFeed, feed);

        updatedFeedTest(newFeed, feedID, itemIDs, numItemsOld, numItemsNew);

        final Feed feedFromDB = DBReader.getFeed(newFeed.getId(), false, 0, Integer.MAX_VALUE);
        assertNotNull(feedFromDB);
        assertEquals(newFeed.getId(), feedFromDB.getId());
        updatedFeedTest(feedFromDB, feedID, itemIDs, numItemsOld, numItemsNew);
    }

    enum FeedItemLocation {
        QUEUE_PLAYED,
        QUEUE_UNPLAYED,
        INBOX,
        PLAYED,
        UNPLAYED
    }

    @Test
    public void testQueuePopulated_Newest_First_Max_3_Episodes_Into_Inbox() {
        UserPreferences.setEnqueueLocation(UserPreferences.EnqueueLocation.PRIORITY);

        List<FeedItem> feedItems = new ArrayList<>();
        List<FeedItem> queueItems = new ArrayList<>();

        feedItems.add(makeTestFeedItem(17, FeedItemLocation.QUEUE_UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(18, FeedItemLocation.UNPLAYED, queueItems));
        feedItems.add(makeTestFeedItem(19, FeedItemLocation.PLAYED, queueItems));
        feedItems.add(makeTestFeedItem(20, FeedItemLocation.INBOX, queueItems));

        Feed feed = prepareTestData(FeedPreferences.NewEpisodesAction.ADD_TO_INBOX,
                FeedPreferences.PlaybackOrderSetting.NEWEST_FIRST,
                3, feedItems, queueItems);

        //----------------------------------------------------------------------
        FeedDatabaseWriter.updateFeed(context, feed, false);
        DBWriter.waitForDatabase();
        //----------------------------------------------------------------------

        // Check inbox
        List<FeedItem> inbox = DBReader.getFeedItemList(feed, new FeedItemFilter(FeedItemFilter.NEW), SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE);
        assertEquals(2, inbox.size());
        assertEquals("EPISODE 20", inbox.get(0).getTitle());
        assertEquals("EPISODE 18", inbox.get(1).getTitle());

        // Check queue
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

        //----------------------------------------------------------------------
        FeedDatabaseWriter.updateFeed(context, feed, false);
        DBWriter.waitForDatabase();
        //----------------------------------------------------------------------

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

        //----------------------------------------------------------------------
        FeedDatabaseWriter.updateFeed(context, feed, false);
        DBWriter.waitForDatabase();
        //----------------------------------------------------------------------

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

        //----------------------------------------------------------------------
        FeedDatabaseWriter.updateFeed(context, feed, false);
        DBWriter.waitForDatabase();
        //----------------------------------------------------------------------

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
        adapter.close();

        try {
            Future<?> future = DBWriter.addQueueItem(context, queueItems.toArray(new FeedItem[0]));
            future.get(); // Wait for data to write on the database thread
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        }
        feedItem.setPlayed(location == FeedItemLocation.PLAYED || location == FeedItemLocation.QUEUE_PLAYED);
        if (location == FeedItemLocation.QUEUE_PLAYED || location == FeedItemLocation.QUEUE_UNPLAYED) {
            queueItems.add(feedItem);
        }
        return feedItem;
    }


    @Test
    public void testUpdateFeedMediaUrlResetState() {
        final Feed feed = createFeed();
        FeedItem item = new FeedItem(0, "item", "id", "link", new Date(), FeedItem.PLAYED, feed);
        feed.setItems(Collections.singletonList(item));

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();

        // ensure that objects have been saved in db, then reset
        assertTrue(feed.getId() != 0);
        assertTrue(item.getId() != 0);

        FeedMedia media = new FeedMedia(item, "url", 1024, "mime/type");
        item.setMedia(media);
        List<FeedItem> list = new ArrayList<>();
        list.add(item);
        feed.setItems(list);

        final Feed newFeed = FeedDatabaseWriter.updateFeed(context, feed, false);
        assertNotSame(newFeed, feed);

        final Feed feedFromDB = DBReader.getFeed(newFeed.getId(), false, 0, Integer.MAX_VALUE);
        final FeedItem feedItemFromDB = feedFromDB.getItems().get(0);
        assertTrue(feedItemFromDB.isNew());
    }

    @Test
    public void testUpdateFeedRemoveUnlistedItems() {
        final Feed feed = createFeed();
        for (int i = 0; i < 10; i++) {
            feed.getItems().add(
                    new FeedItem(0, "item " + i, "id " + i, "link " + i, new Date(i), FeedItem.PLAYED, feed));
        }
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();

        // delete some items
        feed.getItems().subList(0, 2).clear();
        Feed newFeed = FeedDatabaseWriter.updateFeed(context, feed, true);
        assertEquals(8, newFeed.getItems().size()); // 10 - 2 = 8 items

        Feed feedFromDB = DBReader.getFeed(newFeed.getId(), false, 0, Integer.MAX_VALUE);
        assertEquals(8, feedFromDB.getItems().size()); // 10 - 2 = 8 items
    }

    @Test
    public void testUpdateFeedSetDuplicate() {
        final Feed feed = createFeed();
        for (int i = 0; i < 10; i++) {
            FeedItem item =
                    new FeedItem(0, "item " + i, "id " + i, "link " + i, new Date(i), FeedItem.PLAYED, feed);
            FeedMedia media = new FeedMedia(item, "download url " + i, 123, "media/mp3");
            item.setMedia(media);
            feed.getItems().add(item);
        }
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();

        // change the guid of the first item, but leave the download url the same
        FeedItem item = feed.getItemAtIndex(0);
        item.setItemIdentifier("id 0-duplicate");
        item.setTitle("item 0 duplicate");
        Feed newFeed = FeedDatabaseWriter.updateFeed(context, feed, false);
        assertEquals(10, newFeed.getItems().size()); // id 1-duplicate replaces because the stream url is the same

        Feed feedFromDB = DBReader.getFeed(newFeed.getId(), false, 0, Integer.MAX_VALUE);
        assertEquals(10, feedFromDB.getItems().size()); // id1-duplicate should override id 1

        FeedItem updatedItem = feedFromDB.getItemAtIndex(9);
        assertEquals("item 0 duplicate", updatedItem.getTitle());
        assertEquals("id 0-duplicate", updatedItem.getItemIdentifier()); // Should use the new ID for sync etc
    }


    @SuppressWarnings("SameParameterValue")
    private void updatedFeedTest(final Feed newFeed, long feedID, List<Long> itemIDs,
                                 int numItemsOld, int numItemsNew) {
        assertEquals(feedID, newFeed.getId());
        assertEquals(numItemsNew + numItemsOld, newFeed.getItems().size());
        Collections.reverse(newFeed.getItems());
        Date lastDate = new Date(0);
        for (int i = 0; i < numItemsOld; i++) {
            FeedItem item = newFeed.getItems().get(i);
            assertSame(newFeed, item.getFeed());
            assertEquals((long) itemIDs.get(i), item.getId());
            assertTrue(item.isPlayed());
            assertTrue(item.getPubDate().getTime() >= lastDate.getTime());
            lastDate = item.getPubDate();
        }
        for (int i = numItemsOld; i < numItemsNew + numItemsOld; i++) {
            FeedItem item = newFeed.getItems().get(i);
            assertSame(newFeed, item.getFeed());
            assertTrue(item.getId() != 0);
            assertFalse(item.isPlayed());
            assertTrue(item.getPubDate().getTime() >= lastDate.getTime());
            lastDate = item.getPubDate();
        }
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

    private FeedItem createItem(String identifier, String title, Feed feed) {
        FeedItem item = new FeedItem();
        item.setItemIdentifier(identifier);
        item.setTitle(title);
        item.setMedia(new FeedMedia(item, "url-" + title, 2, "mime"));
        item.setFeed(feed);
        return item;
    }
}
