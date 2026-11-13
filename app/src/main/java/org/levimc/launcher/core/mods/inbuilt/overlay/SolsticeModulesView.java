package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import org.levimc.launcher.R;
import org.levimc.launcher.core.mods.inbuilt.UnifiedMod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Solstice-style ClickGui: horizontally scrollable category columns whose rows
 * are module toggles. Enabled modules are filled with the accent gradient, a
 * gear opens the module config and the star toggles a favourite. Rendered with
 * Canvas so it looks identical on every OEM theme and cannot crash on a
 * Material theme mismatch.
 */
public class SolsticeModulesView extends View {

    public interface Listener {
        void onToggle(UnifiedMod mod, boolean enabled);
        void onConfig(UnifiedMod mod);
        void onFavorite(UnifiedMod mod, boolean favorite);
    }

    private static final int COLOR_HEADER = 0xFF12151E;
    private static final int COLOR_ROW = 0xFF11131B;
    private static final int COLOR_ROW_LAST = 0xFF11131B;
    private static final int COLOR_ROW_ON = 0xFF272B36;
    private static final int COLOR_TEXT = 0xFFD9DBE2;
    private static final int COLOR_TEXT_DIM = 0xFF9296A2;
    private static final int COLOR_TEXT_FAINT = 0xFF5E6370;
    private static final int COLOR_GEAR = 0xFF5E6370;
    private static final int COLOR_STAR = 0xFF5E6370;
    private static final int COLOR_STAR_ON = 0xFF66A4FF;
    private static final int COLOR_SEPARATOR = 0xFF20232E;
    private static final int COLOR_BORDER = 0xFF20232E;
    private static final int COLOR_ACCENT = 0xFF4E83FF;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path starPath = new Path();
    private final RectF rect = new RectF();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final float density;
    private final float scaledDensity;
    private android.graphics.Typeface uiTypeface;
    private android.graphics.Typeface displayTypeface;
    private final int touchSlop;
    private final int longPressTimeout;

    private final float rowHeight;
    private final float headerHeight;
    private final float gap;
    private final float padding;
    private final float radius;

    private final List<Column> columns = new ArrayList<>();
    private Listener listener;

    private float columnWidth;
    private float contentWidth;
    private float contentHeight;
    private float scrollX;
    private float scrollY;

    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private float dragDx;
    private float dragDy;
    private float downScrollX;
    private float downScrollY;
    private long downTime;
    private boolean dragging;
    private boolean longPressFired;
    private Hit pendingHit;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (dragging || pendingHit == null || pendingHit.mod == null) return;
            longPressFired = true;
            if (listener != null) listener.onConfig(pendingHit.mod);
            invalidate();
        }
    };

    public SolsticeModulesView(Context context) {
        this(context, null);
    }

    public SolsticeModulesView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        longPressTimeout = ViewConfiguration.getLongPressTimeout();

        rowHeight = 37f * density;
        headerHeight = 30f * density;
        gap = 12f * density;
        padding = 8f * density;
        radius = 13f * density;
        columnWidth = 230f * density;

        try {
            uiTypeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.inter_medium);
            displayTypeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.space_grotesk_semibold);
        } catch (Exception ignored) {
            uiTypeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL);
            displayTypeface = android.graphics.Typeface.DEFAULT_BOLD;
        }
        textPaint.setTypeface(uiTypeface);
        setClickable(true);
        setFocusable(false);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setContent(List<UnifiedMod> mods, Set<String> favoriteKeys) {
        columns.clear();
        Map<String, Column> byGroup = new LinkedHashMap<>();
        if (mods != null) {
            for (UnifiedMod mod : mods) {
                String group = mod.getGroupName();
                if (group == null || group.trim().isEmpty()) group = "Modules";
                Column column = byGroup.get(group);
                if (column == null) {
                    column = new Column(group);
                    byGroup.put(group, column);
                    columns.add(column);
                }
                boolean favorite = favoriteKeys != null && favoriteKeys.contains(mod.getStableKey());
                column.rows.add(new Row(mod, favorite));
            }
        }
        recomputeLayout();
        clampScroll();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(longPressRunnable);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recomputeLayout();
        clampScroll();
    }

    private void recomputeLayout() {
        float widest = 0f;
        textPaint.setTextSize(12.5f * scaledDensity);
        for (Column column : columns) {
            for (Row row : column.rows) {
                if (row.mod.getName() != null) {
                    widest = Math.max(widest, textPaint.measureText(row.mod.getName()));
                }
            }
        }
        float available = getWidth() > 0 ? getWidth() * 0.86f : 320f * density;
        float wanted = widest + 70f * density + padding * 3f;
        columnWidth = Math.max(190f * density, Math.min(available, wanted));
        columnWidth = Math.min(columnWidth, 340f * density);

        float x = padding;
        float maxHeight = 0f;
        for (Column column : columns) {
            column.x = x;
            column.width = columnWidth;
            column.height = headerHeight + column.rows.size() * rowHeight;
            x += columnWidth + gap;
            maxHeight = Math.max(maxHeight, column.height);
        }
        contentWidth = columns.isEmpty() ? 0f : x - gap + padding;
        contentHeight = maxHeight + padding * 2f;
    }

    private void clampScroll() {
        float maxX = Math.max(0f, contentWidth - getWidth());
        float maxY = Math.max(0f, contentHeight - getHeight());
        scrollX = Math.max(0f, Math.min(scrollX, maxX));
        scrollY = Math.max(0f, Math.min(scrollY, maxY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (columns.isEmpty()) return;

        float baseY = padding - scrollY;
        for (Column column : columns) {
            float x = column.x - scrollX;
            if (x > getWidth() || x + column.width < 0) continue;
            drawColumn(canvas, column, x, baseY);
        }
    }

    private void drawColumn(Canvas canvas, Column column, float x, float top) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_HEADER);
        rect.set(x, top, x + column.width, top + headerHeight);
        canvas.drawRoundRect(rect, radius, radius, paint);
        rect.set(x, top + headerHeight - radius, x + column.width, top + headerHeight);
        canvas.drawRect(rect, paint);

        textPaint.setTypeface(displayTypeface);
        textPaint.setTextSize(10f * scaledDensity);
        textPaint.setColor(COLOR_TEXT_FAINT);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setLetterSpacing(0.08f);
        float headerBaseline = top + headerHeight / 2f
            - (textPaint.ascent() + textPaint.descent()) / 2f;
        canvas.drawText(column.name.toUpperCase(java.util.Locale.ROOT),
            x + 14f * density, headerBaseline, textPaint);
        textPaint.setLetterSpacing(0f);

        int count = column.rows.size();
        for (int i = 0; i < count; i++) {
            Row row = column.rows.get(i);
            float rowTop = top + headerHeight + i * rowHeight;
            float rowBottom = rowTop + rowHeight;
            if (rowBottom < 0 || rowTop > getHeight()) continue;

            boolean last = i == count - 1;
            float corner = last ? radius : 0f;
            rect.set(x, rowTop, x + column.width, rowBottom);

            paint.setShader(null);
            paint.setColor(row.mod.isEnabled() ? COLOR_ROW_ON : (last ? COLOR_ROW_LAST : COLOR_ROW));
            canvas.drawRoundRect(rect, corner, corner, paint);

            if (row.mod.isEnabled()) {
                paint.setColor(COLOR_ACCENT);
                canvas.drawRoundRect(new RectF(x, rowTop, x + 2.5f * density, rowBottom),
                    1.2f * density, 1.2f * density, paint);
            }

            paint.setColor(COLOR_SEPARATOR);
            paint.setStrokeWidth(density);
            canvas.drawLine(x + 12f * density, rowTop, x + column.width - 12f * density,
                rowTop, paint);

            float iconSpace = 58f * density;
            float maxTextWidth = column.width - iconSpace - 20f * density;
            String name = row.mod.getName() == null ? "" : row.mod.getName();
            float textSize = 12.5f * scaledDensity;
            textPaint.setTextSize(textSize);
            while (textSize > 9f * scaledDensity && textPaint.measureText(name) > maxTextWidth) {
                textSize -= 0.5f * scaledDensity;
                textPaint.setTextSize(textSize);
            }
            textPaint.setTypeface(uiTypeface);
            textPaint.setColor(row.mod.isEnabled() ? COLOR_TEXT : COLOR_TEXT_DIM);
            textPaint.setTextAlign(Paint.Align.LEFT);
            float textBaseline = rowTop + rowHeight / 2f
                - (textPaint.ascent() + textPaint.descent()) / 2f;
            canvas.drawText(name, x + 16f * density, textBaseline, textPaint);

            drawStar(canvas, x + column.width - 42f * density, rowTop + rowHeight / 2f,
                row.favorite);
            drawGear(canvas, x + column.width - 17f * density, rowTop + rowHeight / 2f);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density);
        paint.setColor(COLOR_BORDER);
        rect.set(x, top, x + column.width, top + headerHeight + count * rowHeight);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawStar(Canvas canvas, float cx, float cy, boolean filled) {
        iconPaint.setShader(null);
        iconPaint.setStyle(Paint.Style.FILL);
        iconPaint.setColor(filled ? COLOR_STAR_ON : COLOR_STAR);
        buildStar(cx, cy, 6.5f * density, 2.9f * density);
        canvas.drawPath(starPath, iconPaint);
    }

    private void buildStar(float cx, float cy, float outer, float inner) {
        starPath.reset();
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            float r = (i % 2 == 0) ? outer : inner;
            float px = cx + (float) Math.cos(angle) * r;
            float py = cy + (float) Math.sin(angle) * r;
            if (i == 0) starPath.moveTo(px, py);
            else starPath.lineTo(px, py);
        }
        starPath.close();
    }

    private void drawGear(Canvas canvas, float cx, float cy) {
        iconPaint.setShader(null);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(1.3f * density);
        iconPaint.setColor(COLOR_GEAR);
        float outer = 4.6f * density;
        float inner = 2.0f * density;
        canvas.drawCircle(cx, cy, inner, iconPaint);
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            canvas.drawLine(cx + cos * outer * 0.75f, cy + sin * outer * 0.75f,
                cx + cos * outer, cy + sin * outer, iconPaint);
        }
    }

    private String ellipsize(String text, float maxWidth) {
        if (text == null) return "";
        if (textPaint.measureText(text) <= maxWidth) return text;
        String ellipsis = "...";
        int length = text.length();
        while (length > 0 && textPaint.measureText(text, 0, length) + textPaint.measureText(ellipsis) > maxWidth) {
            length--;
        }
        return text.substring(0, length) + ellipsis;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                dragDx = 0f;
                dragDy = 0f;
                downScrollX = scrollX;
                downScrollY = scrollY;
                downTime = SystemClock.uptimeMillis();
                dragging = false;
                longPressFired = false;
                pendingHit = hitTest(downX, downY);
                if (pendingHit != null && pendingHit.mod != null) {
                    handler.postDelayed(longPressRunnable, longPressTimeout);
                }
                return true;
            case MotionEvent.ACTION_MOVE: {
                float x = event.getX();
                float y = event.getY();
                dragDx += x - lastX;
                dragDy += y - lastY;
                lastX = x;
                lastY = y;
                if (!dragging && Math.hypot(dragDx, dragDy) > touchSlop) {
                    dragging = true;
                    handler.removeCallbacks(longPressRunnable);
                }
                if (dragging) {
                    scrollX = downScrollX - dragDx;
                    scrollY = downScrollY - dragDy;
                    clampScroll();
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPressRunnable);
                if (!dragging && !longPressFired
                        && SystemClock.uptimeMillis() - downTime < longPressTimeout) {
                    handleTap(event.getX(), event.getY());
                }
                pendingHit = null;
                return true;
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPressRunnable);
                pendingHit = null;
                return true;
            default:
                return true;
        }
    }

    private void handleTap(float x, float y) {
        Hit hit = hitTest(x, y);
        if (hit == null || hit.mod == null) return;
        float iconSpace = 58f * density;
        float iconStart = hit.column.x + hit.column.width - iconSpace;
        if (x + scrollX >= iconStart) {
            float starCenter = hit.column.x + hit.column.width - 42f * density;
            float gearCenter = hit.column.x + hit.column.width - 17f * density;
            float contentX = x + scrollX;
            float contentY = y + scrollY;
            if (Math.abs(contentX - starCenter) <= 12f * density
                    && Math.abs(contentY - hit.rowCenterY) <= 12f * density) {
                boolean favorite = !hit.row.favorite;
                hit.row.favorite = favorite;
                if (listener != null) listener.onFavorite(hit.mod, favorite);
                invalidate();
                return;
            }
            if (Math.abs(contentX - gearCenter) <= 12f * density
                    && Math.abs(contentY - hit.rowCenterY) <= 12f * density) {
                if (listener != null) listener.onConfig(hit.mod);
                return;
            }
        }
        boolean enabled = !hit.mod.isEnabled();
        hit.mod.applyEnabled(enabled);
        if (listener != null) listener.onToggle(hit.mod, enabled);
        invalidate();
    }

    private Hit hitTest(float x, float y) {
        float contentX = x + scrollX;
        float localY = (y + scrollY) - padding - headerHeight;
        if (localY < 0) return null;
        int index = (int) (localY / rowHeight);
        for (Column column : columns) {
            if (contentX < column.x || contentX > column.x + column.width) continue;
            if (index < 0 || index >= column.rows.size()) return null;
            Hit hit = new Hit();
            hit.column = column;
            hit.row = column.rows.get(index);
            hit.mod = hit.row.mod;
            hit.rowCenterY = padding + headerHeight + index * rowHeight + rowHeight / 2f;
            return hit;
        }
        return null;
    }

    private static final class Column {
        final String name;
        final List<Row> rows = new ArrayList<>();
        float x;
        float width;
        float height;

        Column(String name) {
            this.name = name;
        }
    }

    private static final class Row {
        final UnifiedMod mod;
        boolean favorite;

        Row(UnifiedMod mod, boolean favorite) {
            this.mod = mod;
            this.favorite = favorite;
        }
    }

    private static final class Hit {
        Column column;
        Row row;
        UnifiedMod mod;
        float rowCenterY;
    }
}
