package de.danoeh.antennapod;

import android.app.Application;
import android.util.Log;

import com.google.android.material.color.DynamicColors;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.EventBusException;

import de.danoeh.antennapod.playback.service.internal.BluetoothReconnectPlayer;

/** Main application class. */
public class PodcastApp extends Application {
    private static final String TAG = "PodcastApp";
    private BluetoothReconnectPlayer bluetoothReconnectPlayer;

    @Override
    public void onCreate() {
        Log.d(TAG, "PodcastApp.onCreate()");
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler(new CrashReportExceptionHandler());
        RxJavaErrorHandlerSetup.setupRxJavaErrorHandler();

        try {
            // Robolectric calls onCreate for every test, which causes problems with static members
            EventBus.builder()
                    .logNoSubscriberMessages(false)
                    .sendNoSubscriberEvent(false)
                    .installDefaultEventBus();
        } catch (EventBusException e) {
            Log.d(TAG, e.getMessage());
        }

        DynamicColors.applyToActivitiesIfAvailable(this);
        ClientConfigurator.initialize(this);
        PreferenceUpgrader.checkUpgrades(this);
        bluetoothReconnectPlayer = new BluetoothReconnectPlayer(this);
        bluetoothReconnectPlayer.register();
    }
}
