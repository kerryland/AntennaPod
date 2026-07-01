package de.danoeh.antennapod.ui.screen.preferences;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.net.download.service.feed.VpnNetworkChecker;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.preferences.screen.AnimatedPreferenceFragment;
import de.danoeh.antennapod.ui.preferences.screen.downloads.ChooseDataFolderDialog;

import java.io.File;


public class DownloadsPreferencesFragment extends AnimatedPreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String PREF_SCREEN_AUTODL = "prefAutoDownloadSettings";
    private static final String PREF_SCREEN_AUTO_DELETE = "prefAutoDeleteScreen";
    private static final String PREF_PROXY = "prefProxy";
    private static final String PREF_CHOOSE_DATA_DIR = "prefChooseDataDir";
    private static final String PREF_VPN_DOWNLOAD = "prefVpnDownload";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_downloads);
        setupNetworkScreen();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((PreferenceActivity) getActivity()).getSupportActionBar().setTitle(R.string.downloads_pref);
        PreferenceManager.getDefaultSharedPreferences(getContext()).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        PreferenceManager.getDefaultSharedPreferences(getContext()).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        setDataFolderText();
    }

    private void setupNetworkScreen() {
        findPreference(PREF_SCREEN_AUTODL).setOnPreferenceClickListener(preference -> {
            ((PreferenceActivity) getActivity()).openScreen(R.xml.preferences_autodownload);
            return true;
        });
        findPreference(PREF_SCREEN_AUTO_DELETE).setOnPreferenceClickListener(preference -> {
            ((PreferenceActivity) getActivity()).openScreen(R.xml.preferences_auto_deletion);
            return true;
        });
        // validate and set correct value: number of downloads between 1 and 50 (inclusive)
        findPreference(PREF_PROXY).setOnPreferenceClickListener(preference -> {
            ProxyDialog dialog = new ProxyDialog(getActivity());
            dialog.show();
            return true;
        });
        findPreference(PREF_CHOOSE_DATA_DIR).setOnPreferenceClickListener(preference -> {
            ChooseDataFolderDialog.showDialog(getContext(), path -> {
                UserPreferences.setDataFolder(path);
                setDataFolderText();
            });
            return true;
        });

        SwitchPreferenceCompat vpnSwitch = findPreference(PREF_VPN_DOWNLOAD);
        if (vpnSwitch != null) {
            vpnSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean isVpnEnabled = (Boolean) newValue;
                UserPreferences.setVpnDownload(isVpnEnabled);

                if (isVpnEnabled) {
                    Log.d("KJS", "User turned the VPN switch ON");
                    VpnNetworkChecker.launchVpnSelector(getContext());
                } else {
                    Log.d("KJS", "User turned the VPN switch OFF");
                }

                return true;
            });
        }

        setDataFolderText();
    }

    private void setDataFolderText() {
        File f = UserPreferences.getDataFolder(null);
        if (f != null) {
            findPreference(PREF_CHOOSE_DATA_DIR).setSummary(f.getAbsolutePath());
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (UserPreferences.PREF_UPDATE_INTERVAL_MINUTES.equals(key)
                || UserPreferences.PREF_MOBILE_UPDATE.equals(key)) {
            FeedUpdateManager.getInstance().restartUpdateAlarm(getContext(), true);
        }
    }
}
