package de.danoeh.antennapod.ui.screen;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.episodeslist.EpisodesListFragment;

public class FavoritesFragment extends EpisodesListFragment {
    public static final String TAG = "FavoritesFragment";
    private static final FeedItemFilter FILTER_FAVORITES = new FeedItemFilter(
            FeedItemFilter.IS_FAVORITE, FeedItemFilter.INCLUDE_ALL_FEED_STATES);
    private static Pair<Integer, Integer> scrollPosition = null;

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View root = super.onCreateView(inflater, container, savedInstanceState);
        toolbar.inflateMenu(R.menu.favorites);
        toolbar.setTitle(R.string.favorite_episodes_label);
        updateToolbar();
        emptyView.setIcon(R.drawable.ic_star);
        emptyView.setTitle(R.string.no_fav_episodes_head_label);
        emptyView.setMessage(R.string.no_fav_episodes_label);
        return root;
    }

    @Override
    protected FeedItemFilter getFilter() {
        return FILTER_FAVORITES;
    }

    @Override
    protected SortOrder getSortOrder() {
        return UserPreferences.getAllEpisodesSortOrder();
    }

    @Override
    protected String getFragmentTag() {
        return TAG;
    }

    @Override
    public void onPause() {
        super.onPause();
        scrollPosition = recyclerView.getScrollPosition();
    }

    @Override
    protected void onItemsFirstLoaded() {
        recyclerView.restoreScrollPosition(scrollPosition);
    }
}
