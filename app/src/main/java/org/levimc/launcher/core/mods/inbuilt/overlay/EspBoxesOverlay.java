package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;
import org.levimc.launcher.core.mods.inbuilt.nativemod.EspMod;
import org.levimc.launcher.preloader.PreloaderInput;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Java-side ESP box models. Native still computes the projected screen
 * rectangles; this overlay renders the selected 2D model (box, corners, glow
 * or filled) and lets native draw only the original 3D variant.
 */
public class EspBoxesOverlay extends View {
    private static final long POLL_INTERVAL_NANOS = 33_000_000L;
    private static final Pattern TAB = Pattern.compile("\t");
    private static final String[] EMPTY = new String[0];

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float density = 1f;
    private boolean attached;
    private volatile boolean showing;
    private volatile boolean moduleEnabled = true;
    private int style = 0;
    private String[] boxes = EMPTY;
    private boolean framePosted;
    private long lastPollNanos;
    private float[] boxX = new float[0];
    private float[] boxY = new float[0];
    private float[] boxW = new float[0];
    private float[] boxH = new float[0];
    private int[] boxColor = new int[0];
    private int[] boxArmor = new int[0];
    private int boxCount;
    private boolean paused;

    private final Runnable resumeRunnable = () -> {
        if (attached) postFrame();
    };

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            framePosted = false;
            if (!attached) return;
            if (!gameplayVisible()) {
                if (!paused) {
                    paused = true;
                    boxes = EMPTY;
                    boxCount = 0;
                    invalidate();
                }
                mainHandler.removeCallbacks(resumeRunnable);
                mainHandler.postDelayed(resumeRunnable, 300L);
                return;
            }
            paused = false;
            if (frameTimeNanos - lastPollNanos >= POLL_INTERVAL_NANOS) {
                lastPollNanos = frameTimeNanos;
                poll();
            }
            postFrame();
        }
    };

    public EspBoxesOverlay(Activity activity) {
        super(activity);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setElevation(94f);
        density = activity.getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.6f * density);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(6f * density);
        pipPaint.setStyle(Paint.Style.FILL);
        applySettings();
    }

    private void applySettings() {
        style = InbuiltModManager.getInstance(getContext()).getEspBoxStyle();
        EspMod.setEspBoxStyle(style);
    }

    public void applyConfigurationChanges() {
        applySettings();
        invalidate();
    }

    public void show() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::show);
            return;
        }
        if (showing) return;
        showing = true;
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!showing || getParent() != null) return;
                try {
                    ViewGroup root = ((Activity) getContext())
                            .findViewById(android.R.id.content);
                    if (root == null) return;
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT);
                    root.addView(EspBoxesOverlay.this, params);
                } catch (Exception ignored) {
                }
            }
        }, 100);
    }

    public void hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::hide);
            return;
        }
        showing = false;
        attached = false;
        mainHandler.removeCallbacks(resumeRunnable);
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        framePosted = false;
        boxes = EMPTY;
        boxCount = 0;
        try {
            if (getParent() instanceof ViewGroup) {
                ((ViewGroup) getParent()).removeView(this);
            }
        } catch (Exception ignored) {
        }
    }

    public void setModuleEnabled(boolean enabled) {
        moduleEnabled = enabled;
        if (enabled) return;
        Runnable clear = () -> {
            boxes = EMPTY;
            boxCount = 0;
            invalidate();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) clear.run();
        else mainHandler.post(clear);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        applySettings();
        EspMod.initializeAsync(ok -> {
            if (!attached || !ok) return;
            EspMod.setEspBoxStyle(style);
        });
        updateSurfaceSize(getWidth(), getHeight());
        postFrame();
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        framePosted = false;
        mainHandler.removeCallbacks(resumeRunnable);
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateSurfaceSize(w, h);
    }

    private void updateSurfaceSize(int w, int h) {
        if (w > 0 && h > 0) EspMod.setNameTagScreenSize(w, h);
    }

    private void postFrame() {
        if (framePosted || !attached) return;
        framePosted = true;
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    private boolean gameplayVisible() {
        boolean hudOpen = PreloaderInput.isHudScreenOpen();
        boolean pauseOpen = PreloaderInput.isPauseMenuOpen();
        boolean menuOpen = PreloaderInput.isShowingMenu();
        InbuiltOverlayManager manager = InbuiltOverlayManager.getInstance();
        boolean modMenu = manager != null && manager.isModMenuShowing();
        return hudOpen && !pauseOpen && !menuOpen && !modMenu;
    }

    private void poll() {
        String[] next = EMPTY;
        try {
            if (moduleEnabled && gameplayVisible() && EspMod.isInitialized()) {
                String[] nativeBoxes = EspMod.getEspBoxes();
                if (nativeBoxes != null) next = nativeBoxes;
            }
        } catch (Throwable error) {
            next = EMPTY;
        }
        if (Arrays.equals(boxes, next)) return;
        boxes = next;
        parseBoxes(next);
        invalidate();
    }

    private void parseBoxes(String[] raw) {
        final int n = raw.length;
        if (boxX.length < n) {
            boxX = new float[n];
            boxY = new float[n];
            boxW = new float[n];
            boxH = new float[n];
            boxColor = new int[n];
            boxArmor = new int[n];
        }
        int count = 0;
        for (String entry : raw) {
            if (entry == null) continue;
            String[] parts = TAB.split(entry);
            if (parts.length < 5) continue;
            try {
                boxX[count] = Float.parseFloat(parts[0]);
                boxY[count] = Float.parseFloat(parts[1]);
                boxW[count] = Float.parseFloat(parts[2]);
                boxH[count] = Float.parseFloat(parts[3]);
                boxColor[count] = (int) Long.parseLong(parts[4].trim());
                boxArmor[count] = 0;
                if (parts.length >= 6) {
                    try {
                        boxArmor[count] = Integer.parseInt(parts[5].trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
                count++;
            } catch (NumberFormatException ignored) {
            }
        }
        boxCount = count;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!moduleEnabled || style <= 0 || boxCount == 0) return;
        try {
            for (int i = 0; i < boxCount; i++) {
                drawBox(canvas, boxX[i], boxY[i], boxW[i], boxH[i], boxColor[i]);
                if (boxArmor[i] > 0) {
                    drawArmorPips(canvas, boxX[i], boxY[i], boxW[i], boxArmor[i]);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void drawArmorPips(Canvas canvas, float x, float y, float w,
                               int armorPieces) {
        float pipW = 4f * density;
        float pipH = 6f * density;
        float gap = 1.5f * density;
        float total = 4 * pipW + 3 * gap;
        float left = x + (w - total) * 0.5f;
        float top = y - pipH - 4f * density;
        for (int i = 0; i < 4; i++) {
            pipPaint.setColor(i < armorPieces ? 0xFF58A6FF : 0xFF303643);
            float px = left + i * (pipW + gap);
            canvas.drawRoundRect(px, top, px + pipW, top + pipH,
                    density, density, pipPaint);
        }
    }

    private void drawBox(Canvas canvas, float x, float y, float w, float h, int color) {
        rect.set(x, y, x + w, y + h);
        switch (style) {
            case 1:
                paint.setColor(color);
                paint.setStrokeWidth(1.6f * density);
                canvas.drawRoundRect(rect, 1.5f * density, 1.5f * density, paint);
                break;
            case 2: {
                float arm = Math.min(Math.min(w, h) * 0.24f, 18f * density);
                paint.setColor(color);
                paint.setStrokeWidth(1.8f * density);
                canvas.drawLine(rect.left, rect.top, rect.left + arm, rect.top, paint);
                canvas.drawLine(rect.left, rect.top, rect.left, rect.top + arm, paint);
                canvas.drawLine(rect.right, rect.top, rect.right - arm, rect.top, paint);
                canvas.drawLine(rect.right, rect.top, rect.right, rect.top + arm, paint);
                canvas.drawLine(rect.left, rect.bottom, rect.left + arm, rect.bottom, paint);
                canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - arm, paint);
                canvas.drawLine(rect.right, rect.bottom, rect.right - arm, rect.bottom, paint);
                canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - arm, paint);
                break;
            }
            case 3:
                glowPaint.setColor((color & 0x00FFFFFF) | 0x55000000);
                glowPaint.setStrokeWidth(6f * density);
                canvas.drawRoundRect(rect, 2f * density, 2f * density, glowPaint);
                paint.setColor(color);
                paint.setStrokeWidth(1.4f * density);
                canvas.drawRoundRect(rect, 2f * density, 2f * density, paint);
                break;
            case 4:
                paint.setStyle(Paint.Style.FILL);
                paint.setColor((color & 0x00FFFFFF) | 0x33000000);
                canvas.drawRoundRect(rect, 1.5f * density, 1.5f * density, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(color);
                paint.setStrokeWidth(1.5f * density);
                canvas.drawRoundRect(rect, 1.5f * density, 1.5f * density, paint);
                break;
            default:
                break;
        }
    }
}
