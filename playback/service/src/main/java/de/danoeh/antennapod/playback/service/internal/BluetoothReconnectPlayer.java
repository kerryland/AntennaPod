package de.danoeh.antennapod.playback.service.internal;

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

import androidx.media3.session.MediaController;

import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

/**
 * When a Bluetooth device connects, we might want to r
 * sume playback.
 */
public class BluetoothReconnectPlayer extends BroadcastReceiver {
    private static final String TAG = "BtReconnectPlayer";
    private static final long BT_CONNECT_TIMEOUT_MS = 4000;

    private final Context context;
    private final AudioManager audioManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AudioDeviceCallback audioDeviceCallback;
    private Runnable timeoutRunnable;

    public BluetoothReconnectPlayer(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        if (UserPreferences.isUnpauseOnBluetoothReconnect() &&
                BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) {
            waitForBluetoothAudioAndPlay();

        } else if (UserPreferences.isPauseOnHeadsetDisconnect() &&
                BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
            cancelPendingWait();
            // We *shouldn't* need this, but sometimes exoplayer.setHandleAudioBecomingNoisy()
            // does not seem to work.
            PlaybackController.bindToMedia3Service(context, MediaController::pause);
        }
    }

    private void waitForBluetoothAudioAndPlay() {
        cancelPendingWait();

        // Resume instantly if already connected to Bluetooth device
        AudioDeviceInfo connectedDevice = getConnectedBluetoothAudioDevice();
        if (connectedDevice != null) {
            Log.d(TAG, "Bluetooth audio already routed. Resuming immediately.");
            resumePlayback(connectedDevice);
            return;
        }

        // Wait for the Bluetooth device to arrive in audio system
        audioDeviceCallback = new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                for (AudioDeviceInfo device : addedDevices) {
                    if (isBluetoothAudioDevice(device)) {
                        Log.d(TAG, "Bluetooth audio output added: " + device.getProductName());
                        AudioDeviceInfo targetDevice = device;
                        // mainHandler.post ensures the audio service has the chance to finish
                        // internal routing to BT, avoiding audio leaking out the phone speaker
                        mainHandler.post(() -> resumePlayback(targetDevice));
                        return;
                    }
                }
            }
        };

        // A final, last ditch attempt to find the Bluetooth device
        timeoutRunnable = () -> {
            Log.w(TAG, "Timed out waiting for Bluetooth audio routing.");
            cancelPendingWait();

            // Final fallback check
            AudioDeviceInfo fallbackDevice = getConnectedBluetoothAudioDevice();
            if (fallbackDevice != null) {
                Log.d(TAG, "Found BT device on timeout fallback.");
                resumePlayback(fallbackDevice);
            } else {
                Log.i(TAG, "No Bluetooth audio device connected. Aborting playback to prevent speaker leakage.");
            }
        };

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler);
        mainHandler.postDelayed(timeoutRunnable, BT_CONNECT_TIMEOUT_MS);
    }

    private void resumePlayback(AudioDeviceInfo device) {
        cancelPendingWait();

        tellUserPlaybackResumed(context);
        Log.d(TAG, "Resume playback on device: " + device.getProductName());
        PlaybackController.bindToMedia3Service(context, MediaController::play);
    }

    private void cancelPendingWait() {
        if (audioDeviceCallback != null) {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback);
            audioDeviceCallback = null;
        }
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
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

    public void register() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        context.registerReceiver(this, filter);
    }

    public void unregister() {
        cancelPendingWait();
        try {
            context.unregisterReceiver(this);
        } catch (IllegalArgumentException ignored) {
        }
    }
}