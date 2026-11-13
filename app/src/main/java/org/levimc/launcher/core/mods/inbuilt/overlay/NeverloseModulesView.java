package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import org.levimc.launcher.R;
import org.levimc.launcher.core.mods.inbuilt.UnifiedMod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Neverlose-style module board. Every module is placed in a category card and
 * all cards live on one compact canvas; there are no full-screen category
 * pages. Cards are balanced between two columns and the whole board scrolls
 * vertically when it does not fit.
 */
public class NeverloseModulesView extends View {

    public interface Listener {
        void onToggle(UnifiedMod mod, boolean enabled);
        void onConfig(UnifiedMod mod, float anchorX, float anchorY);
    }

    private static final int COLOR_CARD = 0xE011131B;
    private static final int COLOR_BORDER = 0xFF20232E;
    private static final int COLOR_SEPARATOR = 0xFF1C1F28;
    private static final int COLOR_TEXT = 0xFFD9DBE2;
    private static final int COLOR_TEXT_DIM = 0xFF9296A2;
    private static final int COLOR_TEXT_FAINT = 0xFF5E6370;
    private static final int COLOR_ACCENT = 0xFF4E83FF;
    private static final int COLOR_TRACK_OFF = 0xFF1D212B;
    private static final int COLOR_TRACK_ON = 0xFF4B7EFF;
    private static final int COLOR_KNOB_OFF = 0xFF85909C;
    private static final int COLOR_KNOB_ON = 0xFFF8F9FC;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    private final float density;
    private final float scaledDensity;
    private final float rowHeight;
    private final float cardRadius;
    private final float columnGap;
    private final float boardPadding;
    private final float titleHeight;
    private final float cardGap;
    private final int touchSlop;

    private final List<Column> columns = new ArrayList<>();
    private final Map<String, Float> toggleMotion = new HashMap<>();
    private final Map<String, Float> arrowMotion = new HashMap<>();
    private Listener listener;
    private Runnable onScrollListener;

    private float contentWidth;
    private float contentHeight;
    private float scrollY;
    private float maxScroll;

    private String expandedKey;

    private float downX;
    private float downY;
    private float lastY;
    private float dragDy;
    private boolean dragging;
    private Hit pendingHit;

    private Typeface uiTypeface;
    private Typeface displayTypeface;

    private static final class Row {
        final UnifiedMod mod;
        Row(UnifiedMod mod) { this.mod = mod; }
    }

    private static final class Column {
        final String title;
        final List<Row> rows = new ArrayList<>();
        float x;
        float y;
        float width;
        float height;
        Column(String title) { this.title = title; }
    }

    private static final class Hit {
        final UnifiedMod mod;
        final boolean config;
        final RectF bounds;
        final float anchorX;
        final float anchorY;
        Hit(UnifiedMod mod, boolean config, RectF bounds,
            float anchorX, float anchorY) {
            this.mod = mod;
            this.config = config;
            this.bounds = bounds;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
        }
    }

    public NeverloseModulesView(Context context) {
        this(context, null);
    }

    public NeverloseModulesView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        rowHeight = 37f * density;
        cardRadius = 13f * density;
        columnGap = 10f * density;
        boardPadding = 12f * density;
        titleHeight = 18f * density;
        cardGap = 12f * density;

        try {
            uiTypeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.inter_medium);
            displayTypeface = androidx.core.content.res.ResourcesCompat.getFont(
                    context, R.font.space_grotesk_semibold);
        } catch (Exception ignored) {
            uiTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);
            displayTypeface = Typeface.DEFAULT_BOLD;
        }
        textPaint.setTypeface(uiTypeface);
        dotPaint.setColor(COLOR_TEXT_DIM);
        setClickable(true);
        setFocusable(false);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setExpandedKey(String key) {
        if (expandedKey == null ? key == null : expandedKey.equals(key)) return;
        expandedKey = key;
        postInvalidateOnAnimation();
    }

    public void setOnScrollListener(Runnable listener) {
        onScrollListener = listener;
    }

    public void setContent(List<UnifiedMod> mods, Set<String> favoriteKeys) {
        Map<String, Column> byTitle = new LinkedHashMap<>();
        if (mods != null) {
            for (UnifiedMod mod : mods) {
                if (mod == null) continue;
                String title = categoryTitle(mod);
                Column column = byTitle.get(title);
                if (column == null) {
                    column = new Column(title);
                    byTitle.put(title, column);
                }
                column.rows.add(new Row(mod));
            }
        }

        columns.clear();
        List<Column> ordered = new ArrayList<>(byTitle.values());
        ordered.sort((a, b) -> Integer.compare(categoryOrder(a.title), categoryOrder(b.title)));
        columns.addAll(ordered);
        recomputeLayout(ordered);
        clampScroll();
        invalidate();
    }

    private int categoryOrder(String title) {
        if (title == null) return 99;
        String upper = title.toUpperCase(Locale.ROOT);
        if (upper.contains("COMBAT")) return 0;
        if (upper.contains("VISUAL")) return 1;
        if (upper.contains("MOVEMENT")) return 2;
        if (upper.contains("MISC")) return 3;
        if (upper.contains("EXTERNAL")) return 4;
        return 5;
    }

    private String categoryTitle(UnifiedMod mod) {
        if (mod.getSource() == UnifiedMod.Source.EXTERNAL) {
            String group = mod.getGroupName();
            return group == null || group.trim().isEmpty()
                    ? "EXTERNAL" : group.toUpperCase(Locale.ROOT);
        }
        String id = mod.getId() == null ? "" : mod.getId().toLowerCase(Locale.ROOT);
        switch (id) {
            case "aim_assist":
            case "trigger_bot":
            case "target_esp":
            case "swing":
            case "friend":
                return "COMBAT";
            case "esp":
            case "name_tags":
            case "fullbright":
            case "toggle_hud":
            case "fps_display":
            case "cps_display":
                return "VISUALS";
            case "speed":
            case "auto_sprint":
            case "zoom":
            case "snaplook":
            case "gyro":
            case "virtual_cursor":
            case "pojav_controls":
                return "MOVEMENT";
            default:
                return "MISCELLANEOUS";
        }
    }

    private void recomputeLayout(List<Column> groups) {
        float width = getWidth() > 0 ? getWidth() : 640f * density;
        float available = Math.max(1f, width - boardPadding * 2f);
        float colWidth = (available - columnGap) * 0.5f;

        float[] columnY = new float[]{boardPadding, boardPadding};
        for (Column group : groups) {
            group.width = colWidth;
            group.height = group.rows.size() * rowHeight;
            int index = columnY[0] <= columnY[1] ? 0 : 1;
            group.x = boardPadding + index * (colWidth + columnGap);
            group.y = columnY[index] + titleHeight;
            columnY[index] += titleHeight + group.height + cardGap;
        }
        contentWidth = width;
        contentHeight = Math.max(columnY[0], columnY[1]) + boardPadding;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recomputeLayout(columns);
        clampScroll();
    }

    private void clampScroll() {
        maxScroll = Math.max(0f, contentHeight - getHeight());
        if (scrollY < 0f) scrollY = 0f;
        if (scrollY > maxScroll) scrollY = maxScroll;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                lastY = downY;
                dragDy = 0f;
                dragging = false;
                pendingHit = hitTest(downX, downY + scrollY);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dy = event.getY() - lastY;
                lastY = event.getY();
                dragDy += dy;
                if (!dragging && Math.abs(event.getY() - downY) > touchSlop) {
                    dragging = true;
                    pendingHit = null;
                    if (onScrollListener != null) onScrollListener.run();
                }
                if (dragging) {
                    scrollY -= dy;
                    clampScroll();
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (!dragging && pendingHit != null) {
                    Hit hit = pendingHit;
                    if (hit.config) {
                        if (listener != null) {
                            listener.onConfig(hit.mod, hit.anchorX, hit.anchorY);
                        }
                    } else {
                        boolean enabled = !hit.mod.isEnabled();
                        hit.mod.applyEnabled(enabled);
                        if (listener != null) listener.onToggle(hit.mod, enabled);
                    }
                }
                pendingHit = null;
                dragging = false;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pendingHit = null;
                dragging = false;
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private Hit hitTest(float x, float y) {
        for (Column column : columns) {
            if (x < column.x || x > column.x + column.width) continue;
            float top = column.y;
            if (y < top || y > top + column.height) continue;
            int rowIndex = (int) ((y - top) / rowHeight);
            if (rowIndex < 0 || rowIndex >= column.rows.size()) return null;
            Row row = column.rows.get(rowIndex);
            float rowTop = top + rowIndex * rowHeight;
            float configCenter = column.x + column.width - 65f * density;
            boolean config = Math.abs(x - configCenter) <= 13f * density;
            return new Hit(row.mod, config, new RectF(
                    column.x, rowTop, column.x + column.width, rowTop + rowHeight),
                    configCenter, rowTop + rowHeight);
        }
        return null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        boolean animating = false;
        for (Column column : columns) {
            float top = column.y - scrollY;
            if (top + column.height < -cardRadius || top > getHeight() + cardRadius) continue;
            drawCard(canvas, column, top);
            for (int i = 0; i < column.rows.size(); i++) {
                Row row = column.rows.get(i);
                float rowTop = top + i * rowHeight;
                if (rowTop + rowHeight < 0 || rowTop > getHeight()) continue;
                drawRow(canvas, column, row, rowTop, i);
                float target = row.mod.isEnabled() ? 1f : 0f;
                float current = toggleMotion.containsKey(row.mod.getStableKey())
                        ? toggleMotion.get(row.mod.getStableKey()) : target;
                float next = current + (target - current) * 0.32f;
                if (Math.abs(next - target) < 0.01f) next = target;
                else animating = true;
                toggleMotion.put(row.mod.getStableKey(), next);

                float arrowTarget = expandedKey != null
                        && expandedKey.equals(row.mod.getStableKey()) ? 1f : 0f;
                float arrowCurrent = arrowMotion.containsKey(row.mod.getStableKey())
                        ? arrowMotion.get(row.mod.getStableKey()) : arrowTarget;
                float arrowNext = arrowCurrent + (arrowTarget - arrowCurrent) * 0.28f;
                if (Math.abs(arrowNext - arrowTarget) < 0.01f) arrowNext = arrowTarget;
                else animating = true;
                arrowMotion.put(row.mod.getStableKey(), arrowNext);
            }
        }
        if (animating) postInvalidateOnAnimation();
    }

    private void drawCard(Canvas canvas, Column column, float top) {
        textPaint.setTypeface(displayTypeface);
        textPaint.setTextSize(10f * scaledDensity);
        textPaint.setLetterSpacing(0.12f);
        textPaint.setColor(COLOR_TEXT_FAINT);
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(column.title == null ? "" : column.title,
                column.x + 1f * density, top - 6f * density, textPaint);
        textPaint.setLetterSpacing(0f);

        rect.set(column.x, top, column.x + column.width, top + column.height);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_CARD);
        canvas.drawRoundRect(rect, cardRadius, cardRadius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * density);
        paint.setColor(COLOR_BORDER);
        canvas.drawRoundRect(rect, cardRadius, cardRadius, paint);
    }

    private void drawRow(Canvas canvas, Column column, Row row, float top, int index) {
        if (index > 0) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f * density);
            paint.setColor(COLOR_SEPARATOR);
            canvas.drawLine(column.x + 12f * density, top,
                    column.x + column.width - 12f * density, top, paint);
        }

        textPaint.setTypeface(uiTypeface);
        textPaint.setTextSize(15f * scaledDensity);
        textPaint.setColor(COLOR_TEXT);
        textPaint.setTextAlign(Paint.Align.LEFT);
        float textY = top + (rowHeight - (textPaint.descent() + textPaint.ascent())) * 0.5f;
        String name = row.mod.getName() == null ? "" : row.mod.getName();
        float maxTextWidth = column.width - 96f * density;
        if (textPaint.measureText(name) > maxTextWidth) {
            name = ellipsize(name, maxTextWidth);
        }
        canvas.drawText(name, column.x + 13f * density, textY, textPaint);

        float arrowX = column.x + column.width - 65f * density;
        float arrowY = top + rowHeight * 0.5f;
        float arrowT = arrowMotion.containsKey(row.mod.getStableKey())
                ? arrowMotion.get(row.mod.getStableKey()) : 0f;
        canvas.save();
        canvas.rotate(arrowT * 180f, arrowX, arrowY);
        path.reset();
        path.moveTo(arrowX - 4f * density, arrowY - 1.7f * density);
        path.lineTo(arrowX, arrowY + 2.3f * density);
        path.lineTo(arrowX + 4f * density, arrowY - 1.7f * density);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.7f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(blend(COLOR_TEXT_DIM, COLOR_ACCENT, arrowT));
        canvas.drawPath(path, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeJoin(Paint.Join.MITER);
        canvas.restore();

        float motion = toggleMotion.containsKey(row.mod.getStableKey())
                ? toggleMotion.get(row.mod.getStableKey())
                : (row.mod.isEnabled() ? 1f : 0f);
        float toggleLeft = column.x + column.width - 43f * density;
        float toggleTop = top + 9f * density;
        float toggleWidth = 29f * density;
        float toggleHeight = 18f * density;

        rect.set(toggleLeft, toggleTop, toggleLeft + toggleWidth, toggleTop + toggleHeight);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blend(COLOR_TRACK_OFF, COLOR_TRACK_ON, motion));
        canvas.drawRoundRect(rect, toggleHeight * 0.5f, toggleHeight * 0.5f, paint);

        float knobRadius = 7f * density;
        float knobX = toggleLeft + knobRadius + 2f * density
                + (toggleWidth - knobRadius * 2f - 4f * density) * motion;
        paint.setColor(blend(COLOR_KNOB_OFF, COLOR_KNOB_ON, motion));
        canvas.drawCircle(knobX, toggleTop + toggleHeight * 0.5f, knobRadius, paint);
    }

    private String ellipsize(String value, float maxWidth) {
        String ellipsis = "…";
        if (textPaint.measureText(value) <= maxWidth) return value;
        int end = value.length();
        while (end > 0 && textPaint.measureText(value.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return value.substring(0, end) + ellipsis;
    }

    private int blend(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) ((from >>> 24) + ((to >>> 24) - (from >>> 24)) * t);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
