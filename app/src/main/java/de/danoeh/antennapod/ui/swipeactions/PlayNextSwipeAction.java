package de.danoeh.antennapod.ui.swipeactions;

import android.content.Context;

import androidx.fragment.app.Fragment;

import java.util.Collections;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.ui.episodeslist.EpisodeMultiSelectActionHandler;
import de.danoeh.antennapod.usecase.QueueUseCase;

public class PlayNextSwipeAction implements SwipeAction {

    @Override
    public String getId() {
        return PLAY_NEXT;
    }

    @Override
    public int getActionIcon() {
        return R.drawable.media3_icon_queue_next;
    }

    @Override
    public int getActionColor() {
        return R.attr.icon_purple;
    }

    @Override
    public String getTitle(Context context) {
        return context.getString(R.string.play_next);
    }

    @Override
    public void performAction(FeedItem item, Fragment fragment, FeedItemFilter filter) {
        if (item.isTagged(FeedItem.TAG_QUEUE)) {
            QueueUseCase.getInstance().moveToPlayNext(fragment.getActivity(), Collections.singletonList(item), null);
        } else {
            new EpisodeMultiSelectActionHandler(fragment.getActivity(), R.id.add_to_queue_play_next_item)
                    .handleAction(Collections.singletonList(item));
        }
    }

    @Override
    public boolean willRemove(FeedItemFilter filter, FeedItem item) {
        return false;
    }
}
