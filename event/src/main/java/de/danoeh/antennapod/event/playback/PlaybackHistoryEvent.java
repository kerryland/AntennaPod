package de.danoeh.antennapod.event.playback;

import androidx.annotation.Nullable;

public class PlaybackHistoryEvent {
    @Nullable
    private final Long feedId;

    private PlaybackHistoryEvent(@Nullable Long feedId) {
        this.feedId = feedId;
    }

    public static PlaybackHistoryEvent listUpdated() {
        return new PlaybackHistoryEvent(null);
    }

    public static PlaybackHistoryEvent listUpdated(@Nullable Long feedId) {
        return new PlaybackHistoryEvent(feedId);
    }

    @Nullable
    public Long getFeedId() {
        return feedId;
    }

    @Override
    public String toString() {
        return "PlaybackHistoryEvent{feedId=" + feedId + "}";
    }
}
