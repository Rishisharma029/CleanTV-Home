package com.gathin.tvhome.ai;

/**
 * Enumeration of recognized user intent domains for the CleanTV AI Controller.
 */
public enum IntentType {
    LAUNCH_APP,          // Opening installed apps by label, package, or alias
    VOLUME_CONTROL,      // Discrete steps, mute/unmute, and 0-100% volume
    SYSTEM_ACTION,       // Home, Settings, Wi-Fi, Bluetooth, Inputs, Manage Apps
    MEDIA_CONTROL,       // Play, Pause, Stop, Skip/Next, Previous/Rewind
    LAUNCHER_FEATURE,    // Control Center, Addons, Screensaver, Sleep Timer, Game Mode, Profiles
    GOOGLE_AI_FALLBACK,  // Conversational, 5W1H questions, weather, out-of-scope queries
    UNKNOWN              // Incomplete or empty commands requiring error feedback
}
