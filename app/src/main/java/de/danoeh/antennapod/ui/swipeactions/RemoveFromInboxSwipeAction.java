package de.danoeh.antennapod.ui.swipeactions;

import static de.danoeh.antennapod.ui.episodeslist.FeedItemMenuHandler.markReadWithUndo;

import android.content.Context;
import android.util.Log;

import androidx.fragment.app.Fragment;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;

public class RemoveFromInboxSwipeAction implements SwipeAction {

    private static final String TAG = "RemoveFromInboxSwipe";

    @Override
    public String getId() {
        return REMOVE_FROM_INBOX;
    }

    @Override
    public int getActionIcon() {
        return R.drawable.ic_check;
    }

    @Override
    public int getActionColor() {
        return R.attr.icon_purple;
    }

    @Override
    public String getTitle(Context context) {
        return context.getString(R.string.remove_inbox_label);
    }

    @Override
    public void performAction(FeedItem item, Fragment fragment, FeedItemFilter filter) {
        if (item.isNew()) {
            item.setRemoved(true);
            markReadWithUndo(fragment, item, FeedItem.UNPLAYED, true);
            Log.d(TAG, "Removed " + item.getTitle() + " from inbox");
        }
    }

    @Override
    public boolean willRemove(FeedItemFilter filter, FeedItem item) {
        return filter.showNew;
    }
}
