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
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import androidx.media3.common.DeviceInfo;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;

import java.util.HashSet;
import java.util.Set;

import de.danoeh.antennapod.playback.base.MediaItemAdapter;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

/**
 * When a Bluetooth device connects, we want to resume playback. When it disconnects,
 * we want to pause it.
 *
 * The receiver is registered in the manifest as a static receiver so that it can pick up
 * ACL connect/disconnect events even when the application has been closed. Because a static
 * receiver is instantiated by the system without a constructor context, all of the logic
 * operates on the {@link Context} passed into {@link #onReceive(Context, Intent)}.
 *
 * When the playback service is running, an {@link AudioDeviceCallback} is additionally
 * registered (via {@link #register()}/{@link #unregister()}) to react to the exact moment a
 * Bluetooth device is added/removed from the audio routing, and to support LE-audio device
 * types that do not reliably emit ACL broadcasts.
 */
public class BluetoothReconnectPlayer extends BroadcastReceiver {
    private static final String TAG = "BtReconnectPlayer";

    /**
     * Delay before resuming so the audio pipeline has a chance to finish routing to the
     * Bluetooth device, otherwise audio can leak out the phone speaker.
     */
    private static final long RESUME_RETRY_DELAY_MS = 200;

    /**
     * How long to wait after an ACL connect before looking for the Bluetooth audio sink.
     * The link often comes up before the route is registered, so resuming immediately would
     * either miss the device or leak audio out the speaker.
     */
    private static final long BLUETOOTH_AUDIO_SETTLE_DELAY_MS = 800;

    /**
     * How long to wait after a disconnect before deciding whether a Bluetooth sink remains.
     * Disconnect events can arrive before {@code getDevices()} reflects the removal, so we
     * must query again once the audio route has settled to avoid mistaking a full disconnect
     * for a device-to-device switch (which would let audio fall through to the speaker).
     */
    private static final long BLUETOOTH_DISCONNECT_SETTLE_DELAY_MS = 500;

    /**
     * How far to rewind when audio moves from one Bluetooth device to another. A short gap of
     * audio is typically lost during the route hand-over, so we reinstate it by rewinding.
     */
    private static final long BLUETOOTH_SWITCH_REWIND_MS = 3500;

    /**
     * Debounce window. Both the ACL broadcast and the AudioDeviceCallback fire for the same
     * device, and the two may even be delivered in a fresh process. A short window prevents
     * double resume / double vibration.
     */
    private static final long RESUME_DEBOUNCE_WINDOW_MS = 1500;

    private static long lastResumeRequestTime = 0;
    private static long lastVibrationTime = 0;

    private Context context;
    private AudioManager audioManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<String> knownBluetoothDevices = new HashSet<>();

    private final BroadcastReceiver audioBecomingNoisy = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                return;
            }
            Log.d(TAG, "onAudioBecomingNoisy");
            handleAudioBecomingNoisy(context);
        }
    };

    private final AudioDeviceCallback audioDeviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            Log.d(TAG, "onAudioDevicesAdded");
            if (!UserPreferences.isUnpauseOnBluetoothReconnect()) {
                Log.d(TAG, "Unpause on Bluetooth reconnect disabled");
                return;
            }
            for (AudioDeviceInfo device : addedDevices) {
                if (isBluetoothAudioDevice(device)) {
                    Log.d(TAG, "Bluetooth audio output added: " + device.getProductName());
                    if (knownBluetoothDevices.add(deviceKey(device))) {
                        Log.d(TAG, "New Bluetooth audio device, resuming");
                        notifyDeviceChange(context);
                        triggerResume(context);
                    } else {
                        Log.d(TAG, "Already-known Bluetooth audio device, ignoring");
                    }
                    return;
                } else {
                    Log.d(TAG, device.getProductName() + " is not a Bluetooth audio device");
                }
            }
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            Log.d(TAG, "onAudioDevicesRemoved");
            for (AudioDeviceInfo device : removedDevices) {
                if (isBluetoothAudioDevice(device)) {
                    knownBluetoothDevices.remove(deviceKey(device));
                    Log.d(TAG, "Bluetooth audio output removed: " + device.getProductName());
                    handleBluetoothDisconnect(context);
                    return;
                }
            }
        }
    };

    public BluetoothReconnectPlayer(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    /**
     * Required so Android can instantiate this receiver when it is declared in the manifest.
     * In that case {@link #onReceive(Context, Intent)} provides the context.
     */
    public BluetoothReconnectPlayer() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (this.context == null) {
            // A manifest-registered receiver must not bind to a service from its
            // ReceiverRestrictedContext, so always use the application context.
            this.context = context.getApplicationContext();
            this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        }
        if (intent == null || intent.getAction() == null) {
            return;
        }
        Log.d(TAG, "Received " + intent.getAction());

        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) {
            if (UserPreferences.isUnpauseOnBluetoothReconnect()) {
                // The broadcast often reaches us before the route is set up, so the device
                // may not be a Bluetooth audio sink yet. Wait for routing to settle, then
                // resume. This matters when playback was inactive and no AudioDeviceCallback
                // is registered to react to the later routing change.
                resumeWhenBluetoothSinkAvailable(this.context);
            }
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
            handleBluetoothDisconnect(this.context);
        }
    }

    /**
     * Decides what to do after a Bluetooth device disconnected. The decision is deferred so the
     * audio device list has settled: if another Bluetooth sink remains it is a device-to-device
     * switch (notify the user), otherwise it is a full disconnect (pause, to avoid audio leaking
     * out the phone speaker).
     */
    private void handleBluetoothDisconnect(Context context) {
        mainHandler.postDelayed(() -> {
            if (getConnectedBluetoothAudioDevice(context) == null) {
                Log.d(TAG, "No Bluetooth audio device remains, pausing to avoid speaker output");
                pausePlayback(context);
            } else {
                Log.d(TAG, "Another Bluetooth audio device remains, notifying");
                notifyDeviceChange(context);
                rewindPlayback(context, BLUETOOTH_SWITCH_REWIND_MS);
            }
        }, BLUETOOTH_DISCONNECT_SETTLE_DELAY_MS);
    }

    /**
     * Rewinds playback by {@code rewindMs}, to recover a small amount of audio that gets lost
     * when the route hand-over switches between Bluetooth devices.
     */
    private void rewindPlayback(Context context, long rewindMs) {
        Context bindContext = context.getApplicationContext();
        Log.d(TAG, "rewindPlayback: rewinding " + rewindMs + "ms");
        PlaybackController.bindToMedia3Service(bindContext, mediaController -> {
            if (isCastController(mediaController)) {
                Log.d(TAG, "rewindPlayback: casting, ignoring");
                return;
            }
            if (!mediaController.isPlaying()) {
                Log.d(TAG, "rewindPlayback: not playing, ignoring");
                return;
            }
            long target = Math.max(0, mediaController.getCurrentPosition() - rewindMs);
            Log.d(TAG, "rewindPlayback: seeking to " + target + "ms");
            mediaController.seekTo(target);
        });
    }

    /**
     * Reacts in near real time when audio is about to route to a public output (the speaker).
     * {@code ACTION_AUDIO_BECOMING_NOISY} only fires when the active route leaves a private
     * device for a public one, never for a device-to-device switch, so if we had a Bluetooth
     * sink connected this is a full Bluetooth disconnect and we pause immediately. For wired
     * headsets the user's setting is respected.
     */
    private void handleAudioBecomingNoisy(Context context) {
        boolean bluetoothWasActive = !knownBluetoothDevices.isEmpty();
        if (!bluetoothWasActive && !UserPreferences.isPauseOnHeadsetDisconnect()) {
            Log.d(TAG, "becomingNoisy: not from a Bluetooth disconnect, respecting setting");
            return;
        }
        Log.d(TAG, "becomingNoisy: pausing to avoid speaker output");
        pausePlayback(context);
    }

    /**
     * ACL links can come up before the device is reported as an audio sink, so poll a few
     * times rather than resuming spuriously or giving up too early.
     */
    private void resumeWhenBluetoothSinkAvailable(Context context) {
        if (getConnectedBluetoothAudioDevice(context) != null) {
            Log.d(TAG, "Bluetooth connected. Resuming.");
            notifyDeviceChange(context);
            triggerResume(context);
            return;
        }
        mainHandler.postDelayed(() -> {
            if (getConnectedBluetoothAudioDevice(context) != null) {
                Log.d(TAG, "Bluetooth connected (audio sink appeared). Resuming.");
                notifyDeviceChange(context);
                triggerResume(context);
            } else {
                Log.d(TAG, "Bluetooth connected but no audio sink detected yet");
            }
        }, BLUETOOTH_AUDIO_SETTLE_DELAY_MS);
    }

    /**
     * Buffers the resume request with a short delay, and debounces so a single device
     * connection is only acted upon once even if reported by both the ACL broadcast and
     * the AudioDeviceCallback (possibly in different process instances).
     */
    private void triggerResume(Context context) {
        long now = SystemClock.elapsedRealtime();
        Log.d(TAG, "triggerResume: now=" + now + " last=" + lastResumeRequestTime
                + " debounceMs=" + RESUME_DEBOUNCE_WINDOW_MS);
        if (now - lastResumeRequestTime < RESUME_DEBOUNCE_WINDOW_MS) {
            Log.d(TAG, "Ignoring resume request, debounced");
            return;
        }
        lastResumeRequestTime = now;
        mainHandler.postDelayed(() -> resumePlayback(context), RESUME_RETRY_DELAY_MS);
    }

    private void resumePlayback(Context context) {
        // Always bind from the application context: a receiver context is restricted from
        // binding to services.
        Context bindContext = context.getApplicationContext();
        Log.d(TAG, "resumePlayback: binding to media3 service");
        PlaybackController.bindToMedia3Service(bindContext, mediaController -> {
            Log.d(TAG, "resumePlayback: controller connected");
            if (!UserPreferences.isUnpauseOnBluetoothReconnect()) {
                Log.d(TAG, "Unpause on Bluetooth reconnect disabled");
                return;
            }
            if (isCastController(mediaController)) {
                Log.d(TAG, "resumePlayback: casting, ignoring");
                return;
            }
            if (mediaController.getPlaybackState() == Player.STATE_ENDED) {
                Log.d(TAG, "resumePlayback: playback has ended, not resuming");
                return;
            }
            long mediaId = PlaybackPreferences.getCurrentlyPlayingFeedMediaId();
            if (mediaId == PlaybackPreferences.NO_MEDIA_PLAYING) {
                Log.d(TAG, "resumePlayback: no media to resume");
                return;
            }
            if (mediaController.getCurrentMediaItem() == null) {
                // Cold start: restore the last played episode so PlaybackService can load it.
                Log.d(TAG, "resumePlayback: loading last media item " + mediaId);
                mediaController.setMediaItem(MediaItemAdapter.fromMediaIdStub(mediaId));
                mediaController.prepare();
            }
            if (mediaController.isPlaying()) {
                Log.d(TAG, "resumePlayback: already playing, nothing to do");
                return;
            }
            tellUserPlaybackResumed(context);
            Log.d(TAG, "resumePlayback: resuming");
            mediaController.play();
        });
    }

    /**
     * Notifies the user that a Bluetooth audio device connected, or that it switched to a
     * remaining device. This notification is deliberately not gated on playback state, so the
     * user always gets feedback when the audio output changes, even when nothing is playing.
     */
    private void notifyDeviceChange(Context context) {
        Log.d(TAG, "notifyDeviceChange: notifying via vibration");
        tellUserPlaybackResumed(context);
    }

    private void pausePlayback(Context context) {
        // A pause is a fresh state, so a later reconnect must be allowed to resume even if it
        // happened shortly after an earlier resume attempt. Otherwise the debounce could
        // swallow the resume and leave playback paused.
        lastResumeRequestTime = 0;
        // Always bind from the application context: a receiver context is restricted from
        // binding to services.
        Context bindContext = context.getApplicationContext();
        Log.d(TAG, "pausePlayback: binding to media3 service");
        PlaybackController.bindToMedia3Service(bindContext, mediaController -> {
            if (isCastController(mediaController)) {
                Log.d(TAG, "pausePlayback: casting, ignoring");
                return;
            }
            Log.d(TAG, "pausePlayback: pausing");
            mediaController.pause();
        });
    }

    private boolean isCastController(MediaController mediaController) {
        return mediaController.getDeviceInfo().playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE;
    }

    private AudioDeviceInfo getConnectedBluetoothAudioDevice(Context context) {
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) {
            return null;
        }
        return findConnectedBluetoothAudioDevice(manager);
    }

    private AudioDeviceInfo findConnectedBluetoothAudioDevice(AudioManager manager) {
        AudioDeviceInfo[] devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
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

    private String deviceKey(AudioDeviceInfo device) {
        return device.getType() + ":" + device.getId();
    }

    private void tellUserPlaybackResumed(Context context) {
        // Coalesce notifications so one device change never causes a double vibration.
        long now = SystemClock.elapsedRealtime();
        if (now - lastVibrationTime < RESUME_DEBOUNCE_WINDOW_MS) {
            Log.d(TAG, "Ignoring vibration, debounced");
            return;
        }
        lastVibrationTime = now;

        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager =
                    (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = manager != null ? manager.getDefaultVibrator() : null;
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
        if (vibrator == null || !vibrator.hasVibrator()) {
            Log.d(TAG, "No vibrator available, skipping vibration");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            long[] timings = new long[]{0, 100, 100, 200};
            int repeatIndex = -1;
            VibrationEffect effect;
            if (vibrator.hasAmplitudeControl()) {
                int[] amplitudes = new int[]{0, 255, 0, 150};
                effect = VibrationEffect.createWaveform(timings, amplitudes, repeatIndex);
            } else {
                // Without amplitude control the amplitude-array waveform is a no-op on
                // several devices, so fall back to a plain waveform.
                effect = VibrationEffect.createWaveform(timings, repeatIndex);
            }
            Log.d(TAG, "Vibrating to notify user");
            vibrator.vibrate(effect);
        }
    }

    /**
     * Registers the AudioDeviceCallback and the audio-becoming-noisy receiver. The
     * BroadcastReceiver for ACL events itself is registered in the manifest, so it does not
     * need to be registered dynamically.
     */
    public void register() {
        Log.d(TAG, "register: capturing currently-connected Bluetooth devices");
        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo device : devices) {
            if (isBluetoothAudioDevice(device)) {
                knownBluetoothDevices.add(deviceKey(device));
            }
        }
        Log.d(TAG, "register: registering AudioDeviceCallback");
        try {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register audio device callback", e);
        }
        Log.d(TAG, "register: registering audio-becoming-noisy receiver");
        try {
            IntentFilter noisyFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            context.registerReceiver(audioBecomingNoisy, noisyFilter);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register audio-becoming-noisy receiver", e);
        }
    }

    public void unregister() {
        Log.d(TAG, "unregister: unregistering AudioDeviceCallback");
        try {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback);
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister audio device callback", e);
        }
        try {
            context.unregisterReceiver(audioBecomingNoisy);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
