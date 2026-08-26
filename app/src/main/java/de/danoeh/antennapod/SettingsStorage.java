package de.danoeh.antennapod;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.danoeh.antennapod.storage.database.PodDBAdapter;

/**
 * Mirrors the user's settings (stored in the default {@link SharedPreferences}) into the
 * {@code Preferences} table of the AntennaPod database. Because the database is replaced as a
 * whole during a backup import, storing settings there makes them survive a Database Import:
 * on startup the database values are restored back into {@link SharedPreferences}.
 */
public class SettingsStorage {
    private static final String TAG = "SettingsStorage";
    // PlaybackPreferences stores runtime playback state in the same default SharedPreferences;
    // those keys are not user settings and should not be persisted to the database.
    private static final String PLAYBACK_STATE_PREFIX = "de.danoeh.antennapod.preferences.";
    private static final ExecutorService DB_EXECUTOR = Executors.newSingleThreadExecutor();
    // SharedPreferences stores listeners in a WeakHashMap, so keep a strong reference to prevent GC.
    private static final SharedPreferences.OnSharedPreferenceChangeListener LISTENER =
            (sp, key) -> DB_EXECUTOR.submit(() -> mirror(PodDBAdapter.getInstance(), sp, key));

    private SettingsStorage() {
    }

    public static void install(Context context) {
        PodDBAdapter db = PodDBAdapter.getInstance();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Map<String, String> stored = db.getAllPreferences();
        if (stored.isEmpty()) {
            seedDatabase(db, prefs);
        } else {
            restorePreferences(db, prefs);
        }
        removeStoredPlaybackState(db);
        prefs.registerOnSharedPreferenceChangeListener(LISTENER);
    }

    private static boolean isSettingKey(String key) {
        return !key.startsWith(PLAYBACK_STATE_PREFIX);
    }

    private static void seedDatabase(PodDBAdapter db, SharedPreferences prefs) {
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (isSettingKey(entry.getKey())) {
                db.setPreference(entry.getKey(), encode(entry.getValue()));
            }
        }
    }

    private static void restorePreferences(PodDBAdapter db, SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, String> entry : db.getAllPreferences().entrySet()) {
            if (isSettingKey(entry.getKey())) {
                applyTypedValue(editor, entry.getKey(), entry.getValue());
            }
        }
        editor.apply();
    }

    private static void removeStoredPlaybackState(PodDBAdapter db) {
        for (String key : db.getAllPreferences().keySet()) {
            if (!isSettingKey(key)) {
                db.removePreference(key);
            }
        }
    }

    private static void mirror(PodDBAdapter db, SharedPreferences prefs, String key) {
        if (!isSettingKey(key)) {
            return;
        }
        try {
            Object value = prefs.getAll().get(key);
            if (value == null) {
                db.removePreference(key);
            } else {
                db.setPreference(key, encode(value));
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to persist setting '" + key + "'", e);
        }
    }

    private static void applyTypedValue(SharedPreferences.Editor editor, String key, String stored) {
        int separator = stored.indexOf(':');
        if (separator < 0) {
            editor.putString(key, stored);
            return;
        }
        String type = stored.substring(0, separator);
        String value = stored.substring(separator + 1);
        try {
            switch (type) {
                case "s":
                    editor.putString(key, value);
                    break;
                case "b":
                    editor.putBoolean(key, Boolean.parseBoolean(value));
                    break;
                case "i":
                    editor.putInt(key, Integer.parseInt(value));
                    break;
                case "l":
                    editor.putLong(key, Long.parseLong(value));
                    break;
                case "f":
                    editor.putFloat(key, Float.parseFloat(value));
                    break;
                case "set":
                    editor.putStringSet(key, decodeSet(value));
                    break;
                default:
                    editor.putString(key, stored);
                    break;
            }
        } catch (NumberFormatException e) {
            editor.putString(key, stored);
        }
    }

    private static String encode(Object value) {
        if (value instanceof Set<?>) {
            JSONArray array = new JSONArray();
            for (Object item : ((Set<?>) value)) {
                array.put(String.valueOf(item));
            }
            return "set:" + array;
        } else if (value instanceof Boolean) {
            return "b:" + value;
        } else if (value instanceof Integer) {
            return "i:" + value;
        } else if (value instanceof Long) {
            return "l:" + value;
        } else if (value instanceof Float) {
            return "f:" + value;
        }
        return "s:" + value;
    }

    private static Set<String> decodeSet(String value) {
        Set<String> set = new HashSet<>();
        try {
            JSONArray array = new JSONArray(value);
            for (int i = 0; i < array.length(); i++) {
                set.add(array.getString(i));
            }
        } catch (JSONException e) {
            // Ignore corrupt values and return an empty set.
        }
        return set;
    }
}
