package com.gathin.tvhome;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameModeTest {

    @Test
    public void gameModeListenerLifecycle() {
        AtomicBoolean called = new AtomicBoolean(false);
        GameModeManager.GameModeListener listener = enabled -> called.set(enabled);

        GameModeManager.registerListener(listener);
        GameModeManager.unregisterListener(listener);
    }

    @Test
    public void defaultGameModeState() {
        // Null context safe check
        assertFalse(GameModeManager.isGameModeEnabled(null));
    }
}
