package de.danoeh.antennapod.storage.database;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Date;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class AutoDeletePreferenceTest {
    private static final String PREF_AUTO_DELETE_LOCAL = "prefAutoDeleteLocal";
    private static final String LOCAL_FEED_URL = "antennapod_local:folder";

    private SharedPreferences prefs;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().clear().commit();
    }

    @Test
    public void globalAutoDeleteDisabled_globalAction_noDeletion() {
        setAutoDelete(false);
        assertFalse(DBWriter.shouldAutoDeleteEpisode(item(FeedPreferences.AutoDeleteAction.GLOBAL, false)));
    }

    @Test
    public void globalAutoDeleteEnabled_globalAction_deletion() {
        setAutoDelete(true);
        assertTrue(DBWriter.shouldAutoDeleteEpisode(item(FeedPreferences.AutoDeleteAction.GLOBAL, false)));
    }

    @Test
    public void feedOverrideNever_blocksDeletion() {
        setAutoDelete(true);
        assertFalse(DBWriter.shouldAutoDeleteEpisode(item(FeedPreferences.AutoDeleteAction.NEVER, false)));
    }

    @Test
    public void feedOverrideAlways_deletesEvenIfGlobalDisabled() {
        setAutoDelete(false);
        assertTrue(DBWriter.shouldAutoDeleteEpisode(item(FeedPreferences.AutoDeleteAction.ALWAYS, false)));
    }

    @Test
    public void favoriteKeepsEpisode() {
        setAutoDelete(true);
        FeedItem favorite = item(FeedPreferences.AutoDeleteAction.GLOBAL, false);
        favorite.addTag(FeedItem.TAG_FAVORITE);
        assertFalse(DBWriter.shouldAutoDeleteEpisode(favorite));

        prefs.edit().putBoolean(UserPreferences.PREF_FAVORITE_KEEPS_EPISODE, false).commit();
        assertTrue(DBWriter.shouldAutoDeleteEpisode(favorite));
    }

    @Test
    public void localFeed_requiresLocalAutoDelete() {
        setAutoDelete(true);
        assertFalse(DBWriter.shouldAutoDeleteEpisode(item(FeedPreferences.AutoDeleteAction.GLOBAL, true)));

        prefs.edit().putBoolean(PREF_AUTO_DELETE_LOCAL, true).commit();
        assertTrue(DBWriter.shouldAutoDeleteEpisode(item(FeedPreferences.AutoDeleteAction.GLOBAL, true)));
    }

    private void setAutoDelete(boolean enabled) {
        prefs.edit().putBoolean(UserPreferences.PREF_AUTO_DELETE, enabled).commit();
    }

    private FeedItem item(FeedPreferences.AutoDeleteAction action, boolean localFeed) {
        Feed feed = new Feed(localFeed ? LOCAL_FEED_URL : "https://example.com/feed.xml", null, "title");
        feed.setPreferences(new FeedPreferences(feed.getId(), FeedPreferences.AutoDownloadSetting.GLOBAL,
                action, VolumeAdaptionSetting.OFF, FeedPreferences.NewEpisodesAction.GLOBAL, null, null));
        return new FeedItem(0, "Episode", "episode-id", "url", new Date(), FeedItem.UNPLAYED, feed);
    }
}
