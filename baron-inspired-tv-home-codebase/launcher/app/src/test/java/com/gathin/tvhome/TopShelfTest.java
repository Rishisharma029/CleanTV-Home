package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class TopShelfTest {

    @Test
    public void shelfItemHoldsAttributesProperly() {
        TopShelfManager.ShelfItem item = new TopShelfManager.ShelfItem(
                "Stranger Things", "S4 E1 · Chapter One", "content://media/poster.jpg", "intent:#Intent", "com.netflix.ninja"
        );
        assertEquals("Stranger Things", item.title);
        assertEquals("S4 E1 · Chapter One", item.subtitle);
        assertEquals("content://media/poster.jpg", item.posterUri);
        assertEquals("intent:#Intent", item.intentUri);
        assertEquals("com.netflix.ninja", item.targetPackage);
        assertNull(item.posterBitmap);
    }

    @Test
    public void cancelPendingQueriesDoesNotThrow() {
        TopShelfManager.cancelPendingQueries();
        TopShelfManager.cancelPendingQueries();
    }

    @Test
    public void recycleItemsHandlesEmptyAndNullListSafely() {
        TopShelfManager.recycleItems(null);
        TopShelfManager.recycleItems(Collections.emptyList());

        List<TopShelfManager.ShelfItem> items = new ArrayList<>();
        items.add(new TopShelfManager.ShelfItem("YouTube", "Trending", null, null, "com.google.android.youtube.tv"));
        items.add(null);
        TopShelfManager.recycleItems(items);
        assertNotNull(items.get(0));
    }
}
