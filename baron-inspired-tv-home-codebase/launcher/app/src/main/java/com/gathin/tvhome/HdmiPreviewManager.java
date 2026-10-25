package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.net.Uri;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Live HDMI Preview Manager:
 * Connects to Android TV's native TvInputManager and TvView hardware passthrough pipeline
 * to render mini live video feed thumbnails from connected HDMI inputs (PS5, Xbox, Cable box, etc.).
 */
public final class HdmiPreviewManager {

    public static final class HdmiDevice {
        public final String inputId;
        public final String portName;
        public final String deviceName;
        public final String category;
        public final String badge;
        public final int state;
        public final boolean isConnected;

        public HdmiDevice(String inputId, String portName, String deviceName,
                          String category, String badge, int state) {
            this.inputId = inputId;
            this.portName = portName;
            this.deviceName = deviceName;
            this.category = category;
            this.badge = badge;
            this.state = state;
            this.isConnected = (state == TvInputManager.INPUT_STATE_CONNECTED);
        }

        public String getDisplayTitle() {
            if (deviceName != null && !deviceName.isEmpty() && !deviceName.equalsIgnoreCase(portName)) {
                return deviceName + " (" + portName + ")";
            }
            return portName;
        }

        public String getDisplaySubtitle() {
            if (isConnected) {
                return "● LIVE FEED · " + badge;
            }
            return "STANDBY · " + category;
        }
    }

    private HdmiPreviewManager() {}

    /**
     * Inspects TvInputManager and classifies connected HDMI/CEC devices (PS5, Xbox, Cable box, etc.)
     */
    public static List<HdmiDevice> getConnectedHdmiDevices(Context context) {
        List<HdmiDevice> list = new ArrayList<>();
        TvInputManager tim = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return list;

        try {
            List<TvInputInfo> inputs = tim.getTvInputList();
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.isHidden(context)) continue;
                if (info.getType() != TvInputInfo.TYPE_HDMI) continue;

                String id = info.getId();
                CharSequence rawLabel = info.loadLabel(context);
                CharSequence customLabel = info.loadCustomLabel(context);

                String portName = rawLabel != null ? rawLabel.toString() : "HDMI";
                if (!portName.toUpperCase(Locale.ROOT).contains("HDMI")) {
                    portName = "HDMI " + portName;
                }

                String deviceName = customLabel != null && customLabel.length() > 0
                        ? customLabel.toString() : portName;

                DeviceInfo classified = classifyDevice(deviceName, id);
                int state = tim.getInputState(id);

                list.add(new HdmiDevice(id, portName, classified.name, classified.category, classified.badge, state));
            }
        } catch (Exception ignored) {}

        return list;
    }

    /**
     * Tunes a TvView to an HDMI passthrough input for a silent mini live video thumbnail.
     */
    public static void tunePreview(TvView tvView, String inputId, Runnable onVideoReady) {
        if (tvView == null || inputId == null || inputId.isEmpty()) return;
        try {
            tvView.reset();
            tvView.setStreamVolume(0.0f); // Muted for preview
            tvView.setCallback(new TvView.TvInputCallback() {
                @Override
                public void onVideoAvailable(String id) {
                    if (onVideoReady != null) onVideoReady.run();
                }

                @Override
                public void onVideoUnavailable(String id, int reason) {
                    // Video stream temporarily unavailable or device in standby
                }
            });

            Uri channelUri = TvContract.buildChannelUriForPassthroughInput(inputId);
            tvView.tune(inputId, channelUri);
        } catch (Exception ignored) {}
    }

    /**
     * Releases TvView hardware decoder resources immediately.
     */
    public static void releasePreview(TvView tvView) {
        if (tvView == null) return;
        try {
            tvView.reset();
        } catch (Exception ignored) {}
    }

    public static final class DeviceInfo {
        public final String name;
        public final String category;
        public final String badge;

        public DeviceInfo(String name, String category, String badge) {
            this.name = name;
            this.category = category;
            this.badge = badge;
        }
    }

    /**
     * Identifies gaming consoles, set-top boxes, streaming sticks, and computers by label and ID.
     */
    public static DeviceInfo classifyDevice(String label, String inputId) {
        String lower = (label + " " + inputId).toLowerCase(Locale.ROOT);

        if (lower.contains("ps5") || lower.contains("playstation 5")) {
            return new DeviceInfo("PlayStation 5", "Gaming Console", "4K 120Hz HDR");
        } else if (lower.contains("ps4") || lower.contains("playstation")) {
            return new DeviceInfo("PlayStation 4", "Gaming Console", "1080p HDR");
        } else if (lower.contains("xbox") || lower.contains("series x") || lower.contains("series s")) {
            return new DeviceInfo("Xbox Series X|S", "Gaming Console", "4K 120Hz HDR");
        } else if (lower.contains("switch") || lower.contains("nintendo")) {
            return new DeviceInfo("Nintendo Switch", "Gaming Console", "1080p 60Hz");
        } else if (lower.contains("apple tv") || lower.contains("appletv")) {
            return new DeviceInfo("Apple TV 4K", "Streaming Box", "4K Dolby Vision");
        } else if (lower.contains("fire tv") || lower.contains("firestick")) {
            return new DeviceInfo("Fire TV Stick", "Streaming Stick", "4K 60Hz");
        } else if (lower.contains("chromecast") || lower.contains("google tv")) {
            return new DeviceInfo("Chromecast / Google TV", "Streaming Box", "4K HDR");
        } else if (lower.contains("stb") || lower.contains("cable") || lower.contains("set-top")
                || lower.contains("tata") || lower.contains("airtel") || lower.contains("dish")
                || lower.contains("sky") || lower.contains("directv")) {
            return new DeviceInfo("Cable / Set-Top Box", "Live TV / Cable", "1080i / 4K");
        } else if (lower.contains("mac") || lower.contains("pc") || lower.contains("laptop") || lower.contains("desktop")) {
            return new DeviceInfo("PC / Mac Display", "Computer Input", "4K 60Hz");
        }

        // Generic HDMI with port number
        String portNum = "1";
        if (lower.contains("hdmi 2") || lower.contains("hdmi2") || lower.contains("hw2")) portNum = "2";
        else if (lower.contains("hdmi 3") || lower.contains("hdmi3") || lower.contains("hw3")) portNum = "3";
        else if (lower.contains("hdmi 4") || lower.contains("hdmi4") || lower.contains("hw4")) portNum = "4";

        return new DeviceInfo("HDMI " + portNum, "Auxiliary Input", "HDMI 2.1 / eARC");
    }
}
