package com.gathin.tvhome;

import android.graphics.Bitmap;

/**
 * Converts text/URLs into Android Bitmaps using pure Java QrCode.
 */
public final class QRCodeGenerator {

    private QRCodeGenerator() {}

    public static Bitmap generate(String text, int sizePx) {
        if (text == null || text.isEmpty()) return null;
        try {
            QrCode qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM);
            int qrSize = qr.size;
            int quietZone = 2; // Standard 2-cell quiet border
            int totalCells = qrSize + quietZone * 2;
            int scale = Math.max(1, sizePx / totalCells);
            int width = totalCells * scale;
            Bitmap bitmap = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888);

            int[] pixels = new int[width * width];
            for (int y = 0; y < width; y++) {
                int qrY = (y / scale) - quietZone;
                for (int x = 0; x < width; x++) {
                    int qrX = (x / scale) - quietZone;
                    boolean isDark = qrX >= 0 && qrX < qrSize && qrY >= 0 && qrY < qrSize && qr.getModule(qrX, qrY);
                    pixels[y * width + x] = isDark ? 0xFF000000 : 0xFFFFFFFF;
                }
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, width);
            return bitmap;
        } catch (Throwable t) {
            return null;
        }
    }
}
