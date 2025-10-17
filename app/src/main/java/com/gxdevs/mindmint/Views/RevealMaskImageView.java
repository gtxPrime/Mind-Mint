package com.gxdevs.mindmint.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.os.Handler;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;

public class RevealMaskImageView extends AppCompatImageView {

    // ==== 🧩 Reveal Parameters ====
    private float revealFraction = 0f; // 0 = hidden, 1 = fully revealed

    // ==== 🌊 Wave Parameters (feel free to tweak) ====
    private float waveAmplitude = 10f;  // Height of the wave (px)
    private float waveLength = 250f;    // Distance between wave peaks (px)
    private float waveSpeed = 0.1f;    // Speed of wave movement
    private float wavePhase = 0f;       // Internal animation phase tracker

    // ==== ⚙️ Update Interval ====
    private long frameDelay = 35L;      // ms between frames (lower = smoother, higher = lighter on CPU)

    private final Path clipPath = new Path();
    private final Handler waveHandler = new Handler();

    // Continuous animation runnable
    private final Runnable waveRunnable = new Runnable() {
        @Override
        public void run() {
            wavePhase += waveSpeed; // Move wave forward
            invalidate();            // Redraw the view
            waveHandler.postDelayed(this, frameDelay);
        }
    };

    // ==== Constructors ====
    public RevealMaskImageView(Context context) {
        super(context);
        init();
    }

    public RevealMaskImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RevealMaskImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Start wave motion automatically
        startWave();
    }

    private void startWave() {
        waveHandler.removeCallbacks(waveRunnable);
        waveHandler.post(waveRunnable);
    }

    // ==== 🔄 Reveal Control ====
    public void setRevealFraction(float fraction) {
        revealFraction = Math.max(0f, Math.min(1f, fraction));
        invalidate();
    }

    public float getRevealFraction() {
        return revealFraction;
    }

    // ==== 🎨 Drawing Logic ====
    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        // Compute the visible area height based on fraction
        float visibleHeight = h * revealFraction;
        float revealTop = h - visibleHeight;

        clipPath.reset();
        clipPath.moveTo(0, h);

        // Create a soft damped wave along width
        for (float x = 0; x <= w; x += 8f) {
            float damping = (float) Math.sin((x / w) * Math.PI); // smooth edges
            float y = (float) (Math.sin((x / waveLength) * 2 * Math.PI + wavePhase)
                    * waveAmplitude * damping + revealTop);
            clipPath.lineTo(x, y);
        }

        clipPath.lineTo(w, h);
        clipPath.close();

        // Clip the top area and draw only the revealed part
        canvas.save();
        canvas.clipPath(clipPath);
        super.onDraw(canvas);
        canvas.restore();
    }

    // ==== 🧹 Cleanup ====
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        waveHandler.removeCallbacks(waveRunnable); // prevent leaks
    }

    // ==== ⚡️ Customization Helpers ====
    public void setWaveAmplitude(float amplitude) {
        this.waveAmplitude = amplitude;
    }

    public void setWaveLength(float length) {
        this.waveLength = length;
    }

    public void setWaveSpeed(float speed) {
        this.waveSpeed = speed;
    }

    public void setFrameDelay(long delayMs) {
        this.frameDelay = delayMs;
    }
}