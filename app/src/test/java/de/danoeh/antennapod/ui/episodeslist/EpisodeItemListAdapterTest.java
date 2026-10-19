package de.danoeh.antennapod.ui.episodeslist;

import static org.junit.Assert.assertEquals;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.fragment.app.FragmentActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterfaceStub;

@RunWith(RobolectricTestRunner.class)
public class EpisodeItemListAdapterTest {
    private FragmentActivity activity;
    private ViewGroup parent;
    private EpisodeItemListAdapter adapter;
    private List<FeedItem> testData;
    private Feed f;


    @Before
    public void setUp() throws Exception {
        activity = Robolectric.buildActivity(FragmentActivity.class)
                .create()
                .resume()
                .get();
        activity.setTheme(R.style.Theme_AntennaPod_Dark);

        adapter = new EpisodeItemListAdapter(activity);
        parent = new LinearLayout(activity);

        // Prepare test data
        f = new Feed(1, null, "Test Feed", "", "","","","","","","","","","",0,false, null,"",null,false, 0);
        FeedItem f1 = new FeedItem(1, "Episode Zero", "", "", null, 0, f);
        FeedItem f2 = new FeedItem(2, "Episode One", "", "", null, 0, f);
        FeedItem f3 = new FeedItem(3, "Episode Two", "", "", null, 0, f);
        FeedItem f4 = new FeedItem(4, "Episode Three", "", "", null, 0, f);
        testData = Arrays.asList(f1, f2, f3, f4);
        DownloadServiceInterface.setImpl(new DownloadServiceInterfaceStub());
        for (FeedItem item : testData) {
            item.setMedia(new FeedMedia(item, "http://example.com/episode.mp3", 1000, "audio/mpeg"));
        }
    }

    @Test
    public void onBindViewHolder_withValidPositions_shouldBindAllItems() {
        adapter.updateItems(testData);
        assertEquals(4, adapter.getItemCount());

        for (int i = 0; i < testData.size(); i++) {
            EpisodeItemViewHolder holder = adapter.onCreateViewHolder(parent, 0);
            adapter.onBindViewHolder(holder, i);

            assertEquals("Position " + i + " should match",
                    testData.get(i).getTitle(), holder.getFeedItem().getTitle());
        }
    }

    @Test
    public void testGetSelectedItems() {
        adapter.updateItems(testData);
        assertEquals(4, adapter.getItemCount());
        adapter.setSelected(1, true);

        List<FeedItem> selectedItems = adapter.getSelectedItems();
        assertEquals(1, selectedItems.size());
        assertEquals("Episode One", selectedItems.get(0).getTitle());
        assertEquals(1, adapter.getSelectedCount());
    }

    @Test
    public void testGetSelectedItemsInOrder() {
        adapter.updateItems(testData);
        assertEquals(4, adapter.getItemCount());
        adapter.setSelected(1, true);
        adapter.setSelected(0, true);
        adapter.setSelected(2, true);

        List<Integer> selectedPositions = adapter.getSelectedItemsInOrder();
        assertEquals(3, selectedPositions.size());

        assertEquals("Episode One", testData.get(selectedPositions.get(0)).getTitle());
        assertEquals("Episode Zero", testData.get(selectedPositions.get(1)).getTitle());
        assertEquals("Episode Two", testData.get(selectedPositions.get(2)).getTitle());
    }

    @Test
    public void testGetSelectedFeedItemsInOrder() {
        adapter.updateItems(testData);
        assertEquals(4, adapter.getItemCount());
        adapter.setSelected(3, true);
        adapter.setSelected(2, true);
        adapter.setSelected(1, true);
        adapter.setSelected(0, true);
        adapter.setSelected(1, false);
        adapter.setSelected(2, false);
        adapter.setSelected(1, true);

        List<FeedItem> selectedPositions = adapter.getSelectedFeedItemsInOrder();
        assertEquals(3, selectedPositions.size());

        assertEquals("Episode Three", selectedPositions.get(0).getTitle());
        assertEquals("Episode Zero", selectedPositions.get(1).getTitle());
        assertEquals("Episode One", selectedPositions.get(2).getTitle());
    }

    @Test
    public void testSelectAllAfterSelectSome() {
        adapter.updateItems(testData);

        // Select 3 items
        adapter.setSelected(3, true);
        adapter.setSelected(2, true);
        adapter.setSelected(1, true);
        assertEquals(4, adapter.getItemCount());

        // Select all 4 items
        adapter.setSelected(0, testData.size(),true);

        // How many are selected?
        assertEquals(4, adapter.getSelectedCount());
        assertEquals(4, adapter.getSelectedItems().size());
        assertEquals(4, adapter.getSelectedFeedItemsInOrder().size());
    }

    @Test
    public void onBindViewHolder_newestFirstFeed_indicatorEnabled_showsClockIcon() {
        setPlaybackOrder(FeedPreferences.PlaybackOrderSetting.NEWEST_FIRST);
        adapter.setShowTemporaryQueueIndicator(true);
        adapter.updateItems(testData);

        ImageView isPermanent = getIsPermanentView(0);
        assertEquals(View.VISIBLE, isPermanent.getVisibility());
        assertEquals("May be removed automatically", isPermanent.getContentDescription());
    }

    @Test
    public void onBindViewHolder_oldestFirstFeed_indicatorEnabled_showsNoIcon() {
        setPlaybackOrder(FeedPreferences.PlaybackOrderSetting.OLDEST_FIRST);
        adapter.setShowTemporaryQueueIndicator(true);
        adapter.updateItems(testData);

        assertEquals(View.GONE, getIsPermanentView(0).getVisibility());
    }

    @Test
    public void onBindViewHolder_pinnedItem_showsPinInsteadOfClockIcon() {
        setPlaybackOrder(FeedPreferences.PlaybackOrderSetting.NEWEST_FIRST);
        adapter.setShowTemporaryQueueIndicator(true);
        testData.get(0).addTag(FeedItem.TAG_QUEUE_PERMANENT);
        adapter.updateItems(testData);

        ImageView isPermanent = getIsPermanentView(0);
        assertEquals(View.VISIBLE, isPermanent.getVisibility());
        assertEquals("Permanent", isPermanent.getContentDescription());
    }

    @Test
    public void onBindViewHolder_newestFirstFeed_indicatorDisabled_showsNoIcon() {
        setPlaybackOrder(FeedPreferences.PlaybackOrderSetting.NEWEST_FIRST);
        adapter.updateItems(testData);

        assertEquals(View.GONE, getIsPermanentView(0).getVisibility());
    }

    @Test
    public void onBindViewHolder_noFeedPreferences_showsNoIcon() {
        adapter.setShowTemporaryQueueIndicator(true);
        adapter.updateItems(testData);

        assertEquals(View.GONE, getIsPermanentView(0).getVisibility());
    }

    @Test
    public void onBindViewHolder_newItem_showsInInboxIcon() {
        testData.get(0).setNew();
        adapter.updateItems(testData);

        assertEquals(View.VISIBLE, getInboxView(0).getVisibility());
    }

    @Test
    public void onBindViewHolder_inboxList_hidesInInboxIcon() {
        testData.get(0).setNew();
        adapter.setShowInInboxIndicator(false);
        adapter.updateItems(testData);

        assertEquals(View.GONE, getInboxView(0).getVisibility());
    }

    private void setPlaybackOrder(FeedPreferences.PlaybackOrderSetting playbackOrder) {
        FeedPreferences preferences = new FeedPreferences(f.getId(), FeedPreferences.AutoDownloadSetting.GLOBAL,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.GLOBAL, "", "");
        preferences.setPlaybackOrder(playbackOrder);
        f.setPreferences(preferences);
    }

    private ImageView getIsPermanentView(int position) {
        EpisodeItemViewHolder holder = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(holder, position);
        return holder.itemView.findViewById(R.id.isPermanent);
    }

    private ImageView getInboxView(int position) {
        EpisodeItemViewHolder holder = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(holder, position);
        return holder.itemView.findViewById(R.id.statusInbox);
    }
}