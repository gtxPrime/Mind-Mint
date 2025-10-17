package com.gxdevs.mindmint.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.graphics.Color;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gxdevs.mindmint.R;

import java.util.ArrayList;
import java.util.List;

public class CustomPieChart extends View {

    private List<Integer> data;
    private List<String> segmentNamesList;
    private Paint paint;
    private Paint textPaint;
    private float startAngle;
    private float gapDegrees;
    private int[] pieChartColors;
    private float[] arcValues;
    private int currentTotalDataSum;

    private float animatedFraction;
    private final RectF rectF = new RectF();

    private int selectedSegmentIndex = -1;
    private float defaultArcWidth;
    private float highlightedArcWidth;

    private float customRadius = -1f; // -1 means auto
    private float customGlow = -1f; // -1 means default
    private float customThickness = -1f; // -1 means default
    private float customStartAngle = Float.NaN; // NaN means default
    private float customGapDegree = Float.NaN; // NaN means default
    private float customTextSize = -1f; // -1 means default
    private float customEndAngle = Float.NaN; // NaN means default

    private boolean isFullCircle = false;

    private static final int DIMMED_ALPHA = 100;
    private static final int FULL_ALPHA = 255;

    public CustomPieChart(Context context) {
        super(context);
        init();
        if (isInEditMode()) setPreviewData();
    }

    public CustomPieChart(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        loadAttrs(context, attrs);
        init();
        if (isInEditMode()) setPreviewData();
    }

    public CustomPieChart(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        loadAttrs(context, attrs);
        init();
        if (isInEditMode()) setPreviewData();
    }

    private void loadAttrs(Context context, AttributeSet attrs) {
        if (attrs == null) return;
        android.content.res.TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CustomPieChart);
        try {
            customRadius = a.getDimension(R.styleable.CustomPieChart_pieRadius, -1f);
            customGlow = a.getDimension(R.styleable.CustomPieChart_pieGlow, -1f);
            customThickness = a.getDimension(R.styleable.CustomPieChart_pieThickness, -1f);
            customTextSize = a.getDimension(R.styleable.CustomPieChart_pieTextSize, -1f);
            customStartAngle = a.getFloat(R.styleable.CustomPieChart_pieStartAngle, Float.NaN);
            customEndAngle = a.getFloat(R.styleable.CustomPieChart_pieEndAngle, Float.NaN);
            customGapDegree = a.getFloat(R.styleable.CustomPieChart_pieGapDegree, Float.NaN);
        } finally {
            a.recycle();
        }
    }

    private void init() {
        defaultArcWidth = 60f;
        highlightedArcWidth = defaultArcWidth * 1.3f;
        startAngle = 140f;
        gapDegrees = 20f;
        if (customThickness > 0) {
            defaultArcWidth = customThickness;
            highlightedArcWidth = defaultArcWidth * 1.3f;
        }
        if (!Float.isNaN(customStartAngle)) {
            startAngle = customStartAngle;
        }
        if (!Float.isNaN(customGapDegree)) {
            gapDegrees = customGapDegree;
        }
        if (customTextSize > 0) {
            if (textPaint == null) textPaint = new Paint();
            textPaint.setTextSize(customTextSize);
        }

        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);

        textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(35f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        if (customTextSize > 0) {
            textPaint.setTextSize(customTextSize);
        }

        Paint centerTextPaint = new Paint();
        centerTextPaint.setAntiAlias(true);
        centerTextPaint.setColor(Color.BLACK);
        centerTextPaint.setTextSize(50f);
        centerTextPaint.setTextAlign(Paint.Align.CENTER);

        Paint valueTextPaint = new Paint();
        valueTextPaint.setAntiAlias(true);
        valueTextPaint.setColor(Color.DKGRAY);
        valueTextPaint.setTextSize(40f);
        valueTextPaint.setTextAlign(Paint.Align.CENTER);

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setData(List<Integer> dataInMinutes, List<String> names, int[] pieChartColors) {
        List<Integer> filteredDataInMinutes = new ArrayList<>();
        List<String> filteredNames = new ArrayList<>();

        if (dataInMinutes == null) {
            dataInMinutes = new ArrayList<>();
        }

        for (int i = 0; i < dataInMinutes.size(); i++) {
            int minutes = dataInMinutes.get(i);
            if (minutes > 0) {
                filteredDataInMinutes.add(minutes);
                if (names != null && i < names.size()) {
                    filteredNames.add(names.get(i));
                } else {
                    filteredNames.add("Unknown");
                }
            }
        }

        if (filteredDataInMinutes.isEmpty()) {
            Log.w("CustomPieChart", "No valid data (minutes > 0). No bars will be drawn.");
            this.data = new ArrayList<>();
            this.segmentNamesList = new ArrayList<>();
        } else {
            this.data = filteredDataInMinutes;
            this.segmentNamesList = filteredNames;
        }

        this.pieChartColors = pieChartColors;
        calculateArcValues();
        if (isInEditMode()) {
            animatedFraction = 1f;
            invalidate();
        } else {
            startAnimation();
        }
        highlightSegmentByName(null);
    }

    private void calculateArcValues() {
        currentTotalDataSum = 0;
        if (this.data == null || this.data.isEmpty()) {
            arcValues = new float[0];
            return;
        }
        for (int valueInMinutes : this.data) {
            currentTotalDataSum += valueInMinutes;
        }
        if (currentTotalDataSum == 0) {
            arcValues = new float[this.data.size()];
            return;
        }
        int segmentCount = this.data.size();
        // Determine if we should render a full circle
        isFullCircle = ((!Float.isNaN(customStartAngle) && !Float.isNaN(customEndAngle) && customStartAngle == 0f && customEndAngle == 0f)
                || (Float.isNaN(customStartAngle) && Float.isNaN(customEndAngle)));
        float gapsCount = isFullCircle ? segmentCount : Math.max(segmentCount - 1, 0);
        float totalGaps = gapDegrees * gapsCount;
        float availableAngle;
        float effectiveStart = !Float.isNaN(customStartAngle) ? customStartAngle : startAngle;
        float effectiveEnd = !Float.isNaN(customEndAngle) ? customEndAngle : (effectiveStart + 280f);
        if (isFullCircle) {
            // Full circle (wrap-around): distribute across 360 with equal gaps, including between last and first
            availableAngle = Math.max(0f, 360f - totalGaps);
            startAngle = 0f;
        } else {
            availableAngle = Math.max(0f, effectiveEnd - effectiveStart - totalGaps);
            startAngle = effectiveStart;
        }
        arcValues = new float[this.data.size()];
        for (int i = 0; i < this.data.size(); i++) {
            arcValues[i] = (float) this.data.get(i) / currentTotalDataSum * availableAngle;
        }
    }

    private void startAnimation() {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(600);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            animatedFraction = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if ((data == null || data.isEmpty()) && isInEditMode()) {
            setPreviewData();
        }

        if (data == null || data.isEmpty()) {
            String placeholder = "empty";
            Paint simplePaint = new Paint();
            simplePaint.setColor(Color.BLACK);
            simplePaint.setTextSize(50f);
            simplePaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(placeholder, getWidth() / 2f, getHeight() / 2f, simplePaint);
            return;
        }

        float newStartAngle = startAngle;
        float maxPossibleArcWidth = (selectedSegmentIndex != -1) ? highlightedArcWidth : defaultArcWidth;
        float radius = customRadius > 0 ? customRadius : Math.min(getWidth(), getHeight()) / 2f - maxPossibleArcWidth / 2f;

        float chartCenterX = getWidth() / 2f;
        float chartCenterY = getHeight() / 2f;

        rectF.set(
                chartCenterX - radius,
                chartCenterY - radius,
                chartCenterX + radius,
                chartCenterY + radius
        );

        for (int i = 0; i < data.size(); i++) {
            float currentSegmentArcWidth = defaultArcWidth;
            int currentSegmentAlpha = FULL_ALPHA;
            int currentColor = pieChartColors[i % pieChartColors.length];

            if (selectedSegmentIndex != -1) {
                if (i == selectedSegmentIndex) {
                    currentSegmentArcWidth = highlightedArcWidth;
                } else {
                    currentSegmentAlpha = DIMMED_ALPHA;
                }
            }
            paint.setStrokeWidth(currentSegmentArcWidth);
            int segmentColorWithAlpha = Color.argb(currentSegmentAlpha, Color.red(currentColor), Color.green(currentColor), Color.blue(currentColor));
            paint.setColor(segmentColorWithAlpha);

            float shadowRadius = (i == selectedSegmentIndex) ? (customGlow > 0 ? customGlow : 70f) : (customGlow > 0 ? customGlow : 60f);
            paint.setShadowLayer(shadowRadius, 0f, 0f, segmentColorWithAlpha);

            float currentArcSweepAngle = arcValues[i] * animatedFraction;
            canvas.drawArc(rectF, newStartAngle, currentArcSweepAngle, false, paint);
            paint.setShadowLayer(0, 0, 0, 0);

            if (currentArcSweepAngle > 0.1f && currentSegmentAlpha == FULL_ALPHA) {
                float percentage = (float) data.get(i) / currentTotalDataSum * 100f;
                String percentText = String.format(Locale.getDefault(), "%.0f%%", percentage);
                float arcCenterAngleDegrees = newStartAngle + currentArcSweepAngle / 2f;
                float arcCenterAngleRadians = (float) Math.toRadians(arcCenterAngleDegrees);

                float textRadius = radius - (maxPossibleArcWidth - defaultArcWidth)/2f;

                float textX = chartCenterX + (float) (textRadius * Math.cos(arcCenterAngleRadians));
                float textY = chartCenterY + (float) (textRadius * Math.sin(arcCenterAngleRadians));

                double brightness = Color.red(currentColor) * 0.299 +
                        Color.green(currentColor) * 0.587 +
                        Color.blue(currentColor) * 0.114;
                textPaint.setColor(brightness < 128 ? Color.WHITE : Color.BLACK);
                canvas.drawText(percentText, textX, textY - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint);
            }
            newStartAngle += arcValues[i];
            if (isFullCircle || i < data.size() - 1) {
                newStartAngle += gapDegrees;
            }
        }
    }

    public void highlightSegmentByName(String subjectName) {
        if (segmentNamesList == null || segmentNamesList.isEmpty() || data == null || data.isEmpty()) {
            selectedSegmentIndex = -1;
            invalidate();
            return;
        }

        if (subjectName == null) {
            selectedSegmentIndex = -1;
        } else {
            boolean found = false;
            for (int i = 0; i < segmentNamesList.size(); i++) {
                if (subjectName.equals(segmentNamesList.get(i))) {
                    if (selectedSegmentIndex == i) {
                        selectedSegmentIndex = -1;
                    } else {
                        selectedSegmentIndex = i;
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                selectedSegmentIndex = -1;
            }
        }
        invalidate();
    }

    private void setPreviewData() {
        List<Integer> demoData = new ArrayList<>();
        demoData.add(30);
        demoData.add(20);
        demoData.add(10);
        List<String> demoNames = new ArrayList<>();
        demoNames.add("YouTube");
        demoNames.add("Instagram");
        demoNames.add("Snapchat");
        int[] demoColors = new int[] { Color.parseColor("#0cedda"), Color.parseColor("#ed0cde"), Color.parseColor("#FFEB3B"), Color.parseColor("#184B50") };
        setData(demoData, demoNames, demoColors);
    }
}
