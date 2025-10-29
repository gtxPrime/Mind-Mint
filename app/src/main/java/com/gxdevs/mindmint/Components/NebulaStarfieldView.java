
package com.gxdevs.mindmint.Components;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * NebulaStarfieldView
 * <p>
 * A mesmerizing galaxy background — twinkling stars + drifting nebula clouds.
 * Perfect for focus, meditation, or any immersive space UI.
 */
public class NebulaStarfieldView extends View {

    private static final int DEFAULT_STAR_COUNT = 120;

    private static final float DEFAULT_MIN_STAR_SIZE = 1.2f;
    private static final float DEFAULT_MAX_STAR_SIZE = 3.5f;
    private static final float DEFAULT_DRIFT_RANGE = 0.25f;

    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nebulaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();

    private final List<Star> stars = new ArrayList<>();

    private int starCount = DEFAULT_STAR_COUNT;
    private float minStarSize = DEFAULT_MIN_STAR_SIZE;
    private float maxStarSize = DEFAULT_MAX_STAR_SIZE;
    private float driftRange = DEFAULT_DRIFT_RANGE;
    private int bgColor = Color.rgb(255, 255, 255);
    private Context context;

    // If non-null, overrides theme detection: true = dark, false = light
    private Boolean themeOverrideDark = null;

    public NebulaStarfieldView(Context context) {
        super(context);
        init();
    }

    public NebulaStarfieldView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NebulaStarfieldView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        starPaint.setStyle(Paint.Style.FILL);
        nebulaPaint.setStyle(Paint.Style.FILL);
    }

    private void createStars() {
        stars.clear();
        for (int i = 0; i < starCount; i++) {
            stars.add(randomStar());
        }
    }

    private Star randomStar() {
        Star s = new Star();
        s.baseX = random.nextFloat() * getWidth();
        s.baseY = random.nextFloat() * getHeight();
        s.size = minStarSize + random.nextFloat() * (maxStarSize - minStarSize);
        s.alpha = 100 + random.nextInt(155);
        s.twinkleSpeed = 0.01f + random.nextFloat() * 0.02f;
        s.movePhase = random.nextFloat() * (float) Math.PI * 2f;
        s.twinklePhase = random.nextFloat() * (float) Math.PI * 2f;
        s.colorShift = random.nextBoolean();
        return s;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        createStars();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(bgColor);
        boolean isDark;
        if (themeOverrideDark != null) {
            isDark = themeOverrideDark;
        } else {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String mode = prefs.getString("pref_theme_mode", "Dark Theme");
            if ("Dark Theme".equalsIgnoreCase(mode) || "dark".equalsIgnoreCase(mode)) {
                isDark = true;
            } else if ("Light Theme".equalsIgnoreCase(mode) || "light".equalsIgnoreCase(mode)) {
                isDark = false;
            } else {
                int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                isDark = (nightModeFlags == Configuration.UI_MODE_NIGHT_YES);
            }
        }

        for (Star s : stars) {
            float driftX = (float) Math.sin(s.movePhase) * driftRange * 20f;
            float driftY = (float) Math.cos(s.movePhase) * driftRange * 20f;
            float twinkle = (float) Math.sin(s.twinklePhase) * 0.5f + 0.5f;

            int alpha = (int) (s.alpha * (0.7f + 0.3f * twinkle));
            int base = isDark ? 255 : 0;
            int r = base, g = base, b = base;

            if (s.colorShift) {
                r = base == 255 ? 200 + random.nextInt(56) : random.nextInt(56);
                g = base == 255 ? 200 + random.nextInt(56) : random.nextInt(56);
                b = base == 255 ? 200 + random.nextInt(56) : random.nextInt(56);
            }

            starPaint.setColor(Color.argb(alpha, r, g, b));
            canvas.drawCircle(s.baseX + driftX, s.baseY + driftY, s.size, starPaint);

            s.movePhase += 0.004f;
            s.twinklePhase += s.twinkleSpeed;
        }

        postInvalidateOnAnimation();
    }

    // === Customization APIs ===
    public void setStarCount(int count) {
        this.starCount = count;
        createStars();
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void setDriftRange(float range) {
        this.driftRange = range;
    }

    public void setBackgroundColorCustom(int color) {
        this.bgColor = color;
    }

    public void setStarSizeRange(float min, float max) {
        this.minStarSize = min;
        this.maxStarSize = max;
        createStars();
    }

    // === Theme override API ===
    public void setThemeOverrideDark(Boolean isDark) {
        this.themeOverrideDark = isDark;
        if (isDark != null) {
            this.bgColor = isDark ? Color.rgb(0, 0, 0) : Color.rgb(255, 255, 255);
        }
        invalidate();
    }

    // === Inner classes ===
    private static class Star {
        float baseX, baseY;
        float size;
        int alpha;
        float twinkleSpeed;
        float twinklePhase;
        float movePhase;
        boolean colorShift;
    }
}