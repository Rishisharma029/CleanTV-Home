package com.gathin.tvhome;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Manages 1-click audio output switching between:
 * - TV Speakers (Built-in internal audio)
 * - Soundbar (eARC / ARC / Optical / SPDIF)
 * - Bluetooth Headphones / Wireless Audio
 */
public final class AudioOutputManager {

    public static final int MODE_TV_SPEAKERS = 0;
    public static final int MODE_SOUNDBAR = 1;
    public static final int MODE_BLUETOOTH = 2;

    private static final String PREFS_NAME = "cleantv_audio_prefs";
    private static final String KEY_OUTPUT_MODE = "audio_output_mode";

    public interface AudioOutputListener {
        void onAudioOutputChanged(int newMode, String modeName);
    }

    private static final List<AudioOutputListener> listeners = new ArrayList<>();

    private AudioOutputManager() {}

    public static synchronized void registerListener(AudioOutputListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static synchronized void unregisterListener(AudioOutputListener listener) {
        listeners.remove(listener);
    }

    public static int getCurrentMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_OUTPUT_MODE, MODE_TV_SPEAKERS);
    }

    public static String getModeName(int mode) {
        switch (mode) {
            case MODE_SOUNDBAR:
                return "Soundbar (eARC / Optical)";
            case MODE_BLUETOOTH:
                return "Bluetooth Headphones";
            case MODE_TV_SPEAKERS:
            default:
                return "TV Speakers";
        }
    }

    public static String getModeSubtitle(Context context, int mode) {
        switch (mode) {
            case MODE_SOUNDBAR: {
                String detected = getDetectedSoundbarName(context);
                return detected != null ? detected : "eARC / Optical Output";
            }
            case MODE_BLUETOOTH: {
                String btName = getConnectedBluetoothDeviceName(context);
                return btName != null ? btName : "Paired Wireless Audio";
            }
            case MODE_TV_SPEAKERS:
            default:
                return "Internal Built-In Audio";
        }
    }

    public static int getModeIconRes(int mode) {
        switch (mode) {
            case MODE_SOUNDBAR:
                return R.drawable.ic_soundbar;
            case MODE_BLUETOOTH:
                return R.drawable.ic_headphones;
            case MODE_TV_SPEAKERS:
            default:
                return R.drawable.ic_speaker;
        }
    }

    public static int cycleNextMode(Context context) {
        int current = getCurrentMode(context);
        int next = (current + 1) % 3;
        setAudioOutputMode(context, next);
        return next;
    }

    public static boolean setAudioOutputMode(Context context, int mode) {
        if (mode < MODE_TV_SPEAKERS || mode > MODE_BLUETOOTH) {
            mode = MODE_TV_SPEAKERS;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_OUTPUT_MODE, mode).apply();

        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        boolean applied = false;

        if (am != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    AudioDeviceInfo targetDevice = findTargetAudioDevice(am, mode);
                    if (targetDevice != null) {
                        applied = am.setCommunicationDevice(targetDevice);
                    } else if (mode == MODE_TV_SPEAKERS) {
                        am.clearCommunicationDevice();
                        applied = true;
                    }
                }

                // Fallbacks and legacy routing flags
                if (mode == MODE_TV_SPEAKERS) {
                    am.setSpeakerphoneOn(true);
                    am.setBluetoothScoOn(false);
                } else if (mode == MODE_BLUETOOTH) {
                    am.setSpeakerphoneOn(false);
                    am.setBluetoothScoOn(true);
                } else if (mode == MODE_SOUNDBAR) {
                    am.setSpeakerphoneOn(false);
                    am.setBluetoothScoOn(false);
                }
            } catch (Throwable ignored) {}
        }

        // Notify registered UI listeners
        String name = getModeName(mode);
        synchronized (AudioOutputManager.class) {
            for (AudioOutputListener listener : listeners) {
                try {
                    listener.onAudioOutputChanged(mode, name);
                } catch (Throwable ignored) {}
            }
        }

        // Action feedback and intent triggers
        if (mode == MODE_BLUETOOTH) {
            String btName = getConnectedBluetoothDeviceName(context);
            if (btName == null) {
                Toast.makeText(context, "Bluetooth Audio: Pair or connect headphones", Toast.LENGTH_SHORT).show();
                try {
                    Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Throwable ignored) {}
            } else {
                Toast.makeText(context, "Audio Output: " + btName, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(context, "Audio Output: " + name, Toast.LENGTH_SHORT).show();
        }

        return applied;
    }

    private static AudioDeviceInfo findTargetAudioDevice(AudioManager am, int mode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || am == null) return null;
        try {
            AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            if (devices == null) return null;

            for (AudioDeviceInfo dev : devices) {
                int type = dev.getType();
                if (mode == MODE_SOUNDBAR) {
                    if (type == AudioDeviceInfo.TYPE_HDMI_ARC
                            || type == AudioDeviceInfo.TYPE_HDMI
                            || type == AudioDeviceInfo.TYPE_LINE_DIGITAL
                            || type == 11 /* TYPE_SPDIF */) {
                        return dev;
                    }
                } else if (mode == MODE_BLUETOOTH) {
                    if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                            || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                            || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER))) {
                        return dev;
                    }
                } else if (mode == MODE_TV_SPEAKERS) {
                    if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                        return dev;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static String getDetectedSoundbarName(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null;
        try {
            AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            if (devices != null) {
                for (AudioDeviceInfo dev : devices) {
                    int type = dev.getType();
                    if (type == AudioDeviceInfo.TYPE_HDMI_ARC || type == AudioDeviceInfo.TYPE_HDMI) {
                        CharSequence name = dev.getProductName();
                        return name != null && name.length() > 0 ? name.toString() : "HDMI ARC / eARC Soundbar";
                    } else if (type == AudioDeviceInfo.TYPE_LINE_DIGITAL || type == 11 /* TYPE_SPDIF */) {
                        return "Optical / SPDIF Soundbar";
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static String getConnectedBluetoothDeviceName(Context context) {
        try {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (btAdapter != null && btAdapter.isEnabled()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return "Bluetooth Audio Ready";
                    }
                }
                Set<BluetoothDevice> paired = btAdapter.getBondedDevices();
                if (paired != null && !paired.isEmpty()) {
                    for (BluetoothDevice dev : paired) {
                        String name = dev.getName();
                        if (name != null && !name.isEmpty()) {
                            return name;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
