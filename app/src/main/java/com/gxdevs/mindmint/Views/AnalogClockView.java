package com.gxdevs.mindmint.Views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

public class AnalogClockView extends View {

    private Paint hourPaint, minutePaint, numberPaint, dotPaint, centerPaint;
    private int centerX, centerY, radius;
    private int hour = 9, minute = 0;
    private boolean isSelectingHour = true;
    private OnTimeChangeListener timeChangeListener;

    public interface OnTimeChangeListener {
        void onTimeChanged(int hour, int minute);
    }

    public AnalogClockView(Context context) {
        super(context);
        init();
    }

    public AnalogClockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AnalogClockView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        hourPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hourPaint.setColor(Color.parseColor("#4ECDC4")); // brainColor
        hourPaint.setStrokeWidth(8);
        hourPaint.setStrokeCap(Paint.Cap.ROUND);

        minutePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        minutePaint.setColor(Color.parseColor("#4ECDC4"));
        minutePaint.setStrokeWidth(4);
        minutePaint.setStrokeCap(Paint.Cap.ROUND);

        numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        numberPaint.setColor(Color.WHITE);
        numberPaint.setTextSize(48);
        numberPaint.setTextAlign(Paint.Align.CENTER);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(Color.parseColor("#666666"));

        centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerPaint.setColor(Color.parseColor("#4ECDC4"));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2;
        centerY = h / 2;
        radius = Math.min(w, h) / 2 - 20;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        // Draw hour numbers
        for (int i = 1; i <= 12; i++) {
            double angle = Math.toRadians((i * 30) - 90);
            float x = (float) (centerX + (radius - 60) * Math.cos(angle));
            float y = (float) (centerY + (radius - 60) * Math.sin(angle) + 15);
            canvas.drawText(String.valueOf(i), x, y, numberPaint);
        }

        // Draw minute dots
        for (int i = 0; i < 60; i++) {
            if (i % 5 != 0) { // Skip hour positions
                double angle = Math.toRadians((i * 6) - 90);
                float x = (float) (centerX + (radius - 30) * Math.cos(angle));
                float y = (float) (centerY + (radius - 30) * Math.sin(angle));
                canvas.drawCircle(x, y, 3, dotPaint);
            }
        }

        // Draw hour hand
        double hourAngle = Math.toRadians(((hour % 12) * 30 + minute * 0.5) - 90);
        float hourEndX = (float) (centerX + (radius - 80) * Math.cos(hourAngle));
        float hourEndY = (float) (centerY + (radius - 80) * Math.sin(hourAngle));
        canvas.drawLine(centerX, centerY, hourEndX, hourEndY, hourPaint);

        // Draw minute hand
        double minuteAngle = Math.toRadians((minute * 6) - 90);
        float minuteEndX = (float) (centerX + (radius - 40) * Math.cos(minuteAngle));
        float minuteEndY = (float) (centerY + (radius - 40) * Math.sin(minuteAngle));
        canvas.drawLine(centerX, centerY, minuteEndX, minuteEndY, minutePaint);

        // Draw center dot
        canvas.drawCircle(centerX, centerY, 12, centerPaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            float x = event.getX() - centerX;
            float y = event.getY() - centerY;

            double angle = Math.atan2(y, x);
            angle = Math.toDegrees(angle) + 90;
            if (angle < 0) angle += 360;

            if (isSelectingHour) {
                hour = (int) ((angle + 15) / 30) % 12;
                if (hour == 0) hour = 12;
            } else {
                minute = (int) ((angle + 3) / 6) % 60;
            }

            if (timeChangeListener != null) {
                timeChangeListener.onTimeChanged(hour, minute);
            }

            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }

    public void setTime(int hour, int minute) {
        this.hour = hour;
        this.minute = minute;
        invalidate();
    }

    public void setSelectingHour(boolean selectingHour) {
        this.isSelectingHour = selectingHour;
    }

    public void setOnTimeChangeListener(OnTimeChangeListener listener) {
        this.timeChangeListener = listener;
    }

    public int getHour() {
        return hour;
    }

    public int getMinute() {
        return minute;
    }
}
