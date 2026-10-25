package com.gathin.tvhome;

import android.content.ComponentCallbacks2;
import android.graphics.Bitmap;
import android.graphics.Color;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * High-performance Texture Pool and Bounded 15 MB Bitmap Recycler.
 * Keeps app icon, artwork, and color analysis caches bounded to prevent GC stutter
 * and maintain 60–120 FPS on low-end 1GB RAM Android TV chipsets (MediaTek/Amlogic).
 */
public final class TexturePool {

    // Strictly bounded to 15 MB max memory
    public static final int MAX_CACHE_SIZE_BYTES = 15 * 1024 * 1024;

    private static class BoundedLruCache<K, V> extends LinkedHashMap<K, V> {
        private final int maxSizeBytes;
        private int currentSizeBytes = 0;

        public BoundedLruCache(int maxSizeBytes) {
            super(16, 0.75f, true); // true = access-order
            this.maxSizeBytes = maxSizeBytes;
        }

        @Override
        public synchronized V put(K key, V value) {
            if (key == null || value == null) return null;
            int size = sizeOf(value);
            currentSizeBytes += size;
            V prev = super.put(key, value);
            if (prev != null) {
                currentSizeBytes -= sizeOf(prev);
            }
            trimToSize(maxSizeBytes);
            return prev;
        }

        @Override
        public synchronized V get(Object key) {
            return super.get(key);
        }

        protected int sizeOf(V value) {
            if (value instanceof Bitmap) {
                try {
                    return ((Bitmap) value).getByteCount();
                } catch (Throwable ignored) {
                    return 1024;
                }
            }
            return 1;
        }

        public synchronized void trimToSize(int maxSize) {
            Iterator<Map.Entry<K, V>> it = entrySet().iterator();
            while (it.hasNext() && currentSizeBytes > maxSize) {
                Map.Entry<K, V> entry = it.next();
                currentSizeBytes -= sizeOf(entry.getValue());
                it.remove();
                onEntryRemoved(entry.getKey(), entry.getValue());
            }
        }

        public synchronized void evictAll() {
            clear();
            currentSizeBytes = 0;
        }

        public synchronized int size() {
            return currentSizeBytes;
        }

        protected void onEntryRemoved(K key, V value) {}
    }

    private static final BoundedLruCache<String, Bitmap> BITMAP_CACHE = new BoundedLruCache<String, Bitmap>(MAX_CACHE_SIZE_BYTES) {
        @Override
        protected void onEntryRemoved(String key, Bitmap oldValue) {
            if (oldValue != null) {
                try {
                    if (!oldValue.isRecycled()) {
                        offerReusableBitmap(oldValue);
                    }
                } catch (Throwable ignored) {}
            }
        }
    };

    // Color and backdrop gradient cache (max 256 entries each)
    private static final BoundedLruCache<String, Integer> DOMINANT_COLOR_CACHE = new BoundedLruCache<>(256);
    private static final BoundedLruCache<String, int[]> BACKDROP_CACHE = new BoundedLruCache<>(256);

    // Reusable bitmaps pool for BitmapFactory inBitmap
    private static final Set<SoftReference<Bitmap>> REUSABLE_BITMAPS =
            Collections.synchronizedSet(new HashSet<>());

    // Shared reusable analysis bitmaps to avoid createBitmap churn during palette analysis
    private static Bitmap sharedAnalysis24;
    private static Bitmap sharedAnalysis48;
    private static Bitmap sharedAnalysis12;

    private TexturePool() {}

    public static synchronized Bitmap getAnalysisBitmap24() {
        if (sharedAnalysis24 == null) {
            sharedAnalysis24 = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        }
        sharedAnalysis24.eraseColor(Color.TRANSPARENT);
        return sharedAnalysis24;
    }

    public static synchronized Bitmap getAnalysisBitmap48() {
        if (sharedAnalysis48 == null) {
            sharedAnalysis48 = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        }
        sharedAnalysis48.eraseColor(Color.TRANSPARENT);
        return sharedAnalysis48;
    }

    public static synchronized Bitmap getAnalysisBitmap12() {
        if (sharedAnalysis12 == null) {
            sharedAnalysis12 = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888);
        }
        sharedAnalysis12.eraseColor(Color.TRANSPARENT);
        return sharedAnalysis12;
    }

    public static Bitmap getBitmap(String key) {
        if (key == null) return null;
        return BITMAP_CACHE.get(key);
    }

    public static void putBitmap(String key, Bitmap bitmap) {
        if (key == null || bitmap == null) return;
        BITMAP_CACHE.put(key, bitmap);
    }

    public static Integer getCachedDominantColor(String key) {
        if (key == null) return null;
        return DOMINANT_COLOR_CACHE.get(key);
    }

    public static void cacheDominantColor(String key, int color) {
        if (key != null) DOMINANT_COLOR_CACHE.put(key, color);
    }

    public static int[] getCachedBackdrop(String key) {
        if (key == null) return null;
        return BACKDROP_CACHE.get(key);
    }

    public static void cacheBackdrop(String key, int[] backdrop) {
        if (key != null && backdrop != null) BACKDROP_CACHE.put(key, backdrop);
    }

    public static void offerReusableBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            try {
                if (bitmap.isMutable() && !bitmap.isRecycled()) {
                    REUSABLE_BITMAPS.add(new SoftReference<>(bitmap));
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Responds to system memory trim signals to keep 1GB RAM TVs running smoothly.
     */
    public static void onTrimMemory(int level) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            BITMAP_CACHE.evictAll();
            DOMINANT_COLOR_CACHE.evictAll();
            BACKDROP_CACHE.evictAll();
            REUSABLE_BITMAPS.clear();
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            BITMAP_CACHE.trimToSize(MAX_CACHE_SIZE_BYTES / 2);
            REUSABLE_BITMAPS.clear();
        }
    }

    public static int getCacheSizeBytes() {
        return BITMAP_CACHE.size();
    }
}
