package de.danoeh.antennapod.playback.service.internal;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.CombinedVibration;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.media3.session.MediaController;

import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

/**
 * When a Bluetooth device connects, we might want to
 * resume playback.
 */
public class BluetoothReconnectPlayer extends BroadcastReceiver {
    private static final String TAG = "BtReconnectPlayer";

    private final Context context;
    private final AudioManager audioManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final AudioDeviceCallback audioDeviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            Log.d(TAG, "onAudioDevicesAdded: " + addedDevices.length + " devices");
            if (!UserPreferences.isUnpauseOnBluetoothReconnect()) {
                return;
            }
            for (AudioDeviceInfo device : addedDevices) {
                if (isBluetoothAudioDevice(device)) {
                    Log.d(TAG, "Bluetooth audio output added: " + device.getProductName() + ". Resume!");
                    // mainHandler.post ensures the audio service has the chance to finish
                    // internal routing to BT, avoiding audio leaking out the phone speaker
                    mainHandler.post(() -> resumePlayback());
                    return;
                }
            }
        }
    };

    public BluetoothReconnectPlayer(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        Log.d(TAG, "onReceive: " + intent.getAction());
        String action = intent.getAction();

        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)
                || (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)
                    && intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1) == BluetoothA2dp.STATE_CONNECTED)) {

            if (UserPreferences.isUnpauseOnBluetoothReconnect()) {
                Log.d(TAG, "Bluetooth connection confirmed. Triggering callback.");
                // mainHandler.post ensures the audio service has the chance to finish
                // internal routing to BT, avoiding audio leaking out the phone speaker
                mainHandler.post(() -> resumePlayback());
            }

        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)
                || (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)
                    && intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1) == BluetoothA2dp.STATE_DISCONNECTED)) {

            if (UserPreferences.isPauseOnHeadsetDisconnect() && getConnectedBluetoothAudioDevice() == null) {
                Log.d(TAG, "Bluetooth disconnected. Pausing");
                pausePlayback();
            }
        }
    }

    private void resumePlayback() {
        Log.d(TAG, "Inside resumePlayback");
        tellUserPlaybackResumed(context);
        PlaybackController.bindToMedia3Service(context, MediaController::play);
    }

    private AudioDeviceInfo getConnectedBluetoothAudioDevice() {
        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo device : devices) {
            if (isBluetoothAudioDevice(device)) {
                return device;
            }
        }
        return null;
    }

    private boolean isBluetoothAudioDevice(AudioDeviceInfo device) {
        // Must be an output device
        if (!device.isSink()) {
            return false;
        }

        int type = device.getType();
        boolean isBluetooth = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isBluetooth = isBluetooth || type == AudioDeviceInfo.TYPE_BLE_HEADSET;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isBluetooth = isBluetooth
                    || type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                    || type == AudioDeviceInfo.TYPE_BLE_BROADCAST;
        }

        return isBluetooth;
    }

    private void tellUserPlaybackResumed(Context context) {
        // Vibrate the phone. Note that a simple 500ms vibrate didn't do anything on my device
        // and the 0 amplitude values here make a real difference.
        long[] timings = new long[]{0, 100, 100, 200};
        int[] amplitudes = new int[]{0, 255, 0, 150};
        int repeatIndex = -1;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager =
                    (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);

            if (vibratorManager != null) {
                VibrationEffect effect = VibrationEffect.createWaveform(timings, amplitudes, repeatIndex);
                vibratorManager.vibrate(CombinedVibration.createParallel(effect));
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                VibrationEffect effect = VibrationEffect.createWaveform(timings, amplitudes, repeatIndex);
                vibrator.vibrate(effect);
            }
        }
    }

    private void pausePlayback() {
        PlaybackController.bindToMedia3Service(context, MediaController::pause);
    }

    public void register() {
        Log.d(TAG, "Registering BluetoothReconnectPlayer");
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);

        // System broadcasts like Bluetooth connection state don't strictly require flags,
        // but modern Android versions prefer explicit exported status.
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_EXPORTED);

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler);
    }

    public void unregister() {
        Log.d(TAG, "Unregistering BluetoothReconnectPlayer");
        try {
            context.unregisterReceiver(this);
        } catch (IllegalArgumentException ignored) {
        }
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback);
    }
}
