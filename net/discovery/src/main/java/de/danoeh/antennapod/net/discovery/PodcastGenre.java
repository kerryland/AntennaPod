package de.danoeh.antennapod.net.discovery;

import java.util.List;

/**
 * A genre used for browsing podcasts, matching Apple Podcasts' "genre" terminology.
 * Genres form a hierarchy via {@link #children}.
 */
public class PodcastGenre {
    public long id;
    public long podcastIndexId;
    public final String name;
    public final List<PodcastGenre> children;

    public PodcastGenre(long id, long podcastIndexId, String name, List<PodcastGenre> children) {
        this.id = id;
        this.podcastIndexId = podcastIndexId;
        this.name = name;
        this.children = children;
    }
}
