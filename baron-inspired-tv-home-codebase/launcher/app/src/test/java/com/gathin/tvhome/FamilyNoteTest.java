package com.gathin.tvhome;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class FamilyNoteTest {

    private FakeSharedPreferences fakePrefs;

    @Before
    public void setUp() {
        fakePrefs = new FakeSharedPreferences();
        FamilyNoteManager.setTestPreferences(fakePrefs);
        FamilyNoteManager.clear(null);
    }

    @Test
    public void testInitialEmptyState() {
        assertFalse(FamilyNoteManager.hasContent());
        assertEquals(0, FamilyNoteManager.getStrokes().size());
    }

    @Test
    public void testAddStrokeAndNotification() {
        AtomicInteger notifications = new AtomicInteger(0);
        FamilyNoteManager.NoteChangeListener listener = strokes -> notifications.incrementAndGet();
        FamilyNoteManager.registerListener(listener);

        List<FamilyNoteManager.Point> points = new ArrayList<>();
        points.add(new FamilyNoteManager.Point(0.1f, 0.2f));
        points.add(new FamilyNoteManager.Point(0.3f, 0.4f));
        FamilyNoteManager.Stroke stroke = new FamilyNoteManager.Stroke("#FFFFFF", 0.02f, points);

        FamilyNoteManager.addStroke(null, stroke);

        assertTrue(FamilyNoteManager.hasContent());
        assertEquals(1, FamilyNoteManager.getStrokes().size());
        assertEquals(1, notifications.get());

        FamilyNoteManager.Stroke stored = FamilyNoteManager.getStrokes().get(0);
        assertEquals(0xFFFFFFFF, stored.color);
        assertEquals(0.02f, stored.width, 0.001f);
        assertEquals(2, stored.points.size());
        assertEquals(0.1f, stored.points.get(0).x, 0.001f);
        assertEquals(0.2f, stored.points.get(0).y, 0.001f);

        FamilyNoteManager.unregisterListener(listener);
    }

    @Test
    public void testClearStrokes() {
        AtomicInteger notifications = new AtomicInteger(0);
        FamilyNoteManager.NoteChangeListener listener = strokes -> notifications.incrementAndGet();
        FamilyNoteManager.registerListener(listener);

        List<FamilyNoteManager.Point> points = new ArrayList<>();
        points.add(new FamilyNoteManager.Point(0.5f, 0.5f));
        points.add(new FamilyNoteManager.Point(0.6f, 0.6f));
        FamilyNoteManager.addStroke(null, new FamilyNoteManager.Stroke("#FACC15", 0.02f, points));
        assertEquals(1, notifications.get());
        assertTrue(FamilyNoteManager.hasContent());

        FamilyNoteManager.clear(null);
        assertFalse(FamilyNoteManager.hasContent());
        assertEquals(0, FamilyNoteManager.getStrokes().size());
        assertEquals(2, notifications.get());

        FamilyNoteManager.unregisterListener(listener);
    }

    @Test
    public void testJsonSerializationRoundtrip() {
        List<FamilyNoteManager.Stroke> originalList = new ArrayList<>();
        List<FamilyNoteManager.Point> p1 = new ArrayList<>();
        p1.add(new FamilyNoteManager.Point(0.12f, 0.34f));
        p1.add(new FamilyNoteManager.Point(0.56f, 0.78f));
        originalList.add(new FamilyNoteManager.Stroke("#38BDF8", 0.02f, p1));

        String json = FamilyNoteManager.serializeToJson(originalList);
        assertNotNull(json);
        assertTrue(json.contains("38BDF8"));

        List<FamilyNoteManager.Stroke> deserialized = FamilyNoteManager.deserializeFromJson(json);
        assertEquals(1, deserialized.size());
        FamilyNoteManager.Stroke s = deserialized.get(0);
        assertEquals(0xFF38BDF8, s.color);
        assertEquals(0.02f, s.width, 0.001f);
        assertEquals(2, s.points.size());
        assertEquals(0.12f, s.points.get(0).x, 0.001f);
        assertEquals(0.34f, s.points.get(0).y, 0.001f);
    }

    @Test
    public void testParseSingleStrokeJson() {
        String json = "{\"color\":\"#F43F5E\",\"width\":0.025,\"points\":[{\"x\":0.25,\"y\":0.75},{\"x\":0.5,\"y\":0.9}]}";
        FamilyNoteManager.Stroke stroke = FamilyNoteManager.parseSingleStrokeJson(json);
        assertNotNull(stroke);
        assertEquals(0xFFF43F5E, stroke.color);
        assertEquals(0.025f, stroke.width, 0.001f);
        assertEquals(2, stroke.points.size());
        assertEquals(0.25f, stroke.points.get(0).x, 0.001f);
        assertEquals(0.75f, stroke.points.get(0).y, 0.001f);
    }

    @Test
    public void testParseSingleStrokeJsonMalformed() {
        assertNull(FamilyNoteManager.parseSingleStrokeJson(null));
        assertNull(FamilyNoteManager.parseSingleStrokeJson(""));
        assertNull(FamilyNoteManager.parseSingleStrokeJson("not json"));
        assertNull(FamilyNoteManager.parseSingleStrokeJson("{\"color\":\"#fff\"}")); // no points
    }

    @Test
    public void testPersistenceWithSharedPreferences() {
        List<FamilyNoteManager.Point> points = new ArrayList<>();
        points.add(new FamilyNoteManager.Point(0.1f, 0.1f));
        points.add(new FamilyNoteManager.Point(0.9f, 0.9f));
        FamilyNoteManager.addStroke(null, new FamilyNoteManager.Stroke("#34D399", 0.02f, points));

        // State is saved into fakePrefs
        assertTrue(fakePrefs.getString("family_note_json", "").contains("34D399"));

        // Simulate new app start: clear memory state without passing prefs, then load from fakePrefs
        FamilyNoteManager.clear(null);
        fakePrefs.edit().putString("family_note_json", "{\"strokes\":[{\"color\":\"#FF34D399\",\"width\":0.02,\"points\":[{\"x\":0.1,\"y\":0.1},{\"x\":0.9,\"y\":0.9}]}]}").commit();
        FamilyNoteManager.load(null);

        assertTrue(FamilyNoteManager.hasContent());
        assertEquals(1, FamilyNoteManager.getStrokes().size());
        assertEquals(0xFF34D399, FamilyNoteManager.getStrokes().get(0).color);
    }

    @Test
    public void testMaxStrokesBounding() {
        for (int i = 0; i < 110; i++) {
            List<FamilyNoteManager.Point> points = new ArrayList<>();
            points.add(new FamilyNoteManager.Point(0.1f, 0.1f));
            points.add(new FamilyNoteManager.Point(0.2f, 0.2f));
            FamilyNoteManager.addStroke(null, new FamilyNoteManager.Stroke("#FFFFFF", 0.02f, points));
        }

        // Bounded at 100 strokes
        assertTrue(FamilyNoteManager.getStrokes().size() <= 100);
    }
}
