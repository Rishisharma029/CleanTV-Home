package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class NetworkSpeedTest {

    @Test
    public void speedFormatting() {
        assertEquals("-- Mbps", NetworkSpeedMeter.formatSpeed(0f));
        assertEquals("-- Mbps", NetworkSpeedMeter.formatSpeed(-5f));
        assertEquals("12.5 Mbps", NetworkSpeedMeter.formatSpeed(12.5f));
        assertEquals("145.8 Mbps", NetworkSpeedMeter.formatSpeed(145.82f));
        assertEquals("1000.0 Mbps", NetworkSpeedMeter.formatSpeed(1000f));
    }

    @Test
    public void defaultMeterState() {
        assertFalse(NetworkSpeedMeter.isRunning());
        assertNotNull(NetworkSpeedMeter.getLastRating());
    }
}
