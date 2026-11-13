package org.levimc.launcher.core.mods.inbuilt.overlay;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.levimc.launcher.R;

import java.util.Locale;

/** Desktop-cheat style HSV palette: saturation/value field, hue, alpha and blur bars. */
public final class ColorPalette {
    public interface Listener {
        void onColorChanged(int color);
    }

    public interface BlurListener {
        void onBlurChanged(int blur);
    }

    private ColorPalette() {}

    public static View create(Context context, int initial, boolean withAlpha, Listener listener) {
        return create(context, initial, withAlpha, false, 0, 20, listener, null);
    }

    public static View create(Context context, int initial, boolean withAlpha,
                              boolean withBlur, int initialBlur, int maxBlur,
                              Listener listener, BlurListener blurListener) {
        PickerView picker = new PickerView(context, initial, withAlpha, withBlur,
                initialBlur, maxBlur, listener, blurListener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        picker.setLayoutParams(params);
        return picker;
    }

    private static final class PickerView extends View {
        private static final int REGION_NONE = 0;
        private static final int REGION_SV = 1;
        private static final int REGION_HUE = 2;
        private static final int REGION_ALPHA = 3;
        private static final int REGION_BLUR = 4;

        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF svRect = new RectF();
        private final RectF hueRect = new RectF();
        private final RectF alphaRect = new RectF();
        private final RectF blurRect = new RectF();
        private final RectF tabRect = new RectF();
        private final RectF hexRect = new RectF();
        private final float density;

        private final boolean withAlpha;
        private final boolean withBlur;
        private final Listener listener;
        private final BlurListener blurListener;
        private final int maxBlur;
        private final float[] hsv = new float[]{0f, 0f, 1f};
        private int alpha;
        private int blur;
        private int color;
        private int secondary;
        private int activeRegion = REGION_NONE;

        PickerView(Context context, int initial, boolean withAlpha, boolean withBlur,
                   int initialBlur, int maxBlur, Listener listener,
                   BlurListener blurListener) {
            super(context);
            this.withAlpha = withAlpha;
            this.withBlur = withBlur;
            this.listener = listener != null ? listener : value -> {};
            this.blurListener = blurListener != null ? blurListener : value -> {};
            this.maxBlur = Math.max(1, maxBlur);
            this.blur = Math.max(0, Math.min(this.maxBlur, initialBlur));
            this.density = context.getResources().getDisplayMetrics().density;
            Color.colorToHSV(initial, hsv);
            alpha = Color.alpha(initial);
            secondary = Color.HSVToColor(alpha,
                    new float[]{(hsv[0] + 180f) % 360f, 0.72f, Math.max(0.55f, hsv[2])});
            color = initial;
            stroke.setStyle(Paint.Style.STROKE);
            text.setColor(0xFFD9DBE2);
            text.setTextSize(12f * context.getResources().getDisplayMetrics().scaledDensity);
            text.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                    android.graphics.Typeface.NORMAL));
            small.setColor(0xFF9296A2);
            small.setTextSize(9f * context.getResources().getDisplayMetrics().scaledDensity);
            small.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                    android.graphics.Typeface.NORMAL));
            setClickable(true);
        }

        private float dp(float value) {
            return value * density;
        }

        private float squareSize(int width) {
            float pad = dp(8);
            float available = Math.max(dp(96), width - pad * 2f);
            return Math.min(dp(188), available);
        }

        private float contentHeight(int width) {
            float pad = dp(8);
            float y = pad + dp(34);
            y += squareSize(width);
            y += dp(14) + dp(10);
            if (withAlpha) y += dp(12) + dp(10);
            if (withBlur) y += dp(12) + dp(10);
            y += dp(14) + dp(30) + pad;
            return y;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (width <= 0) width = (int) dp(300);
            setMeasuredDimension(width, (int) contentHeight(width));
        }

        private void layoutRects() {
            float pad = dp(8);
            float top = pad + dp(34);
            float size = squareSize(getWidth());
            float left = pad + Math.max(0f, (getWidth() - pad * 2f - size) * 0.5f);
            svRect.set(left, top, left + size, top + size);

            float y = svRect.bottom + dp(14);
            hueRect.set(pad, y, getWidth() - pad, y + dp(10));
            y = hueRect.bottom + dp(12);
            if (withAlpha) {
                alphaRect.set(pad, y, getWidth() - pad, y + dp(10));
                y = alphaRect.bottom + dp(12);
            } else {
                alphaRect.set(0, 0, 0, 0);
            }
            if (withBlur) {
                blurRect.set(pad, y, getWidth() - pad, y + dp(10));
                y = blurRect.bottom;
            } else {
                blurRect.set(0, 0, 0, 0);
                y = withAlpha ? alphaRect.bottom : hueRect.bottom;
            }
            float hexTop = y + dp(14);
            hexRect.set(pad, hexTop, getWidth() - pad, hexTop + dp(30));
            tabRect.set(pad, dp(6), pad + dp(120), dp(6) + dp(26));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            layoutRects();
            float pad = dp(10);

            drawTabs(canvas, pad);
            drawSv(canvas);
            drawHue(canvas);
            if (withAlpha) drawAlpha(canvas);
            if (withBlur) drawBlur(canvas);
            drawHex(canvas);
        }

        private void drawTabs(Canvas canvas, float pad) {
            fill.setStyle(Paint.Style.FILL);
            fill.setShader(null);
            fill.setColor(0xFF181B25);
            canvas.drawRoundRect(tabRect, dp(7), dp(7), fill);
            stroke.setStrokeWidth(dp(1));
            stroke.setColor(0xFF272B36);
            canvas.drawRoundRect(tabRect, dp(7), dp(7), stroke);

            float sw = dp(14);
            float sx = tabRect.left + dp(7);
            float sy = tabRect.centerY() - sw * 0.5f;
            fill.setColor(color);
            canvas.drawRoundRect(sx, sy, sx + sw, sy + sw, dp(4), dp(4), fill);
            stroke.setStrokeWidth(dp(1));
            stroke.setColor(0x55FFFFFF);
            canvas.drawRoundRect(sx, sy, sx + sw, sy + sw, dp(4), dp(4), stroke);

            text.setColor(0xFFD9DBE2);
            text.setTextAlign(Paint.Align.LEFT);
            float baseline = tabRect.centerY() - (text.descent() + text.ascent()) * 0.5f;
            canvas.drawText(getContext().getString(R.string.color_palette_primary),
                    sx + sw + dp(8), baseline, text);

            float radius = dp(10);
            float cx = tabRect.right + dp(18);
            fill.setColor(secondary);
            canvas.drawCircle(cx, tabRect.centerY(), radius, fill);
            stroke.setStrokeWidth(dp(1));
            stroke.setColor(0x33FFFFFF);
            canvas.drawCircle(cx, tabRect.centerY(), radius, stroke);

            small.setTextAlign(Paint.Align.LEFT);
            float labelX = cx + radius + dp(6);
            if (labelX + small.measureText(
                    getContext().getString(R.string.color_palette_secondary)) < getWidth() - pad) {
                float sb = tabRect.centerY() - (small.descent() + small.ascent()) * 0.5f;
                canvas.drawText(getContext().getString(R.string.color_palette_secondary),
                        labelX, sb, small);
            }
        }

        private void drawSv(Canvas canvas) {
            int hueColor = Color.HSVToColor(new float[]{hsv[0], 1f, 1f});
            fill.setShader(new LinearGradient(svRect.left, 0f, svRect.right, 0f,
                    Color.WHITE, hueColor, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(svRect, dp(8), dp(8), fill);
            fill.setShader(new LinearGradient(0f, svRect.top, 0f, svRect.bottom,
                    Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(svRect, dp(8), dp(8), fill);
            fill.setShader(null);

            float mx = svRect.left + hsv[1] * svRect.width();
            float my = svRect.bottom - hsv[2] * svRect.height();
            stroke.setStrokeWidth(dp(2.2f));
            stroke.setColor(0xFF000000);
            canvas.drawCircle(mx, my, dp(7), stroke);
            stroke.setStrokeWidth(dp(1.4f));
            stroke.setColor(0xFFFFFFFF);
            canvas.drawCircle(mx, my, dp(7), stroke);
        }

        private void drawHue(Canvas canvas) {
            int[] colors = new int[]{
                    0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF,
                    0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};
            fill.setShader(new LinearGradient(hueRect.left, 0f, hueRect.right, 0f,
                    colors, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(hueRect, hueRect.height() * 0.5f,
                    hueRect.height() * 0.5f, fill);
            fill.setShader(null);
            drawBarThumb(canvas, hueRect, hsv[0] / 360f);
        }

        private void drawAlpha(Canvas canvas) {
            float cell = dp(5);
            for (float x = alphaRect.left; x < alphaRect.right; x += cell) {
                for (float y = alphaRect.top; y < alphaRect.bottom; y += cell) {
                    boolean light = ((int) ((x - alphaRect.left) / cell)
                            + (int) ((y - alphaRect.top) / cell)) % 2 == 0;
                    fill.setColor(light ? 0xFF3A3F4B : 0xFF232833);
                    canvas.drawRect(x, y, Math.min(x + cell, alphaRect.right),
                            Math.min(y + cell, alphaRect.bottom), fill);
                }
            }
            int solid = Color.HSVToColor(255, hsv);
            int clear = Color.HSVToColor(0, hsv);
            fill.setShader(new LinearGradient(alphaRect.left, 0f, alphaRect.right, 0f,
                    clear, solid, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(alphaRect, alphaRect.height() * 0.5f,
                    alphaRect.height() * 0.5f, fill);
            fill.setShader(null);
            drawBarThumb(canvas, alphaRect, alpha / 255f);
        }

        private void drawBlur(Canvas canvas) {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0xFF181B25);
            canvas.drawRoundRect(blurRect, blurRect.height() * 0.5f,
                    blurRect.height() * 0.5f, fill);

            int solid = Color.HSVToColor(Math.max(48, alpha), hsv);
            fill.setShader(new LinearGradient(blurRect.left, 0f, blurRect.right, 0f,
                    0x331A1D27, solid, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(blurRect, blurRect.height() * 0.5f,
                    blurRect.height() * 0.5f, fill);
            fill.setShader(null);

            small.setColor(0xDDFFFFFF);
            small.setTextAlign(Paint.Align.LEFT);
            float baseline = blurRect.centerY() - (small.descent() + small.ascent()) * 0.5f;
            canvas.drawText(getContext().getString(R.string.color_palette_blur),
                    blurRect.left + dp(8), baseline, small);
            drawBarThumb(canvas, blurRect, blur / (float) maxBlur);
        }

        private void drawBarThumb(Canvas canvas, RectF rect, float fraction) {
            float cx = rect.left + Math.max(0f, Math.min(1f, fraction)) * rect.width();
            float cy = rect.centerY();
            stroke.setStrokeWidth(dp(2));
            stroke.setColor(0xFF000000);
            canvas.drawCircle(cx, cy, dp(7), stroke);
            stroke.setStrokeWidth(dp(1.4f));
            stroke.setColor(0xFFFFFFFF);
            canvas.drawCircle(cx, cy, dp(7), stroke);
        }

        private void drawHex(Canvas canvas) {
            small.setColor(0xFF5E6370);
            small.setTextAlign(Paint.Align.LEFT);
            float baseline = hexRect.centerY() - (small.descent() + small.ascent()) * 0.5f;
            canvas.drawText(getContext().getString(R.string.color_palette_hex),
                    hexRect.left + dp(2), baseline, small);

            String value = String.format(Locale.US, "#%08X", color);
            float pad = dp(10);
            text.setColor(0xFFD9DBE2);
            text.setTextAlign(Paint.Align.LEFT);
            float w = text.measureText(value) + pad * 2f;
            float left = hexRect.right - w;
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0xFF181B25);
            float top = hexRect.top + dp(3);
            canvas.drawRoundRect(left, top, hexRect.right, hexRect.bottom - dp(3),
                    dp(6), dp(6), fill);
            fill.setColor(color);
            canvas.drawRoundRect(left + dp(5), top + dp(5), left + dp(15),
                    hexRect.bottom - dp(8), dp(3), dp(3), fill);
            float tb = hexRect.centerY() - (text.descent() + text.ascent()) * 0.5f;
            canvas.drawText(value, left + dp(20), tb, text);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (tabRect.contains(event.getX(), event.getY())) {
                        if (event.getX() > tabRect.right + dp(4)) {
                            int old = color;
                            color = secondary;
                            secondary = old;
                            Color.colorToHSV(color, hsv);
                            alpha = Color.alpha(color);
                            invalidate();
                            listener.onColorChanged(color);
                        }
                        return true;
                    }
                    if (hexRect.contains(event.getX(), event.getY())) {
                        showHexDialog();
                        return true;
                    }
                    activeRegion = regionAt(event.getX(), event.getY());
                    if (activeRegion != REGION_NONE) {
                        updateFromTouch(event.getX(), event.getY());
                        return true;
                    }
                    return super.onTouchEvent(event);
                case MotionEvent.ACTION_MOVE:
                    if (activeRegion != REGION_NONE) {
                        updateFromTouch(event.getX(), event.getY());
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (activeRegion != REGION_NONE) {
                        activeRegion = REGION_NONE;
                        return true;
                    }
                    break;
            }
            return super.onTouchEvent(event);
        }

        private int regionAt(float x, float y) {
            if (svRect.contains(x, y)) return REGION_SV;
            if (hueRect.contains(x, y)) return REGION_HUE;
            if (withAlpha && alphaRect.contains(x, y)) return REGION_ALPHA;
            if (withBlur && blurRect.contains(x, y)) return REGION_BLUR;
            return REGION_NONE;
        }

        private void updateFromTouch(float x, float y) {
            if (activeRegion == REGION_BLUR) {
                int next = Math.round(clamp01((x - blurRect.left)
                        / Math.max(1f, blurRect.width())) * maxBlur);
                if (next != blur) {
                    blur = next;
                    invalidate();
                    blurListener.onBlurChanged(blur);
                }
                return;
            }
            if (activeRegion == REGION_SV) {
                hsv[1] = clamp01((x - svRect.left) / Math.max(1f, svRect.width()));
                hsv[2] = 1f - clamp01((y - svRect.top) / Math.max(1f, svRect.height()));
            } else if (activeRegion == REGION_HUE) {
                hsv[0] = clamp01((x - hueRect.left) / Math.max(1f, hueRect.width())) * 360f;
                if (hsv[0] >= 360f) hsv[0] = 359.9f;
            } else if (activeRegion == REGION_ALPHA) {
                alpha = Math.round(clamp01((x - alphaRect.left)
                        / Math.max(1f, alphaRect.width())) * 255f);
            }
            color = Color.HSVToColor(alpha, hsv);
            invalidate();
            listener.onColorChanged(color);
        }

        private float clamp01(float value) {
            return Math.max(0f, Math.min(1f, value));
        }

        private void showHexDialog() {
            EditText input = new EditText(getContext());
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            input.setSingleLine(true);
            input.setText(String.format(Locale.US, "#%08X", color));
            input.setSelection(input.getText().length());
            input.setGravity(Gravity.CENTER_VERTICAL);

            AlertDialog dialog = new AlertDialog.Builder(getContext())
                    .setTitle(R.string.color_palette_hex)
                    .setView(input)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, (d, which) -> {
                        String raw = input.getText().toString().trim();
                        if (!raw.startsWith("#")) raw = "#" + raw;
                        try {
                            int parsed;
                            if (raw.length() == 9) {
                                parsed = Color.parseColor(raw);
                            } else if (raw.length() == 7) {
                                parsed = Color.parseColor(raw) | 0xFF000000;
                            } else {
                                return;
                            }
                            color = parsed;
                            alpha = Color.alpha(color);
                            Color.colorToHSV(color, hsv);
                            invalidate();
                            listener.onColorChanged(color);
                        } catch (IllegalArgumentException ignored) {
                        }
                    })
                    .create();
            dialog.setOnShowListener(d -> {
                input.requestFocus();
                InputMethodManager imm = (InputMethodManager) getContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            });
            dialog.show();
        }
    }
}
