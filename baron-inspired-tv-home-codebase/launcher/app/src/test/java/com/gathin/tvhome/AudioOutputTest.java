package com.gathin.tvhome;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AudioOutputTest {

    @Test
    public void audioModeConstants() {
        assertEquals(0, AudioOutputManager.MODE_TV_SPEAKERS);
        assertEquals(1, AudioOutputManager.MODE_SOUNDBAR);
        assertEquals(2, AudioOutputManager.MODE_BLUETOOTH);
    }

    @Test
    public void audioModeNames() {
        assertEquals("TV Speakers", AudioOutputManager.getModeName(AudioOutputManager.MODE_TV_SPEAKERS));
        assertEquals("Soundbar (eARC / Optical)", AudioOutputManager.getModeName(AudioOutputManager.MODE_SOUNDBAR));
        assertEquals("Bluetooth Headphones", AudioOutputManager.getModeName(AudioOutputManager.MODE_BLUETOOTH));
    }

    @Test
    public void audioModeDrawables() {
        assertEquals(R.drawable.ic_speaker, AudioOutputManager.getModeIconRes(AudioOutputManager.MODE_TV_SPEAKERS));
        assertEquals(R.drawable.ic_soundbar, AudioOutputManager.getModeIconRes(AudioOutputManager.MODE_SOUNDBAR));
        assertEquals(R.drawable.ic_headphones, AudioOutputManager.getModeIconRes(AudioOutputManager.MODE_BLUETOOTH));
    }

    @Test
    public void audioOutputListenerRegistration() {
        AtomicInteger reportedMode = new AtomicInteger(-1);
        AudioOutputManager.AudioOutputListener listener = (newMode, modeName) -> {
            reportedMode.set(newMode);
        };

        AudioOutputManager.registerListener(listener);
        AudioOutputManager.unregisterListener(listener);
    }
}
