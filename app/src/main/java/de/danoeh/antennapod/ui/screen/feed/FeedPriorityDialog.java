package de.danoeh.antennapod.ui.screen.feed;

import android.content.Context;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.storage.database.DBWriter;

public class FeedPriorityDialog {

    private static int MIN_PRIORITY = 1;
    private static int MAX_PRIORITY = 9;

    private FeedPriorityDialog() {
    }

    public static void show(Context context, Feed feed) {
        show(context, feed, null);
    }

    public static void show(Context context, Feed feed, @Nullable Runnable onPriorityChanged) {
        String[] priorities = context.getResources().getStringArray(R.array.feed_priority_options);
        int currentPriority = feed.getPreferences().getPriority();
        int selected = Math.max(MIN_PRIORITY, Math.min(MAX_PRIORITY, currentPriority)) - 1;
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.edit_priority)
                .setSingleChoiceItems(priorities, selected, (dialog, which) -> {
                    feed.getPreferences().setPriority(which + 1);
                    DBWriter.setFeedPreferences(feed.getPreferences());
                    if (onPriorityChanged != null) {
                        onPriorityChanged.run();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .show();
    }
}
