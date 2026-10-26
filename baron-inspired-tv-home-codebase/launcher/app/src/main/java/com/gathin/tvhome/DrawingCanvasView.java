package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * High-performance hardware-accelerated drawing canvas view for Android TV.
 * Renders normalized stroke paths received from the phone web remote with smooth bezier curves.
 */
public class DrawingCanvasView extends View {

    private final List<FamilyNoteManager.Stroke> strokes = new ArrayList<>();
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Path renderPath = new Path();

    public DrawingCanvasView(Context context) {
        super(context);
        init();
    }

    public DrawingCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DrawingCanvasView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public synchronized void setStrokes(List<FamilyNoteManager.Stroke> newStrokes) {
        strokes.clear();
        if (newStrokes != null) {
            strokes.addAll(newStrokes);
        }
        postInvalidateOnAnimation();
    }

    public synchronized void addStroke(FamilyNoteManager.Stroke stroke) {
        if (stroke != null && !stroke.points.isEmpty()) {
            strokes.add(stroke);
            postInvalidateOnAnimation();
        }
    }

    public synchronized void clearCanvas() {
        strokes.clear();
        postInvalidateOnAnimation();
    }

    public synchronized boolean hasContent() {
        return !strokes.isEmpty();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0 || strokes.isEmpty()) return;

        float minDim = Math.min(width, height);

        synchronized (this) {
            for (int sIdx = 0; sIdx < strokes.size(); sIdx++) {
                FamilyNoteManager.Stroke stroke = strokes.get(sIdx);
                List<FamilyNoteManager.Point> pts = stroke.points;
                if (pts == null || pts.isEmpty()) continue;

                float strokeWidth = Math.max(3f, stroke.width * minDim);
                int baseColor = stroke.color;

                if (pts.size() == 1) {
                    // Single tap dot
                    FamilyNoteManager.Point p = pts.get(0);
                    float cx = p.x * width;
                    float cy = p.y * height;

                    // Subtle ambient glow
                    glowPaint.setColor(baseColor);
                    glowPaint.setAlpha(45);
                    glowPaint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(cx, cy, strokeWidth * 1.5f, glowPaint);

                    // Solid dot
                    strokePaint.setColor(baseColor);
                    strokePaint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(cx, cy, strokeWidth / 2f, strokePaint);
                    continue;
                }

                // Smooth quadratic bezier path connecting midpoints
                renderPath.reset();
                FamilyNoteManager.Point p0 = pts.get(0);
                renderPath.moveTo(p0.x * width, p0.y * height);

                for (int i = 1; i < pts.size(); i++) {
                    FamilyNoteManager.Point prev = pts.get(i - 1);
                    FamilyNoteManager.Point curr = pts.get(i);
                    float prevX = prev.x * width;
                    float prevY = prev.y * height;
                    float currX = curr.x * width;
                    float currY = curr.y * height;
                    float midX = (prevX + currX) / 2f;
                    float midY = (prevY + currY) / 2f;
                    renderPath.quadTo(prevX, prevY, midX, midY);
                }

                FamilyNoteManager.Point last = pts.get(pts.size() - 1);
                renderPath.lineTo(last.x * width, last.y * height);

                // Pass 1: Luminous glow
                glowPaint.setColor(baseColor);
                glowPaint.setAlpha(35);
                glowPaint.setStyle(Paint.Style.STROKE);
                glowPaint.setStrokeWidth(strokeWidth * 2.2f);
                canvas.drawPath(renderPath, glowPaint);

                // Pass 2: Crisp crisp ink line
                strokePaint.setColor(baseColor);
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(strokeWidth);
                canvas.drawPath(renderPath, strokePaint);
            }
        }
    }
}
