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

        FeedDatabaseWriter.updateFeed(context, feed, false);

        //----------------------------------------------------------------------
        DestinationSelector.populateInboxOrQueue(context, Collections.singletonList(feed));
        //----------------------------------------------------------------------

        DBWriter.waitForDatabase(); // Make sure the database is updated

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
        } else if (location == FeedItemLocation.UNPLAYED || location == FeedItemLocation.QUEUE_UNPLAYED) {
            feedItem.setPlayed(false);
        } else if (location == FeedItemLocation.PLAYED || location == FeedItemLocation.QUEUE_PLAYED) {
            feedItem.setPlayed(true);
        }
        if (location == FeedItemLocation.QUEUE_PLAYED || location == FeedItemLocation.QUEUE_UNPLAYED) {
            queueItems.add(feedItem);
        }
        return feedItem;
    }
}