package com.gathin.tvhome.ai;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TvActionExecutorSmokeTest {

    @Test
    public void execute_nullIntent_returnsError() {
        ExecutionResult result = TvActionExecutor.execute(null, null);
        assertEquals("error", result.status);
        assertEquals("none", result.action);
    }

    @Test
    public void execute_openAppWithoutPackage_returnsError() {
        Map<String, Object> params = new HashMap<>();
        params.put("label", "YouTube");
        TvIntent intent = new TvIntent(IntentType.LAUNCH_APP, "open_app", params, "open youtube");

        ExecutionResult result = TvActionExecutor.execute(null, intent);
        assertEquals("error", result.status);
        assertEquals("open_app", result.action);
        assertTrue(result.message.toLowerCase().contains("specify"));
        assertFalse(result.isFallback);
    }

    @Test
    public void execute_googleFallbackWithoutContext_returnsFallbackError() {
        TvIntent intent = TvIntent.googleFallback("who won world cup", "who won world cup");

        ExecutionResult result = TvActionExecutor.execute(null, intent);
        assertEquals("error", result.status);
        assertEquals("google_fallback", result.action);
        assertTrue(result.isFallback);
    }
}
