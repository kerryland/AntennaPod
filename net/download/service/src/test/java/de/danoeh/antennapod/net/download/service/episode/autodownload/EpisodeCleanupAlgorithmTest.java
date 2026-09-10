package de.danoeh.antennapod.net.download.service.episode.autodownload;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

import static org.junit.Assert.assertEquals;

/**
 * Verifies that the cleanup algorithm accounts for active downloads when deciding
 * how many episodes to delete to make room for new auto-downloads.
 */
@RunWith(RobolectricTestRunner.class)
public class EpisodeCleanupAlgorithmTest extends DbCleanupTests {

    private int activeDownloads;

    @Before
    public void setUpActiveDownloads() {
        DownloadServiceInterface.setImpl(new DownloadServiceInterface() {
            @Override
            public void downloadNow(Context context, FeedItem item, boolean ignoreConstraints) {
            }

            @Override
            public void download(Context context, FeedItem item) {
            }

            @Override
            public int downloadAll(Context context, List<FeedItem> items) {
                return 0;
            }

            @Override
            public void cancel(Context context, de.danoeh.antennapod.model.feed.FeedMedia media) {
            }

            @Override
            public void cancelAll(Context context) {
            }

            @Override
            public int getNumberOfActiveDownloads(Context context) {
                return activeDownloads;
            }

            @Override
            public void notifyDownloadsComplete(Context context, Runnable onComplete) {
            }
        });
    }

    private void fillCache() throws IOException {
        Feed feed = new Feed("url", null, "title");
        List<FeedItem> items = new ArrayList<>();
        feed.setItems(items);
        List<File> files = new ArrayList<>();
        // Fill the episode cache completely (EPISODE_CACHE_SIZE = 5)
        populateItems(EPISODE_CACHE_SIZE, feed, items, files, FeedItem.UNPLAYED, false, false);
    }

    @Test
    public void testNumEpisodesToCleanupWithoutActiveDownloads() throws IOException {
        fillCache();
        EpisodeCleanupAlgorithm algorithm = EpisodeCleanupAlgorithmFactory.build();

        // 5 downloaded + 2 new = 7, over the cache size of 5, so 2 deletions are needed.
        activeDownloads = 0;
        assertEquals(2, algorithm.getNumEpisodesToCleanup(context, 2));
    }

    @Test
    public void testNumEpisodesToCleanupCountsActiveDownloads() throws IOException {
        fillCache();
        EpisodeCleanupAlgorithm algorithm = EpisodeCleanupAlgorithmFactory.build();

        // 5 on disk + 2 in flight + 2 new = 9, so 4 deletions are needed to bring the
        // eventual total (after the in-flight downloads complete) back to the cache size.
        activeDownloads = 2;
        assertEquals(4, algorithm.getNumEpisodesToCleanup(context, 2));
    }
}
