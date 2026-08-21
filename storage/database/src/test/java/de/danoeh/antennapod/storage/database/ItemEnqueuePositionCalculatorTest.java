package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedFilter;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.model.playback.RemoteMedia;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterfaceStub;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.preferences.UserPreferences.EnqueueLocation;
import de.danoeh.antennapod.model.playback.Playable;

import static de.danoeh.antennapod.model.feed.FeedPreferences.SPEED_USE_GLOBAL;
import static de.danoeh.antennapod.storage.preferences.UserPreferences.EnqueueLocation.AFTER_CURRENTLY_PLAYING;
import static de.danoeh.antennapod.storage.preferences.UserPreferences.EnqueueLocation.BACK;
import static de.danoeh.antennapod.storage.preferences.UserPreferences.EnqueueLocation.FRONT;
import static de.danoeh.antennapod.storage.database.CollectionTestUtil.concat;
import static de.danoeh.antennapod.storage.database.CollectionTestUtil.list;
import static de.danoeh.antennapod.storage.preferences.UserPreferences.EnqueueLocation.PRIORITY;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;

public class ItemEnqueuePositionCalculatorTest {

    @RunWith(Parameterized.class)
    public static class BasicTest {
        @Parameters(name = "{index}: case<{0}>, expected:{1}")
        public static Iterable<Object[]> data() {
            return Arrays.asList(new Object[][]{
                    {"case default, i.e., add to the end",
                            concat(QUEUE_DEFAULT_IDS, TFI_ID),
                            BACK, QUEUE_DEFAULT},
                    {"case option enqueue at front",
                            concat(TFI_ID, QUEUE_DEFAULT_IDS),
                            FRONT, QUEUE_DEFAULT},
                    {"case empty queue, option default",
                            list(TFI_ID),
                            BACK, QUEUE_EMPTY},
                    {"case empty queue, option enqueue at front",
                            list(TFI_ID),
                            FRONT, QUEUE_EMPTY},
            });
        }

        @Parameter
        public String message;

        @Parameter(1)
        public List<Long> idsExpected;

        @Parameter(2)
        public EnqueueLocation options;

        @Parameter(3)
        public List<FeedItem> curQueue;

        public static final long TF_ID = 77;
        public static final long TFI_ID = 101;

        /**
         * Add a FeedItem with ID {@link #TFI_ID} with the setup
         */
        @Test
        public void test() {
            DownloadServiceInterface.setImpl(new DownloadServiceInterfaceStub());
            ItemEnqueuePositionCalculator calculator = new ItemEnqueuePositionCalculator(options);

            // shallow copy to which the test will add items
            List<FeedItem> queue = new ArrayList<>(curQueue);
            FeedItem tFI = createFeedItem(TF_ID, TFI_ID, 5);
            customiseFeedItem(tFI);
            doAddToQueueAndAssertResult(message,
                    calculator, tFI, queue, getCurrentlyPlaying(),
                    idsExpected);
        }

        void customiseFeedItem(FeedItem tFI) {
        }

        Playable getCurrentlyPlaying() {
            return null;
        }
    }

    @RunWith(Parameterized.class)
    public static class AfterCurrentlyPlayingTest extends BasicTest {
        @Override
        void customiseFeedItem(FeedItem tFI) {
            tFI.getFeed().getPreferences().setPlaybackOrder(playbackOrder);
        }

        @Parameters(name = "{index}: case<{0}>, expected:{1}, playbackOrder:{5}")
        public static Iterable<Object[]> data() {
            return Arrays.asList(new Object[][]{
                    {"case option after currently playing",
                            list(11L, TFI_ID, 12L, 13L, 14L),
                            AFTER_CURRENTLY_PLAYING, QUEUE_DEFAULT, 11L, null},
                    {"case option after currently playing, currently playing in the middle of the queue",
                            list(11L, 12L, 13L, TFI_ID, 14L),
                            AFTER_CURRENTLY_PLAYING, QUEUE_DEFAULT, 13L, null},
                    {"case option after currently playing, currently playing is not in queue",
                            concat(TFI_ID, QUEUE_DEFAULT_IDS),
                            AFTER_CURRENTLY_PLAYING, QUEUE_DEFAULT, 99L, null},
                    {"case option priority",
                            list(15L, 12L, 14L,  TFI_ID, 11L), // expected
                            PRIORITY, QUEUE_PRIORITY_DIFFERENT_FEEDS, ID_CURRENTLY_PLAYING_NULL,
                            FeedPreferences.PlaybackOrderSetting.OLDEST_FIRST},
                    {"case option priority newest first",
                            list(15L, 12L, 14L, TFI_ID, 140L, 141L, 16L, 11L), // expected
                            PRIORITY, QUEUE_PRIORITY_EXISTING_FEED, ID_CURRENTLY_PLAYING_NULL,
                            FeedPreferences.PlaybackOrderSetting.NEWEST_FIRST },
                    {"case option priority oldest first",
                            list(15L, 12L, 14L, 140L, 141L, TFI_ID, 16L, 11L), // expected
                            PRIORITY, QUEUE_PRIORITY_EXISTING_FEED, ID_CURRENTLY_PLAYING_NULL,
                            FeedPreferences.PlaybackOrderSetting.OLDEST_FIRST },
                    {"case option priority newest first empty queue",
                            list(TFI_ID), // expected
                            PRIORITY, QUEUE_EMPTY, ID_CURRENTLY_PLAYING_NULL,
                            FeedPreferences.PlaybackOrderSetting.NEWEST_FIRST },
                    {"case option priority oldest first empty queue",
                            list(TFI_ID), // expected
                            PRIORITY, QUEUE_EMPTY, ID_CURRENTLY_PLAYING_NULL,
                            FeedPreferences.PlaybackOrderSetting.OLDEST_FIRST },
                    {"case option after currently playing, no currentlyPlaying is null",
                            concat(TFI_ID, QUEUE_DEFAULT_IDS),
                            AFTER_CURRENTLY_PLAYING, QUEUE_DEFAULT, ID_CURRENTLY_PLAYING_NULL, null},
                    {"case option after currently playing, currentlyPlaying is not a feedMedia",
                            concat(TFI_ID, QUEUE_DEFAULT_IDS),
                            AFTER_CURRENTLY_PLAYING, QUEUE_DEFAULT, ID_CURRENTLY_PLAYING_NOT_FEEDMEDIA, null},
                    {"case empty queue, option after currently playing",
                            list(TFI_ID),
                            AFTER_CURRENTLY_PLAYING, QUEUE_EMPTY, ID_CURRENTLY_PLAYING_NULL, null},
            });
        }

        @Parameter(4)
        public long idCurrentlyPlaying;

        @Parameter(5)
        public FeedPreferences.PlaybackOrderSetting playbackOrder;

        @Override
        Playable getCurrentlyPlaying() {
            return ItemEnqueuePositionCalculatorTest.getCurrentlyPlaying(idCurrentlyPlaying);
        }

        private static final long ID_CURRENTLY_PLAYING_NULL = -1L;
        private static final long ID_CURRENTLY_PLAYING_NOT_FEEDMEDIA = -9999L;

    }

    static void doAddToQueueAndAssertResult(String message,
                                            ItemEnqueuePositionCalculator calculator,
                                            FeedItem itemToAdd,
                                            List<FeedItem> queue,
                                            Playable currentlyPlaying,
                                            List<Long> idsExpected) {

        int posActual = calculator.calcPosition(queue, itemToAdd, currentlyPlaying);

        queue.add(posActual, itemToAdd);
        assertEquals(message, idsExpected.size(), queue.size());
        for (int i = 0; i < idsExpected.size(); i++) {
            assertEquals(message + " row " + i + " of " + idsExpected.size(), (long) idsExpected.get(i), queue.get(i).getId());
        }
    }

    static final List<FeedItem> QUEUE_EMPTY = Collections.unmodifiableList(emptyList());

    static final List<FeedItem> QUEUE_DEFAULT = 
            Collections.unmodifiableList(Arrays.asList(
                    createFeedItem(11), createFeedItem(12), createFeedItem(13), createFeedItem(14)));
    static final List<Long> QUEUE_DEFAULT_IDS =
            QUEUE_DEFAULT.stream().map(FeedItem::getId).collect(Collectors.toList());

    static final List<FeedItem> QUEUE_PRIORITY_DIFFERENT_FEEDS =
            Collections.unmodifiableList(Arrays.asList(
                    createFeedItem(100, 15, 1),
                    createFeedItem(200, 12, 2),
                    createFeedItem(300, 14, 4),
                    createFeedItem(400, 11, 6)));
    static final List<FeedItem> QUEUE_PRIORITY_EXISTING_FEED =
            Collections.unmodifiableList(Arrays.asList(
                    createFeedItem(100, 15, 1),
                    createFeedItem(200, 12, 2),
                    createFeedItem(300, 14, 4),
                    createFeedItem(BasicTest.TF_ID, 140, 5),
                    createFeedItem(BasicTest.TF_ID, 141, 5),
                    createFeedItem(300, 16, 4),
                    createFeedItem(400, 11, 6)));
    static Playable getCurrentlyPlaying(long idCurrentlyPlaying) {
        if (ID_CURRENTLY_PLAYING_NOT_FEEDMEDIA == idCurrentlyPlaying) {
            return externalMedia();
        }
        if (ID_CURRENTLY_PLAYING_NULL == idCurrentlyPlaying) {
            return null;
        }
        return createFeedItem(idCurrentlyPlaying).getMedia();
    }

    static Playable externalMedia() {
        return new RemoteMedia(createFeedItem(0));
    }

    static final long ID_CURRENTLY_PLAYING_NULL = -1L;
    static final long ID_CURRENTLY_PLAYING_NOT_FEEDMEDIA = -9999L;


    static FeedItem createFeedItem(long feedId, long feedItemId, int priority) {
        FeedItem feedItem = createFeedItem(feedItemId);
        feedItem.getFeed().setId(feedId);
        feedItem.setFeedId(feedId);

        feedItem.getFeed().getPreferences().setPriority(priority);
        return feedItem;
    }

    static FeedItem createFeedItem(long id) {
        Feed feed = new Feed(id, null, "title", "http://example.com", "This is the description",
                "http://example.com/payment", "Daniel", "en", null, "http://example.com/feed",
                "http://example.com/image", null, "http://example.com/feed", System.currentTimeMillis());
        FeedItem item = new FeedItem(id, "Item" + id, "ItemId" + id, "url",
                new Date(), FeedItem.PLAYED, feed);
        FeedMedia media = new FeedMedia(item, "http://download.url.net/" + id, 1234567, "audio/mpeg");
        media.setId(item.getId());
        item.setMedia(media);

        feed.setPreferences(new FeedPreferences(feed.getId(), FeedPreferences.AutoDownloadSetting.DISABLED,
                true, FeedPreferences.AutoDeleteAction.ALWAYS, VolumeAdaptionSetting.OFF, null, null,
                new FeedFilter(), SPEED_USE_GLOBAL, 0, 0, FeedPreferences.SkipSilence.GLOBAL,
                false, FeedPreferences.NewEpisodesAction.GLOBAL, 5,
                FeedPreferences.PlaybackOrderSetting.NEWEST_FIRST, 3, new HashSet<>()));

        return item;
    }

}
