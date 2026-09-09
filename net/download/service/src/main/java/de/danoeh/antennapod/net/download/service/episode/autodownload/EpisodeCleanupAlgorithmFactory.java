package de.danoeh.antennapod.net.download.service.episode.autodownload;

import de.danoeh.antennapod.storage.preferences.UserPreferences;

public abstract class EpisodeCleanupAlgorithmFactory {
    public static EpisodeCleanupAlgorithm build() {
        int cleanupValue = UserPreferences.getEpisodeCleanupValue();
        switch (cleanupValue) {
            case UserPreferences.EPISODE_CLEANUP_EXCEPT_FAVORITE:
                return new ExceptFavoriteCleanupAlgorithm();
            case UserPreferences.EPISODE_CLEANUP_QUEUE:
                return new EpisodeCountCleanupAlgorithm();
            case UserPreferences.EPISODE_CLEANUP_NULL:
                return new NoOpCleanupAlgorithm();
            default:
                return new TimeCleanupAlgorithm(cleanupValue);
        }
    }
}
