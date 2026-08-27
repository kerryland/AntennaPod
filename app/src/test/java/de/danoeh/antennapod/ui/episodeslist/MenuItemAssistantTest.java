package de.danoeh.antennapod.ui.episodeslist;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.PendingIntent;
import android.content.Context;
import android.os.Looper;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowLooper;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter;

@RunWith(RobolectricTestRunner.class)
public class MenuItemAssistantTest {
    private Context context;
    private List<FeedItem> feedItems;
    private MediaController controller;
    private PendingIntent pendingIntent;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        controller = mock(MediaController.class);
        pendingIntent = mock(PendingIntent.class);

        Feed feed = new Feed(1, null, "Test Feed", "", "", "", "", "", "", "", "", "", "", "", 0,
                false, null, "", null, false, 0);

        FeedItem itemWithMedia = new FeedItem(1, "Item 1", "item-1", null, null, 0, feed);
        FeedMedia media = new FeedMedia(42, itemWithMedia, 0, 0, 0, "audio/mpeg", null,
                "http://example.com/1.mp3", 0, null, 0, 0);
        itemWithMedia.setMedia(media);

        feedItems = Arrays.asList(itemWithMedia,
                new FeedItem(2, "Item 2", "item-2", null, null, 0, feed));
    }

    private static MediaItem mediaItem(long id) {
        return new MediaItem.Builder().setMediaId(String.valueOf(id)).build();
    }

    private void mockSkipToNextPendingIntent(MockedStatic<MediaButtonStarter> mbsMock) {
        mbsMock.when(() -> MediaButtonStarter.createPendingIntent(any(), anyInt()))
                .thenReturn(pendingIntent);
    }

    @Test
    public void skipIfPlaying_nothingPlaying_runsCallbackImmediately() {
        try (MockedStatic<MediaButtonStarter> mbsMock = Mockito.mockStatic(MediaButtonStarter.class)) {
            mockSkipToNextPendingIntent(mbsMock);
            Runnable callback = mock(Runnable.class);

            when(controller.getCurrentMediaItem()).thenReturn(null);

            MenuItemAssistant.skipIfPlaying(controller, context, feedItems, callback);

            verify(callback, times(1)).run();
            verify(controller).release();
            mbsMock.verify(() -> MediaButtonStarter.createPendingIntent(any(), anyInt()), never());
        }
    }

    @Test
    public void skipIfPlaying_playingItemNotInList_runsCallbackImmediately() {
        try (MockedStatic<MediaButtonStarter> mbsMock = Mockito.mockStatic(MediaButtonStarter.class)) {
            mockSkipToNextPendingIntent(mbsMock);
            Runnable callback = mock(Runnable.class);

            when(controller.getCurrentMediaItem()).thenReturn(mediaItem(99));

            MenuItemAssistant.skipIfPlaying(controller, context, feedItems, callback);

            verify(callback, times(1)).run();
            verify(controller).release();
            mbsMock.verify(() -> MediaButtonStarter.createPendingIntent(any(), anyInt()), never());
        }
    }

    @Test
    public void skipIfPlaying_playingItemInList_skipsAndRunsCallbackAfterTransition() throws Exception {
        try (MockedStatic<MediaButtonStarter> mbsMock = Mockito.mockStatic(MediaButtonStarter.class)) {
            mockSkipToNextPendingIntent(mbsMock);
            Runnable callback = mock(Runnable.class);

            when(controller.getCurrentMediaItem()).thenReturn(mediaItem(42));

            MenuItemAssistant.skipIfPlaying(controller, context, feedItems, callback);

            mbsMock.verify(() -> MediaButtonStarter.createPendingIntent(
                    context, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM));
            verify(pendingIntent).send();
            verify(callback, never()).run();

            // Playback transitions to an item that is not part of the list -> polling finishes.
            when(controller.getCurrentMediaItem()).thenReturn(mediaItem(99));
            ShadowLooper shadowLooper = shadowOf(Looper.getMainLooper());
            shadowLooper.idleFor(100, TimeUnit.MILLISECONDS);

            verify(callback, times(1)).run();
            verify(controller).release();
        }
    }

    @Test
    public void skipIfPlaying_stillOnQueuedItem_keepsPolling() {
        try (MockedStatic<MediaButtonStarter> mbsMock = Mockito.mockStatic(MediaButtonStarter.class)) {
            mockSkipToNextPendingIntent(mbsMock);
            Runnable callback = mock(Runnable.class);

            when(controller.getCurrentMediaItem()).thenReturn(mediaItem(42));

            MenuItemAssistant.skipIfPlaying(controller, context, feedItems, callback);

            // Playback stays on the queued (in-list) item after one poll -> no callback yet.
            ShadowLooper shadowLooper = shadowOf(Looper.getMainLooper());
            shadowLooper.idleFor(100, TimeUnit.MILLISECONDS);

            verify(callback, never()).run();
            assertNotNull(shadowLooper);
        }
    }
}
