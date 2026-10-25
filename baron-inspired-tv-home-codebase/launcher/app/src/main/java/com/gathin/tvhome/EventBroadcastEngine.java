package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * Zero-Polling / Battery-Friendly Event Broadcast Engine:
 * - Wakes clock strictly on Intent.ACTION_TIME_TICK (fired once a minute on the dot by Android alarm daemon).
 * - Wakes network status on ConnectivityManager.NetworkCallback broadcasts (zero polling).
 * - Detects Intent.ACTION_SCREEN_OFF to put all rendering, tickers, and players into deep sleep,
 *   and Intent.ACTION_SCREEN_ON to instantly wake up with zero latency.
 */
public final class EventBroadcastEngine {

    public interface ClockListener {
        void onTimeTick();
    }

    public interface ScreenListener {
        void onScreenOff();
        void onScreenOn();
    }

    public interface NetworkListener {
        void onNetworkChanged(boolean isConnected, boolean isWifi, boolean isEthernet);
    }

    private static final List<ClockListener> clockListeners = new ArrayList<>();
    private static final List<ScreenListener> screenListeners = new ArrayList<>();
    private static final List<NetworkListener> networkListeners = new ArrayList<>();

    private static BroadcastReceiver systemReceiver;
    private static ConnectivityManager.NetworkCallback networkCallback;
    private static boolean isRegistered = false;
    private static boolean isScreenOn = true;

    private EventBroadcastEngine() {}

    public static synchronized void register(Context context) {
        if (context == null || isRegistered) return;
        Context appCtx = context.getApplicationContext();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);

        systemReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                String action = intent.getAction();

                if (Intent.ACTION_TIME_TICK.equals(action)
                        || Intent.ACTION_TIME_CHANGED.equals(action)
                        || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
                    if (isScreenOn) {
                        notifyClockTick();
                    }
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    isScreenOn = false;
                    notifyScreenOff();
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    isScreenOn = true;
                    notifyScreenOn();
                    notifyClockTick(); // instant refresh on screen wake
                }
            }
        };

        appCtx.registerReceiver(systemReceiver, filter);

        // Network Callback (Zero Polling)
        try {
            ConnectivityManager cm = (ConnectivityManager) appCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkRequest request = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();

                networkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        checkAndNotifyNetwork(cm, network);
                    }

                    @Override
                    public void onLost(Network network) {
                        checkAndNotifyNetwork(cm, null);
                    }

                    @Override
                    public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                        checkAndNotifyNetwork(cm, network);
                    }
                };

                cm.registerNetworkCallback(request, networkCallback);
            }
        } catch (Throwable ignored) {}

        isRegistered = true;
    }

    public static synchronized void unregister(Context context) {
        if (!isRegistered || context == null) return;
        Context appCtx = context.getApplicationContext();

        if (systemReceiver != null) {
            try {
                appCtx.unregisterReceiver(systemReceiver);
            } catch (Throwable ignored) {}
            systemReceiver = null;
        }

        if (networkCallback != null) {
            try {
                ConnectivityManager cm = (ConnectivityManager) appCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    cm.unregisterNetworkCallback(networkCallback);
                }
            } catch (Throwable ignored) {}
            networkCallback = null;
        }

        isRegistered = false;
    }

    public static synchronized void addClockListener(ClockListener l) {
        if (l != null && !clockListeners.contains(l)) clockListeners.add(l);
    }

    public static synchronized void removeClockListener(ClockListener l) {
        clockListeners.remove(l);
    }

    public static synchronized void addScreenListener(ScreenListener l) {
        if (l != null && !screenListeners.contains(l)) screenListeners.add(l);
    }

    public static synchronized void removeScreenListener(ScreenListener l) {
        screenListeners.remove(l);
    }

    public static synchronized void addNetworkListener(NetworkListener l) {
        if (l != null && !networkListeners.contains(l)) networkListeners.add(l);
    }

    public static synchronized void removeNetworkListener(NetworkListener l) {
        networkListeners.remove(l);
    }

    public static boolean isScreenActive() {
        return isScreenOn;
    }

    private static synchronized void notifyClockTick() {
        for (ClockListener l : new ArrayList<>(clockListeners)) {
            try { l.onTimeTick(); } catch (Throwable ignored) {}
        }
    }

    private static synchronized void notifyScreenOff() {
        for (ScreenListener l : new ArrayList<>(screenListeners)) {
            try { l.onScreenOff(); } catch (Throwable ignored) {}
        }
    }

    private static synchronized void notifyScreenOn() {
        for (ScreenListener l : new ArrayList<>(screenListeners)) {
            try { l.onScreenOn(); } catch (Throwable ignored) {}
        }
    }

    private static void checkAndNotifyNetwork(ConnectivityManager cm, Network activeNetwork) {
        boolean connected = false;
        boolean wifi = false;
        boolean eth = false;

        if (cm != null && activeNetwork != null) {
            try {
                NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
                if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    connected = true;
                    wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                    eth = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
                }
            } catch (Throwable ignored) {}
        }

        synchronized (EventBroadcastEngine.class) {
            for (NetworkListener l : new ArrayList<>(networkListeners)) {
                try { l.onNetworkChanged(connected, wifi, eth); } catch (Throwable ignored) {}
            }
        }
    }
}
