package com.gathin.tvhome;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SleepTimerTest {

    @Before
    @After
    public void reset() {
        SleepTimerManager.cancelTimer(null);
    }

    @Test
    public void presetMinutes() {
        int[] expected = {15, 30, 45, 60};
        assertArrayEquals(expected, SleepTimerManager.PRESET_MINUTES);
    }

    @Test
    public void cycleNextSequence() {
        // Initially Off (0)
        assertFalse(SleepTimerManager.isTimerActive());

        // Cycle 1: 15m
        int step1 = SleepTimerManager.cycleNext(null);
        assertEquals(15, step1);
        assertTrue(SleepTimerManager.isTimerActive());
        assertEquals(15, SleepTimerManager.getTargetMinutes());

        // Cycle 2: 30m
        int step2 = SleepTimerManager.cycleNext(null);
        assertEquals(30, step2);
        assertTrue(SleepTimerManager.isTimerActive());
        assertEquals(30, SleepTimerManager.getTargetMinutes());

        // Cycle 3: 45m
        int step3 = SleepTimerManager.cycleNext(null);
        assertEquals(45, step3);
        assertTrue(SleepTimerManager.isTimerActive());
        assertEquals(45, SleepTimerManager.getTargetMinutes());

        // Cycle 4: 60m
        int step4 = SleepTimerManager.cycleNext(null);
        assertEquals(60, step4);
        assertTrue(SleepTimerManager.isTimerActive());
        assertEquals(60, SleepTimerManager.getTargetMinutes());

        // Cycle 5: Off (0)
        int step5 = SleepTimerManager.cycleNext(null);
        assertEquals(0, step5);
        assertFalse(SleepTimerManager.isTimerActive());
        assertEquals(0, SleepTimerManager.getTargetMinutes());
    }

    @Test
    public void timeFormatting() {
        assertEquals("Off", SleepTimerManager.formatRemaining(0));
        assertEquals("Off", SleepTimerManager.formatRemaining(-100));
        assertEquals("0:45", SleepTimerManager.formatRemaining(45 * 1000L));
        assertEquals("1:00", SleepTimerManager.formatRemaining(60 * 1000L));
        assertEquals("15:00", SleepTimerManager.formatRemaining(15 * 60 * 1000L));
        assertEquals("30:00", SleepTimerManager.formatRemaining(30 * 60 * 1000L));
        assertEquals("45:00", SleepTimerManager.formatRemaining(45 * 60 * 1000L));
        assertEquals("60:00", SleepTimerManager.formatRemaining(60 * 60 * 1000L));
    }

    @Test
    public void listenerNotifications() {
        AtomicInteger startedMinutes = new AtomicInteger(0);
        AtomicBoolean cancelledCalled = new AtomicBoolean(false);

        SleepTimerManager.SleepTimerListener listener = new SleepTimerManager.SleepTimerListener() {
            @Override
            public void onTick(long millisRemaining, String formattedRemaining) {}

            @Override
            public void onTimerStarted(int minutes, long millisRemaining) {
                startedMinutes.set(minutes);
            }

            @Override
            public void onTimerCancelled() {
                cancelledCalled.set(true);
            }

            @Override
            public void onDeepStandbyTriggered() {}
        };

        SleepTimerManager.registerListener(listener);

        SleepTimerManager.setTimer(null, 45);
        assertEquals(45, startedMinutes.get());

        SleepTimerManager.cancelTimer(null);
        assertTrue(cancelledCalled.get());

        SleepTimerManager.unregisterListener(listener);
    }
}
