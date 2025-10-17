package com.gxdevs.mindmint.Adapters;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.viewpager.widget.PagerAdapter;

import com.google.android.material.textview.MaterialTextView;
import com.gxdevs.mindmint.R;

import java.util.Random;

public class SliderAdapter extends PagerAdapter {
    Context context;

    int[] images = {
            R.drawable.angry_brain,
            R.drawable.meditation,
            R.drawable.security_brain
    };

    int[] overlay1 = {
            R.drawable.angry_overlay_1,
            R.drawable.target,
            R.drawable.lock
    };

    int[] overlay2 = {
            R.drawable.angry_overlay_2,
            R.drawable.trophy,
            R.drawable.security
    };

    int[] headings = {
            R.string.heading1,
            R.string.heading2,
            R.string.heading3
    };

    int[] descriptions = {
            R.string.desc1,
            R.string.desc2,
            R.string.desc3
    };

    public SliderAdapter(Context context) {
        this.context = context;
    }

    @Override
    public int getCount() {
        return headings.length;
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }


    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        String moreInfo = " More info?";
        SpannableString spannable;

        // Inflate the layout
        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = layoutInflater.inflate(R.layout.slides_layout, container, false);

        // Find the ImageViews and TextViews
        ImageView sliderImg = view.findViewById(R.id.sliderImg);
        ImageView overlay1V = view.findViewById(R.id.overlay1);
        ImageView overlay2V = view.findViewById(R.id.overlay2);
        MaterialTextView heading = view.findViewById(R.id.sliderHead);
        MaterialTextView desc = view.findViewById(R.id.sliderDesc);

        overlay1V.setTag("overlay1");
        overlay2V.setTag("overlay2");
        sliderImg.setTag("slider");

        // Set content for ImageViews and TextViews
        sliderImg.setImageResource(images[position]);
        heading.setText(headings[position]);
        overlay1V.setImageResource(overlay1[position]);
        overlay2V.setImageResource(overlay2[position]);
        desc.setText(descriptions[position]);

        // Apply layout changes based on the slide position
        applyLayoutChanges(sliderImg, overlay1V, overlay2V, position);

        // Add the inflated view to the ViewPager
        container.addView(view);

        return view;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        // Properly remove the view from the container
        container.removeView((View) object);
    }

    private void applyLayoutChanges(ImageView sliderImg, ImageView overlay1V, ImageView overlay2V, int position) {
        switch (position) {
            case 0:
                // First slide: No changes to slider image
                //sliderImg.setPadding(dpToPx(70), dpToPx(70), dpToPx(70), dpToPx(70));
                setOverlayParams(overlay1V, 70, 70, 70, 80, false, 0, position);
                setOverlayParams(overlay2V, 50, 50, 100, 80, true, 0, position);
                break;
            case 1:
                // Second slide: 70dp padding to slider image
                //sliderImg.setPadding(dpToPx(70), dpToPx(70), dpToPx(70), dpToPx(70));
                setOverlayParams(overlay1V, 80, 80, 60, 60, false, 0, position);
                setOverlayParams(overlay2V, 80, 80, 70, 40, true, 0, position);
                break;
            case 2:
                // Third slide: 70dp padding to slider image
                //sliderImg.setPadding(dpToPx(70), dpToPx(70), dpToPx(70), dpToPx(70));
                setOverlayParams(overlay1V, 90, 90, 50, 45, false, -20, position);
                setOverlayParams(overlay2V, 105, 105, 30, 40, true, 0, position);
                break;
        }
    }

    private void setOverlayParams(ImageView imageView, int widthDp, int heightDp, int sideMarginDp, int marginTopDp, boolean isEndMargin, float rotation, int position) {
        int widthPx = dpToPx(widthDp);
        int heightPx = dpToPx(heightDp);
        int sideMarginPx = dpToPx(sideMarginDp);
        int marginTopPx = dpToPx(marginTopDp);

        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(widthPx, heightPx);

        // Apply margins and constraints based on start or end
        if (isEndMargin) {
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID; // Ensure top constraint is set
            params.setMarginEnd(sideMarginPx);
        } else {
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID; // Ensure top constraint is set
            params.setMarginStart(sideMarginPx);
        }
        params.topMargin = marginTopPx;

        checkImageView(imageView, position);
        // Apply rotation if necessary
        imageView.setLayoutParams(params);
        imageView.setRotation(rotation);
    }

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void startDynamicPopAnimation(final ImageView imageView) {
        new Random();
        // Create ObjectAnimators for pop-up and pop-down effect
        ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(imageView, "scaleX", 1f, 1.2f, 1f);
        scaleXAnimator.setDuration(600);
        scaleXAnimator.setRepeatCount(ValueAnimator.INFINITE);
        scaleXAnimator.setRepeatMode(ValueAnimator.REVERSE);

        ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(imageView, "scaleY", 1f, 1.2f, 1f);
        scaleYAnimator.setDuration(600);
        scaleYAnimator.setRepeatCount(ValueAnimator.INFINITE);
        scaleYAnimator.setRepeatMode(ValueAnimator.REVERSE);

        // Combine all animations
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(scaleXAnimator, scaleYAnimator);
        animatorSet.start();
    }

    private void startCircularMotion(final ImageView imageView, final float radius, final long duration) {
        new Random();

        // Create a ValueAnimator to animate the angle
        ValueAnimator angleAnimator = ValueAnimator.ofFloat(0, 360);
        angleAnimator.setDuration(duration); // Duration of one full circle
        angleAnimator.setRepeatCount(ValueAnimator.INFINITE);
        angleAnimator.setInterpolator(null); // Use default interpolator

        angleAnimator.addUpdateListener(animation -> {
            float angle = (float) animation.getAnimatedValue(); // Get current angle

            // Convert angle to radians
            float radians = (float) Math.toRadians(angle);

            // Calculate new X and Y position
            float x = (float) (radius * Math.cos(radians));
            float y = (float) (radius * Math.sin(radians));

            // Apply the calculated position
            imageView.setTranslationX(x);
            imageView.setTranslationY(y);
        });

        angleAnimator.start();
    }

    private void checkImageView(ImageView imageView, int position) {
        String tag = (String) imageView.getTag();

        if (position == 0) {
            if ("overlay1".equals(tag)) {
                startDynamicPopAnimation(imageView);
            } else if ("overlay2".equals(tag)) {
                startDynamicPopAnimation(imageView);
            }
        } else {
            if ("overlay1".equals(tag)) {
                startAnimationsForOverlays(imageView, 25f, 5000);
            } else if ("overlay2".equals(tag)) {
                startAnimationsForOverlays(imageView, 50f, 10000);
            }
        }
    }

    private void startAnimationsForOverlays(final ImageView overlay1, float radius, long duration) {
        startCircularMotion(overlay1, radius, duration);
    }
}
