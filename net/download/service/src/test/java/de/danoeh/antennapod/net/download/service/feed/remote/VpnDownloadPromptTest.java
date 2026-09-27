package de.danoeh.antennapod.net.download.service.feed.remote;

import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VpnDownloadPromptTest {

    @Before
    public void setUp() {
        VpnDownloadPrompt.reset();
    }

    @Test
    public void testPromptOnFirstCall() {
        Set<Long> episodeIds = new HashSet<>();
        episodeIds.add(1L);
        episodeIds.add(2L);
        assertTrue(VpnDownloadPrompt.recordPrompt(episodeIds));
    }

    @Test
    public void testNoRepromptForSameEpisodes() {
        Set<Long> episodeIds = new HashSet<>();
        episodeIds.add(1L);
        episodeIds.add(2L);
        assertTrue(VpnDownloadPrompt.recordPrompt(episodeIds));
        assertFalse(VpnDownloadPrompt.recordPrompt(new HashSet<>(episodeIds)));
    }

    @Test
    public void testRepromptWhenNewEpisodeAdded() {
        Set<Long> first = new HashSet<>();
        first.add(1L);
        first.add(2L);
        assertTrue(VpnDownloadPrompt.recordPrompt(first));

        Set<Long> withNewEpisode = new HashSet<>(first);
        withNewEpisode.add(3L);
        assertTrue(VpnDownloadPrompt.recordPrompt(withNewEpisode));
    }

    @Test
    public void testNoRepromptAfterEpisodeRemoved() {
        Set<Long> first = new HashSet<>();
        first.add(1L);
        first.add(2L);
        assertTrue(VpnDownloadPrompt.recordPrompt(first));

        Set<Long> subset = new HashSet<>();
        subset.add(1L);
        assertFalse(VpnDownloadPrompt.recordPrompt(subset));
    }
}
