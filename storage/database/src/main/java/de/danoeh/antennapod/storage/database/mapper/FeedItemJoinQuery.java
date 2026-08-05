package de.danoeh.antennapod.storage.database.mapper;

import static de.danoeh.antennapod.model.feed.SortOrder.PRIORITY_PLAYBACK_DATE_NEW_OLD;
import static de.danoeh.antennapod.model.feed.SortOrder.PRIORITY_PLAYBACK_DATE_OLD_NEW;
import static de.danoeh.antennapod.storage.database.PodDBAdapter.KEY_FEED;
import static de.danoeh.antennapod.storage.database.PodDBAdapter.KEY_ID;
import static de.danoeh.antennapod.storage.database.PodDBAdapter.TABLE_NAME_FEEDS;
import static de.danoeh.antennapod.storage.database.PodDBAdapter.TABLE_NAME_FEED_ITEMS;

import de.danoeh.antennapod.model.feed.SortOrder;

public class FeedItemJoinQuery {
    public static String generateFrom(SortOrder sortOrder) {
        if (sortOrder == PRIORITY_PLAYBACK_DATE_NEW_OLD || sortOrder == PRIORITY_PLAYBACK_DATE_OLD_NEW) {
            return " JOIN " + TABLE_NAME_FEEDS + " ON " +
                    TABLE_NAME_FEEDS + "." + KEY_ID + " = " + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED;

        }
        return "";
    }
}
