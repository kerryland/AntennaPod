package de.danoeh.antennapod.net.discovery;

import de.danoeh.antennapod.net.common.AntennapodHttpClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Loads the podcast genres (categories) offered by the Apple Podcasts (iTunes) API.
 * The genres form a hierarchy (parent genre with nested subgenres).
 */
public class ItunesCategoryLoader {
    // iTunes "well-known" genre ID for podcasts
    private static final String PODCAST_ROOT = "26";
    private static final String GENRES_URL = "https://itunes.apple.com/WebObjects/MZStoreServices.woa/ws/genres?id=" + PODCAST_ROOT;


    public List<PodcastGenre> loadGenres() throws IOException, JSONException {
        OkHttpClient client = AntennapodHttpClient.getHttpClient();
        Request request = new Request.Builder().url(GENRES_URL).build();
        List<PodcastGenre> genres = new ArrayList<>();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException(response.toString());
            }
            JSONObject result = new JSONObject(response.body().string());
            JSONObject podcasts = result.getJSONObject(PODCAST_ROOT);
            JSONObject subgenres = podcasts.optJSONObject("subgenres");
            if (subgenres != null) {
                parseSubgenres(subgenres, genres);
            }
        }
        sort(genres);
        return genres;
    }

    private static void parseSubgenres(JSONObject subgenres, List<PodcastGenre> out) throws JSONException {
        Iterator<String> keys = subgenres.keys();
        while (keys.hasNext()) {
            JSONObject genre = subgenres.getJSONObject(keys.next());
            long id = genre.optLong("id", -1);
            String name = genre.optString("name", "");
            List<PodcastGenre> children = new ArrayList<>();
            JSONObject nested = genre.optJSONObject("subgenres");
            if (nested != null) {
                parseSubgenres(nested, children);
            }
            out.add(new PodcastGenre(id, -1, name, children));
        }
    }

    private static void sort(List<PodcastGenre> categories) {
        categories.sort(Comparator.comparing(c -> c.name, String.CASE_INSENSITIVE_ORDER));
        for (PodcastGenre category : categories) {
            sort(category.children);
        }
    }
}
