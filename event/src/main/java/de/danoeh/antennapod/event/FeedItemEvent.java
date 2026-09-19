package de.danoeh.antennapod.event;


import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.model.feed.FeedItem;

public class FeedItemEvent {
    @NonNull public final List<FeedItem> items;
    public final boolean unreadStatusChanged;

    /**
     * Refresh the entire feed
     */
    public FeedItemEvent() {
        this.items = Collections.emptyList();
        this.unreadStatusChanged = true;
    }

    /**
     * Refresh the specified items
     */
    public FeedItemEvent(@NonNull List<FeedItem> items) {
        this.items = items;
        this.unreadStatusChanged = false;
    }

    public static int indexOfItemWithId(List<FeedItem> items, long id) {
        for (int i = 0; i < items.size(); i++) {
            FeedItem item = items.get(i);
            if (item != null && item.getId() == id) {
                return i;
            }
        }
        return -1;
    }
}
