package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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

/**
 * Target ESP. Три модели, которые отдаёт native как экранный бокс + полный
 * набор для 3D→2D проекции (камера, базис, AABB цели):
 *   0 — угловые скобки (2D billboard, неон)
 *   1 — статичные орбитальные кольца (3D, синий/голубой, частицы)
 *   2 — сканирующее кольцо с вертикальной аурой (3D, анимация вверх-вниз)
 */
public class TargetEspOverlay extends View {
    public static final int STYLE_BRACKETS = 0;
    public static final int STYLE_ORBITAL = 1;
    public static final int STYLE_SCAN = 2;

    private static final long FADE_MS = 180L;
    private static final long POLL_INTERVAL_NANOS = 33_000_000L;
    private static final int RING_SEGMENTS = 56;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float[] sp = new float[2];

    private float density = 1f;
    private boolean attached;
    private volatile boolean showing;
    private volatile boolean moduleEnabled = true;
    private int style = STYLE_BRACKETS;
    private int accent = 0xFF4E83FF;
    private long holdMs = 650L;
    private float x, y, w, h;
    private long ageMs = -1;
    private boolean framePosted;
    private long lastPollNanos;
    private boolean paused;

    private float camX, camY, camZ;
    private float minX, minY, minZ, maxX, maxY, maxZ;
    private float fx, fy, fz, rx, rz, ux, uy, uz, focal;
    private float screenW, screenH;

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
                    ageMs = -1;
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
            if (ageMs >= 0) invalidate();
            postFrame();
        }
    };

    public TargetEspOverlay(Activity activity) {
        super(activity);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setElevation(95f);
        density = activity.getResources().getDisplayMetrics().density;
        corePaint.setStyle(Paint.Style.STROKE);
        corePaint.setStrokeCap(Paint.Cap.ROUND);
        corePaint.setStrokeJoin(Paint.Join.ROUND);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStrokeJoin(Paint.Join.ROUND);
        dotPaint.setStyle(Paint.Style.FILL);
        applySettings();
    }

    private void applySettings() {
        InbuiltModManager manager = InbuiltModManager.getInstance(getContext());
        style = Math.max(0, Math.min(STYLE_SCAN, manager.getTargetEspStyle()));
        accent = manager.getTargetEspColor();
        holdMs = Math.max(150, manager.getTargetEspHoldMs());
        EspMod.setTargetEspHold((int) holdMs);
    }

    public void show() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::show);
            return;
        }
        if (showing) return;
        showing = true;
        applySettings();
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
                    root.addView(TargetEspOverlay.this, params);
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
        EspMod.setTargetEsp(false);
        ageMs = -1;
        try {
            if (getParent() instanceof ViewGroup) {
                ((ViewGroup) getParent()).removeView(this);
            }
        } catch (Exception ignored) {
        }
    }

    public void setModuleEnabled(boolean enabled) {
        if (moduleEnabled == enabled) return;
        moduleEnabled = enabled;
        EspMod.setTargetEsp(enabled);
        if (enabled) return;
        Runnable clear = () -> {
            ageMs = -1;
            invalidate();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) clear.run();
        else mainHandler.post(clear);
    }

    public void applyConfigurationChanges() {
        applySettings();
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        InbuiltModManager manager = InbuiltModManager.getInstance(getContext());
        moduleEnabled = manager.isTargetEspEnabled();
        applySettings();
        EspMod.initializeAsync(ok -> {
            if (!attached || !ok) return;
            EspMod.setTargetEspHold((int) holdMs);
            EspMod.setTargetEsp(moduleEnabled);
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
    protected void onSizeChanged(int nw, int nh, int oldw, int oldh) {
        super.onSizeChanged(nw, nh, oldw, oldh);
        updateSurfaceSize(nw, nh);
    }

    private void updateSurfaceSize(int nw, int nh) {
        if (nw > 0 && nh > 0) EspMod.setNameTagScreenSize(nw, nh);
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
        String raw = null;
        try {
            if (moduleEnabled && gameplayVisible() && EspMod.isInitialized()) {
                raw = EspMod.getTargetEsp();
            }
        } catch (Throwable ignored) {
            raw = null;
        }
        if (raw == null || raw.isEmpty()) {
            if (ageMs >= 0) ageMs = -1;
            return;
        }
        try {
            String[] p = raw.split("\t");
            if (p.length >= 26) {
                x = Float.parseFloat(p[0]);
                y = Float.parseFloat(p[1]);
                w = Float.parseFloat(p[2]);
                h = Float.parseFloat(p[3]);
                ageMs = Long.parseLong(p[4]);
                camX = Float.parseFloat(p[6]);
                camY = Float.parseFloat(p[7]);
                camZ = Float.parseFloat(p[8]);
                minX = Float.parseFloat(p[9]);
                minY = Float.parseFloat(p[10]);
                minZ = Float.parseFloat(p[11]);
                maxX = Float.parseFloat(p[12]);
                maxY = Float.parseFloat(p[13]);
                maxZ = Float.parseFloat(p[14]);
                fx = Float.parseFloat(p[15]);
                fy = Float.parseFloat(p[16]);
                fz = Float.parseFloat(p[17]);
                rx = Float.parseFloat(p[18]);
                rz = Float.parseFloat(p[19]);
                ux = Float.parseFloat(p[20]);
                uy = Float.parseFloat(p[21]);
                uz = Float.parseFloat(p[22]);
                focal = Float.parseFloat(p[23]);
                screenW = Float.parseFloat(p[24]);
                screenH = Float.parseFloat(p[25]);
            } else if (p.length >= 5) {
                x = Float.parseFloat(p[0]);
                y = Float.parseFloat(p[1]);
                w = Float.parseFloat(p[2]);
                h = Float.parseFloat(p[3]);
                ageMs = Long.parseLong(p[4]);
                screenW = getWidth();
                screenH = getHeight();
            }
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!moduleEnabled || ageMs < 0 || w <= 0 || h <= 0) return;

        float appear = Math.min(1f, ageMs / 110f);
        float fadeStart = Math.max(0L, holdMs - FADE_MS);
        float alpha = ageMs <= fadeStart
                ? 1f
                : Math.max(0f, 1f - (ageMs - fadeStart) / (float) FADE_MS);
        if (alpha <= 0.01f) return;

        float cx = x + w * 0.5f;
        float cy = y + h * 0.5f;
        float scale = 0.82f + 0.18f * appear;
        float halfW = w * 0.5f * scale;
        float halfH = h * 0.5f * scale;
        rect.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH);

        switch (style) {
            case STYLE_ORBITAL:
                drawOrbital(canvas, alpha);
                break;
            case STYLE_SCAN:
                drawScan(canvas, alpha);
                break;
            case STYLE_BRACKETS:
            default:
                drawBrackets(canvas, alpha);
                break;
        }
    }

    // -------------------------------------------------------------- brackets
    private void drawBrackets(Canvas canvas, float alpha) {
        final float pad = 6f * density;
        final float arm = Math.min(rect.width(), rect.height()) * 0.30f;
        final float l = rect.left - pad;
        final float r = rect.right + pad;
        final float t = rect.top - pad;
        final float b = rect.bottom + pad;
        int core = withAlpha(accent, 255 * alpha);
        int glow = withAlpha(accent, 105 * alpha);
        int trail = withAlpha(accent, 45 * alpha);
        bracketPass(canvas, l, t, r, b, arm * 1.6f, 8f * density, trail);
        bracketPass(canvas, l, t, r, b, arm, 6f * density, glow);
        bracketPass(canvas, l, t, r, b, arm, 2.4f * density, core);
    }

    private void bracketPass(Canvas canvas, float l, float t, float r, float b,
                             float arm, float width, int color) {
        corePaint.setColor(color);
        corePaint.setStrokeWidth(width);
        canvas.drawLine(l, t, l + arm, t, corePaint);
        canvas.drawLine(l, t, l, t + arm, corePaint);
        canvas.drawLine(r, t, r - arm, t, corePaint);
        canvas.drawLine(r, t, r, t + arm, corePaint);
        canvas.drawLine(l, b, l + arm, b, corePaint);
        canvas.drawLine(l, b, l, b - arm, corePaint);
        canvas.drawLine(r, b, r - arm, b, corePaint);
        canvas.drawLine(r, b, r, b - arm, corePaint);
    }

    // --------------------------------------------------------------- orbital
    private void drawOrbital(Canvas canvas, float alpha) {
        final float ccx = (minX + maxX) * 0.5f;
        final float ccy = (minY + maxY) * 0.5f + (maxY - minY) * 0.16f;
        final float ccz = (minZ + maxZ) * 0.5f;
        final float radius = Math.max(maxX - minX, maxZ - minZ) * 0.5f + 0.16f;

        float[][] rings = {
                {0f, 0f},
                {35f, 0f},
                {0f, 35f},
        };
        int glow = withAlpha(accent, 75 * alpha);
        int core = withAlpha(accent, 235 * alpha);
        int white = withAlpha(0xFFFFFFFF, 245 * alpha);
        int particleA = withAlpha(accent, 220 * alpha);
        int particleB = withAlpha(0xFFFFD54F, 220 * alpha);

        for (int ringIndex = 0; ringIndex < rings.length; ringIndex++) {
            float ax = (float) Math.toRadians(rings[ringIndex][0]);
            float az = (float) Math.toRadians(rings[ringIndex][1]);
            glowPaint.setColor(glow);
            glowPaint.setStrokeWidth(6f * density);
            corePaint.setColor(core);
            corePaint.setStrokeWidth(2f * density);
            drawRing(canvas, ccx, ccy, ccz, radius, ax, az, glowPaint);
            drawRing(canvas, ccx, ccy, ccz, radius, ax, az, corePaint);
            for (int i = 0; i < 5; i++) {
                double t = (i / 5.0) * Math.PI * 2.0;
                if (!ringPoint(ccx, ccy, ccz, radius, ax, az, t, sp)) continue;
                dotPaint.setColor(i % 2 == 0 ? particleA : particleB);
                canvas.drawCircle(sp[0], sp[1], 2.2f * density, dotPaint);
            }
        }
        // white highlight at the "poles"
        if (project(ccx, ccy - radius, ccz, sp)) {
            dotPaint.setColor(white);
            canvas.drawCircle(sp[0], sp[1], 3f * density, dotPaint);
        }
        if (project(ccx, ccy + radius, ccz, sp)) {
            dotPaint.setColor(white);
            canvas.drawCircle(sp[0], sp[1], 3f * density, dotPaint);
        }
    }

    private void drawRing(Canvas canvas, float ccx, float ccy, float ccz,
                          float radius, float ax, float az, Paint paint) {
        path.reset();
        boolean started = false;
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double t = (i / (double) RING_SEGMENTS) * Math.PI * 2.0;
            if (!ringPoint(ccx, ccy, ccz, radius, ax, az, t, sp)) {
                started = false;
                continue;
            }
            if (!started) {
                path.moveTo(sp[0], sp[1]);
                started = true;
            } else {
                path.lineTo(sp[0], sp[1]);
            }
        }
        canvas.drawPath(path, paint);
    }

    private boolean ringPoint(float ccx, float ccy, float ccz, float radius,
                              float ax, float az, double t, float[] out) {
        float cx = (float) Math.cos(t);
        float sy = (float) Math.sin(t);
        float sinX = (float) Math.sin(ax);
        float cosX = (float) Math.cos(ax);
        float sinZ = (float) Math.sin(az);
        float cosZ = (float) Math.cos(az);
        // start in the XZ plane, then tilt around X and Z
        float px = cx * cosZ;
        float py = -cx * sinZ;
        float pz = sy * cosX;
        float py2 = py + sy * sinX;
        return project(ccx + px * radius, ccy + py2 * radius, ccz + pz * radius, out);
    }

    // ------------------------------------------------------------------ scan
    private void drawScan(Canvas canvas, float alpha) {
        final float ccx = (minX + maxX) * 0.5f;
        final float ccz = (minZ + maxZ) * 0.5f;
        final float height = Math.max(0.2f, maxY - minY);
        final float radius = Math.max(maxX - minX, maxZ - minZ) * 0.5f + 0.18f;

        float speed = 1.7f;
        float phase = (float) ((Math.sin(ageMs * 0.001f * speed) + 1.0) * 0.5);
        float ringY = minY + height * (0.06f + 0.88f * phase);

        int coreWhite = withAlpha(0xFFFFFFFF, 255 * alpha);
        int edgeCyan = withAlpha(accent, 210 * alpha);
        int glow = withAlpha(accent, 85 * alpha);
        int trail = withAlpha(accent, 60 * alpha);

        // vertical translucent aura above the ring
        glowPaint.setColor(trail);
        glowPaint.setStrokeWidth(2f * density);
        for (int i = 0; i < 16; i++) {
            double t = (i / 16.0) * Math.PI * 2.0;
            float px = ccx + (float) Math.cos(t) * radius;
            float pz = ccz + (float) Math.sin(t) * radius;
            if (project(px, ringY, pz, sp)) {
                float sx0 = sp[0];
                float sy0 = sp[1];
                if (project(px, maxY, pz, sp)) {
                    canvas.drawLine(sx0, sy0, sp[0], sp[1], glowPaint);
                }
            }
        }
        // the ring itself
        glowPaint.setColor(glow);
        glowPaint.setStrokeWidth(9f * density);
        drawRing(canvas, ccx, ringY, ccz, radius, 0f, 0f, glowPaint);
        corePaint.setStrokeWidth(3.2f * density);
        corePaint.setColor(edgeCyan);
        drawRing(canvas, ccx, ringY, ccz, radius, 0f, 0f, corePaint);
        corePaint.setStrokeWidth(1.4f * density);
        corePaint.setColor(coreWhite);
        drawRing(canvas, ccx, ringY, ccz, radius * 0.985f, 0f, 0f, corePaint);
    }

    private static int withAlpha(int argb, float a) {
        int alpha = Math.max(0, Math.min(255, Math.round(a)));
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }

    private boolean project(float wx, float wy, float wz, float[] out) {
        if (focal <= 0f || screenW <= 0f || screenH <= 0f) return false;
        float dx = wx - camX;
        float dy = wy - camY;
        float dz = wz - camZ;
        float depth = dx * fx + dy * fy + dz * fz;
        if (depth <= 0.05f) return false;
        float right = dx * rx + dz * rz;
        float up = dx * ux + dy * uy + dz * uz;
        out[0] = screenW * 0.5f + (right / depth) * focal;
        out[1] = screenH * 0.5f - (up / depth) * focal;
        return true;
    }
}
