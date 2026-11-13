package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;
import org.levimc.launcher.core.mods.inbuilt.nativemod.EspMod;
import org.levimc.launcher.preloader.PreloaderInput;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class NameTagsOverlay extends View {
    private static final String[] MATERIALS = {
            "", "leather", "chainmail", "iron", "gold", "diamond",
            "netherite", "copper", "turtle", "other"
    };
    private static final String[] PIECES = {
            "helmet", "chestplate", "leggings", "boots"
    };
    private static final int FRIEND_COLOR = 0xFF55FF66;
    private static final long POLL_INTERVAL_NANOS = 33_000_000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Bitmap[][] armorIcons = new Bitmap[MATERIALS.length][PIECES.length];
    private final Map<String, Bitmap> itemIcons = new HashMap<>();
    private final RectF iconRect = new RectF();
    private final RectF haloRect = new RectF();
    private final RectF panelRect = new RectF();
    private final ColorFilter leatherFilter =
            new PorterDuffColorFilter(0xFFA0643C, PorterDuff.Mode.MULTIPLY);
    private final ColorFilter otherFilter =
            new PorterDuffColorFilter(0xFF8B8F98, PorterDuff.Mode.MULTIPLY);
    private final ColorFilter shadowFilter =
            new PorterDuffColorFilter(0xC8000000, PorterDuff.Mode.SRC_IN);
    private float density = 1f;
    private int nameColor = 0xFF4E83FF;
    private int nameSize = 11;
    private int armorSize = 15;
    private int nameBlur = 4;
    private int nameBlurColor = 0xFF101018;
    private int nameOutlineColor = 0xFF0A0A12;
    private int nameTagStyle;
    private boolean showArmor = true;
    private boolean showOffhand = true;
    private boolean attached;
    private volatile boolean showing;
    private volatile boolean moduleEnabled = true;
    private Label[] labels = new Label[0];
    private String[] lastRawLabels = new String[0];
    private boolean frameCallbackPosted;
    private int showAttempts;
    private long lastPollNanos;

    private static final class Label {
        float x;
        float y;
        String name = "";
        String offhand = "";
        int armorMask;
        boolean friend;
    }

    private final Runnable showRunnable = new Runnable() {
        @Override
        public void run() {
            if (!showing || attached) return;
            if (!(getContext() instanceof Activity)) {
                showing = false;
                return;
            }
            Activity activity = (Activity) getContext();
            if (activity.isFinishing() || activity.isDestroyed()) {
                showing = false;
                return;
            }
            try {
                ViewGroup root = activity.findViewById(android.R.id.content);
                if (root == null || !root.isAttachedToWindow()) {
                    if (++showAttempts < 20) mainHandler.postDelayed(this, 250L);
                    else showing = false;
                    return;
                }
                if (getParent() != null) return;
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT);
                root.addView(NameTagsOverlay.this, params);
            } catch (RuntimeException ignored) {
                showing = false;
            }
        }
    };

    private boolean paused;

    private final Runnable resumeRunnable = this::postFrameCallback;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            frameCallbackPosted = false;
            if (!attached) return;
            if (!gameplayVisible()) {
                if (!paused) {
                    paused = true;
                    labels = new Label[0];
                    lastRawLabels = new String[0];
                    invalidate();
                }
                mainHandler.removeCallbacks(resumeRunnable);
                mainHandler.postDelayed(resumeRunnable, 300L);
                return;
            }
            paused = false;
            if (frameTimeNanos - lastPollNanos >= POLL_INTERVAL_NANOS) {
                lastPollNanos = frameTimeNanos;
                pollLabels();
            }
            postFrameCallback();
        }
    };

    public NameTagsOverlay(Activity activity) {
        super(activity);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setElevation(90f);
        density = activity.getResources().getDisplayMetrics().density;
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        iconPaint.setFilterBitmap(true);
        iconPaint.setDither(true);
        haloPaint.setFilterBitmap(true);
        haloPaint.setDither(true);
        panelPaint.setStyle(Paint.Style.FILL);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(1.2f));
        loadConfig();
    }

    public void applyConfigurationChanges() {
        loadConfig();
        lastRawLabels = new String[]{};
        pollLabels();
        invalidate();
    }

    private void loadConfig() {
        InbuiltModManager manager = InbuiltModManager.getInstance(getContext());
        nameColor = manager.getNameTagColor();
        nameSize = manager.getNameTagSize();
        armorSize = manager.getNameTagArmorSize();
        nameBlur = 0;
        nameOutlineColor = manager.getNameTagOutlineColor();
        nameTagStyle = manager.getNameTagStyle();
        showArmor = manager.isNameTagShowArmor();
        showOffhand = manager.isNameTagShowOffhand();
    }

    public void show() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::show);
            return;
        }
        if (showing) return;
        showing = true;
        showAttempts = 0;
        mainHandler.postDelayed(showRunnable, 100L);
    }

    public void hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::hide);
            return;
        }
        showing = false;
        attached = false;
        mainHandler.removeCallbacks(showRunnable);
        mainHandler.removeCallbacks(resumeRunnable);
        try {
            Choreographer.getInstance().removeFrameCallback(frameCallback);
        } catch (RuntimeException ignored) {
        }
        frameCallbackPosted = false;
        EspMod.setNameTags(false);
        try {
            if (getParent() instanceof ViewGroup) {
                ((ViewGroup) getParent()).removeView(this);
            }
        } catch (RuntimeException ignored) {
        }
    }

    public void setModuleEnabled(boolean enabled) {
        if (moduleEnabled == enabled) return;
        moduleEnabled = enabled;
        EspMod.setNameTags(enabled);
        if (enabled) {
            if (attached) postFrameCallback();
            return;
        }
        Runnable clear = () -> {
            labels = new Label[0];
            lastRawLabels = new String[0];
            invalidate();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) clear.run();
        else mainHandler.post(clear);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        showing = true;
        loadConfig();
        InbuiltModManager manager = InbuiltModManager.getInstance(getContext());
        moduleEnabled = manager.isNameTagsEnabled();
        EspMod.initializeAsync(ok -> mainHandler.post(() -> {
            if (!attached || !ok) return;
            EspMod.setNameTags(moduleEnabled);
            updateSurfaceSize(getWidth(), getHeight());
            postFrameCallback();
        }));
        updateSurfaceSize(getWidth(), getHeight());
        postFrameCallback();
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        frameCallbackPosted = false;
        mainHandler.removeCallbacks(showRunnable);
        mainHandler.removeCallbacks(resumeRunnable);
        try {
            Choreographer.getInstance().removeFrameCallback(frameCallback);
        } catch (RuntimeException ignored) {
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateSurfaceSize(w, h);
    }

    private void updateSurfaceSize(int w, int h) {
        if (w > 0 && h > 0) {
            EspMod.setNameTagScreenSize(w, h);
        }
    }

    private void postFrameCallback() {
        if (frameCallbackPosted || !attached) return;
        try {
            frameCallbackPosted = true;
            Choreographer.getInstance().postFrameCallback(frameCallback);
        } catch (RuntimeException ignored) {
            frameCallbackPosted = false;
        }
    }

    private boolean gameplayVisible() {
        boolean hudOpen = PreloaderInput.isHudScreenOpen();
        boolean pauseOpen = PreloaderInput.isPauseMenuOpen();
        boolean menuOpen = PreloaderInput.isShowingMenu();
        InbuiltOverlayManager manager = InbuiltOverlayManager.getInstance();
        boolean modMenu = manager != null && manager.isModMenuShowing();
        return hudOpen && !pauseOpen && !menuOpen && !modMenu;
    }

    private void pollLabels() {
        String[] next = new String[0];
        if (moduleEnabled && gameplayVisible() && EspMod.isInitialized()) {
            try {
                String[] nativeLabels = EspMod.getNameTags();
                if (nativeLabels != null) next = nativeLabels;
            } catch (Throwable ignored) {
                next = new String[0];
            }
        }
        if (!Arrays.equals(lastRawLabels, next)) {
            lastRawLabels = next;
            labels = parseLabels(next);
            invalidate();
        }
    }

    private Label[] parseLabels(String[] raw) {
        ArrayList<Label> parsed = new ArrayList<>(raw.length);
        for (String entry : raw) {
            if (entry == null) continue;
            String[] parts = entry.split("\t", -1);
            if (parts.length < 3) continue;
            try {
                Label label = new Label();
                label.x = Float.parseFloat(parts[0]);
                label.y = Float.parseFloat(parts[1]);
                if (parts.length >= 9) {
                    label.armorMask = (int) Long.parseLong(parts[5].trim(), 16);
                    label.friend = "1".equals(parts[6].trim());
                    label.offhand = parts[7];
                    label.name = parts[8];
                } else if (parts.length >= 7) {
                    label.armorMask = (int) Long.parseLong(parts[5].trim(), 16);
                    label.name = parts[6];
                } else if (parts.length >= 6) {
                    label.name = parts[5];
                } else if (parts.length >= 5) {
                    label.name = parts[4];
                } else {
                    label.name = parts[3];
                }
                if (label.name.isEmpty()) continue;
                parsed.add(label);
            } catch (NumberFormatException ignored) {
            }
        }
        return parsed.toArray(new Label[0]);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!moduleEnabled || labels.length == 0) return;
        try {
            applyTextStyle();
            for (Label label : labels) {
                try {
                    drawLabel(canvas, label);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void applyTextStyle() {
        textPaint.setTextSize(nameSize * density);
        textPaint.clearShadowLayer();
    }

    private void drawLabel(Canvas canvas, Label label) {
        final float iconSize = Math.max(8, armorSize) * density;
        final float iconGap = 1.3f * density;
        final float nameWidth = textPaint.measureText(label.name);
        final int iconCount = countArmorIcons(label.armorMask);
        final Bitmap item = showOffhand ? itemIcon(label.offhand) : null;
        final float baseline = label.y;
        final float centerY = baseline + (textPaint.ascent() + textPaint.descent()) * 0.5f;

        // Иконки брони — сверху, по центру над ником.
        if (iconCount > 0) {
            final float iconsWidth = iconCount * iconSize + (iconCount - 1) * iconGap;
            final float ix = label.x - iconsWidth * 0.5f;
            final float iy = baseline + textPaint.ascent() - dp(4f) - iconSize;
            drawArmorRow(canvas, label.armorMask, iconCount, ix, iy,
                    iconSize, iconGap);
        }

        // Ник — ровно по центру цели, с аккуратной обводкой.
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setStyle(Paint.Style.STROKE);
        textPaint.setStrokeWidth(Math.max(2f, nameSize * 0.16f) * density);
        textPaint.setStrokeJoin(Paint.Join.ROUND);
        textPaint.setColor(nameOutlineColor);
        canvas.drawText(label.name, label.x, baseline, textPaint);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(label.friend ? FRIEND_COLOR : nameColor);
        canvas.drawText(label.name, label.x, baseline, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);

        // Офф-хенд предмет — слева от ника.
        if (item != null) {
            final float ix = label.x - nameWidth * 0.5f - dp(3f) - iconSize;
            iconRect.set(ix, centerY - iconSize * 0.5f,
                    ix + iconSize, centerY + iconSize * 0.5f);
            drawIcon(canvas, item, iconRect, null);
        }
    }


    private void drawPanel(Canvas canvas, float minX, float minY,
                           float maxX, float maxY, boolean friend) {
        // Фон name tag полностью прозрачный — виден только ник и иконки.
    }

    private void drawBrackets(Canvas canvas, RectF rect, int accent) {
        borderPaint.setColor(accent);
        borderPaint.setStrokeWidth(dp(1.6f));
        float length = Math.min(dp(10f), rect.width() * 0.22f);
        canvas.drawLine(rect.left, rect.top, rect.left + length, rect.top, borderPaint);
        canvas.drawLine(rect.left, rect.top, rect.left, rect.top + length, borderPaint);
        canvas.drawLine(rect.right, rect.top, rect.right - length, rect.top, borderPaint);
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + length, borderPaint);
        canvas.drawLine(rect.left, rect.bottom, rect.left + length, rect.bottom, borderPaint);
        canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - length, borderPaint);
        canvas.drawLine(rect.right, rect.bottom, rect.right - length, rect.bottom, borderPaint);
        canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - length, borderPaint);
    }

    private int countArmorIcons(int mask) {
        int count = 0;
        for (int slot = 0; slot < 4; slot++) {
            if (((mask >>> (slot * 8)) & 0xFF) != 0) count++;
        }
        return count;
    }

    private void drawArmorRow(Canvas canvas, int mask, int count, float cursor,
                              float top, float iconSize, float gap) {
        if (mask != 0) {
            for (int slot = 0; slot < 4; slot++) {
                int material = (mask >>> (slot * 8)) & 0xFF;
                if (material == 0) continue;
                Bitmap icon = armorIcon(material, slot);
                if (icon != null) {
                    iconRect.set(cursor, top, cursor + iconSize, top + iconSize);
                    drawIcon(canvas, icon, iconRect,
                            material == 1 ? leatherFilter
                                    : material == 9 ? otherFilter : null);
                }
                cursor += iconSize + gap;
            }
            return;
        }
        for (int i = 0; i < count; i++) {
            Bitmap icon = armorIcon(3, i);
            if (icon != null) {
                iconRect.set(cursor, top, cursor + iconSize, top + iconSize);
                drawIcon(canvas, icon, iconRect, otherFilter);
            }
            cursor += iconSize + gap;
        }
    }

    private void drawIcon(Canvas canvas, Bitmap bitmap, RectF dst,
                          ColorFilter filter) {
        if (nameBlur > 0) {
            float radius = Math.max(1f, nameBlur * 0.32f) * density;
            haloPaint.setColorFilter(shadowFilter);
            haloPaint.setAlpha(170);
            for (int i = 0; i < 4; i++) {
                float dx = i == 0 ? -radius : i == 1 ? radius : 0f;
                float dy = i == 2 ? -radius : i == 3 ? radius : 0f;
                haloRect.set(dst);
                haloRect.offset(dx, dy);
                canvas.drawBitmap(bitmap, null, haloRect, haloPaint);
            }
            haloPaint.setAlpha(255);
            haloPaint.setColorFilter(null);
        }
        iconPaint.setColorFilter(filter);
        canvas.drawBitmap(bitmap, null, dst, iconPaint);
        iconPaint.setColorFilter(null);
    }

    private Bitmap itemIcon(String raw) {
        String key = itemKey(raw);
        if (key.isEmpty()) return null;
        if (itemIcons.containsKey(key)) return itemIcons.get(key);
        Bitmap bitmap = null;
        try {
            for (String candidate : itemCandidates(key)) {
                int resource = getResources().getIdentifier(
                        "item_" + candidate, "drawable", getContext().getPackageName());
                if (resource != 0) {
                    bitmap = BitmapFactory.decodeResource(getResources(), resource);
                    break;
                }
            }
        } catch (RuntimeException | OutOfMemoryError ignored) {
            bitmap = null;
        }
        itemIcons.put(key, bitmap);
        return bitmap;
    }

    private String itemKey(String raw) {
        if (raw == null) return "";
        String key = raw.trim().toLowerCase(Locale.US).replace(' ', '_');
        int colon = key.indexOf(':');
        if (colon >= 0) key = key.substring(colon + 1);
        return key;
    }

    private String[] itemCandidates(String key) {
        ArrayList<String> out = new ArrayList<>();
        out.add(key);
        if (key.endsWith("_bucket")) {
            out.add("bucket_" + key.substring(0, key.length() - 7));
        }
        if (key.endsWith("_boat")) {
            out.add("boat_" + key.substring(0, key.length() - 5));
        }
        if (key.startsWith("wooden_")) {
            out.add("wood_" + key.substring(7));
        }
        if (key.startsWith("golden_")) {
            out.add("gold_" + key.substring(7));
        }
        if (key.startsWith("cooked_")) {
            out.add(key.substring(7) + "_cooked");
        }
        if (key.startsWith("raw_")) {
            out.add(key.substring(4) + "_raw");
        }
        if (key.equals("totem_of_undying")) {
            out.add("totem");
        }
        if (key.equals("enchanted_book")) {
            out.add("book_enchanted");
        }
        if (key.equals("writable_book")) {
            out.add("book_writable");
        }
        if (key.equals("written_book")) {
            out.add("book_written");
        }
        if (key.equals("potion")) {
            out.add("potion_bottle_drinkable");
        }
        if (key.equals("splash_potion")) {
            out.add("potion_bottle_splash");
        }
        if (key.equals("lingering_potion")) {
            out.add("potion_bottle_lingering");
        }
        if (key.equals("golden_apple")) {
            out.add("apple_golden");
        }
        if (key.equals("enchanted_golden_apple")) {
            out.add("apple_golden");
        }
        if (key.startsWith("music_disc_")) {
            out.add("record_" + key.substring(11));
        }
        if (key.endsWith("_spawn_egg")) {
            out.add("spawn_egg");
        }
        return out.toArray(new String[0]);
    }

    private Bitmap armorIcon(int material, int slot) {
        if (material <= 0 || material >= MATERIALS.length ||
                slot < 0 || slot >= PIECES.length) {
            return null;
        }
        Bitmap cached = armorIcons[material][slot];
        if (cached != null) return cached;
        Bitmap bitmap = null;
        try {
            int resource = getResources().getIdentifier(
                    "armor_" + MATERIALS[material] + "_" + PIECES[slot],
                    "drawable", getContext().getPackageName());
            if (resource == 0 && material == 9) {
                resource = getResources().getIdentifier(
                        "armor_iron_" + PIECES[slot], "drawable",
                        getContext().getPackageName());
            }
            if (resource == 0 && material == 8) {
                resource = getResources().getIdentifier(
                        "armor_iron_" + PIECES[slot], "drawable",
                        getContext().getPackageName());
            }
            if (resource != 0) {
                bitmap = BitmapFactory.decodeResource(getResources(), resource);
                armorIcons[material][slot] = bitmap;
            }
        } catch (RuntimeException | OutOfMemoryError ignored) {
            bitmap = null;
        }
        return bitmap;
    }

    private float dp(float value) {
        return value * density;
    }
}
