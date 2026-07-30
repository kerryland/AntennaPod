package de.danoeh.antennapod.model.feed;
@Deprecated /* see FeedPreferences.PlaybackOrderSetting*/
public enum PlaybackOrder {
    OLDEST_FIRST(0),
    NEWEST_FIRST(1);

    private final int code;

    PlaybackOrder(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static PlaybackOrder fromCode(int code) {
        for (PlaybackOrder playbackOrder : values()) {
            if (playbackOrder.code == code) {
                return playbackOrder;
            }
        }
        throw new IllegalStateException();
    }
}
