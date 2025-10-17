package com.gxdevs.mindmint.Activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.text.HtmlCompat;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.gxdevs.mindmint.Adapters.SliderAdapter;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Utils.Utils;

public class OnBoarding extends AppCompatActivity {
    private ViewPager viewPager;
    private ConstraintLayout dotsLayout;
    private TextView permissionBtn, batteryBtn;
    private int currentPosition;
    private ActivityResultLauncher<Intent> accessibilityLauncher;
    private ActivityResultLauncher<Intent> batteryOptimizationLauncher;
    private boolean accessibilityGranted = false;
    private boolean batteryOptimizationIgnored = false;
    private BottomSheetDialog bottomSheetDialog;
    private TextView[] dots;
    private Animation buttonAnimation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
            splashScreen.setKeepOnScreenCondition(() -> {
                return false;
            });
        }
        bottomSheetDialog = new BottomSheetDialog(OnBoarding.this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        boolean isFirstRun = getSharedPreferences("PREFERENCE", MODE_PRIVATE).getBoolean("isFirstRun", true);
        if (isFirstRun) {
            setContentView(R.layout.activity_on_boarding);

            Utils.setPad(findViewById(R.id.main), "bottom", this);

            viewPager = findViewById(R.id.slider);
            dotsLayout = findViewById(R.id.dots);
            permissionBtn = findViewById(R.id.accessibilityBtn);
            batteryBtn = findViewById(R.id.batteryBtn);

            SliderAdapter sliderAdapter = new SliderAdapter(this);
            viewPager.setAdapter(sliderAdapter);

            createDots();
            updateDotsColor(0);
            viewPager.addOnPageChangeListener(onPageChangeListener);
            registerForPermission();

            buttonAnimation = AnimationUtils.loadAnimation(OnBoarding.this, R.anim.button_anim);

            permissionBtn.setText(ContextCompat.getString(OnBoarding.this, R.string.next));
            batteryBtn.setVisibility(View.INVISIBLE);
            permissionBtn.setOnClickListener(v -> viewPager.setCurrentItem(viewPager.getCurrentItem() + 1));

        } else {
            // If it's not the first run, go directly to MainActivity
            startActivity(new Intent(OnBoarding.this, HomeActivity.class));
            finish();
        }
    }

    private void createDots() {
        dots = new TextView[3];
        dotsLayout.removeAllViews();

        for (int i = 0; i < dots.length; i++) {
            dots[i] = new TextView(this);
            dots[i].setText(HtmlCompat.fromHtml("&#8226", HtmlCompat.FROM_HTML_MODE_LEGACY));
            dots[i].setTextSize(35);
            dotsLayout.addView(dots[i]);
        }
    }

    private void updateDotsColor(int position) {
        for (TextView dot : dots) {
            dot.setTextColor(ContextCompat.getColor(this, R.color.white));
        }
        if (dots != null && position < dots.length) {
            dots[position].setTextColor(ContextCompat.getColor(this, R.color.cyan));
        }
    }

    ViewPager.OnPageChangeListener onPageChangeListener = new ViewPager.OnPageChangeListener() {
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            updateDotsColor(position);
            currentPosition = position;
            permissionBtn.startAnimation(buttonAnimation);

            if (position == 0 || position == 1) {
                permissionBtn.setText(ContextCompat.getString(OnBoarding.this, R.string.next));
                batteryBtn.setVisibility(View.INVISIBLE);
                permissionBtn.setOnClickListener(v -> viewPager.setCurrentItem(currentPosition + 1));
            } else if (position == 2) {
                permissionBtn.setText(ContextCompat.getString(OnBoarding.this, R.string.grant_permission));
                batteryBtn.setVisibility(View.VISIBLE);
                batteryBtn.startAnimation(buttonAnimation);
                permissionBtn.setOnClickListener(v -> showBottomSheet(R.string.ass_permission, R.string.why_accessibility, R.string.click_on_proceed, R.string.select_installed_apps, "ass", true));
                batteryBtn.setOnClickListener(v -> showBottomSheet(R.string.batter_permission, R.string.why_battery, R.string.click_on_proceed, R.string.step2allow, "battery", false));
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {

        }
    };

    public void registerForPermission() {
        accessibilityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (isAccessibilityPermissionGranted()) {
                        accessibilityGranted = true;
                        Toast.makeText(this, "Thank you for granting Accessibility permission!", Toast.LENGTH_SHORT).show();
                        if (bottomSheetDialog.isShowing()) {
                            bottomSheetDialog.dismiss();
                        }
                        checkPermissionsAndMove();
                    } else {
                        Toast.makeText(this, "Accessibility permission not granted.", Toast.LENGTH_SHORT).show();
                    }
                });

        batteryOptimizationLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(POWER_SERVICE);
                    if (powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                        Toast.makeText(this, "Battery optimization ignored", Toast.LENGTH_SHORT).show();
                        batteryOptimizationIgnored = true;
                        checkPermissionsAndMove();
                        if (bottomSheetDialog.isShowing()) {
                            bottomSheetDialog.dismiss();
                        }
                    } else {
                        Toast.makeText(this, "Battery optimization not ignored", Toast.LENGTH_SHORT).show();
                    }
                });

    }

    private boolean isAccessibilityPermissionGranted() {
        String enabledServices = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String packageName = getPackageName();
        return enabledServices != null && enabledServices.contains(packageName);
    }

    private void moveToNextActivity() {
        getSharedPreferences("PREFERENCE", MODE_PRIVATE).edit()
                .putBoolean("isFirstRun", false).apply();
        startActivity(new Intent(OnBoarding.this, HomeActivity.class));
        finish();
    }

    private void checkPermissionsAndMove() {
        if (accessibilityGranted && batteryOptimizationIgnored) {
            moveToNextActivity();
        }
    }

    @SuppressLint("BatteryLife")
    private void requestBattery() {
        PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(POWER_SERVICE);
        Intent i = new Intent();

        if (!powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            i.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            batteryOptimizationLauncher.launch(i);
        } else {
            Toast.makeText(this, "Already Granted", Toast.LENGTH_SHORT).show();
            if (bottomSheetDialog.isShowing()) {
                bottomSheetDialog.dismiss();
            }
            batteryOptimizationIgnored = true;
            checkPermissionsAndMove();
        }
    }

    private void showBottomSheet(int heading, int desc, int step1, int step2, String pro, boolean step3) {
        String mainText = ContextCompat.getString(this, desc);
        String moreInfo = " More info?";
        String longText = ContextCompat.getString(this, R.string.accessibility_more_info);
        String showLess = " Show less";

        View view = LayoutInflater.from(getApplicationContext())
                .inflate(R.layout.layout_bottomsheet, findViewById(R.id.sheetContainer));
        TextView permissionHead = view.findViewById(R.id.permissionHead);
        TextView permissionDesc = view.findViewById(R.id.permissionDesc);
        TextView permissionStep1 = view.findViewById(R.id.permissionStep1);
        TextView permissionStep2 = view.findViewById(R.id.permissionStep2);
        TextView permissionStep3 = view.findViewById(R.id.permissionStep3);
        TextView proceed = view.findViewById(R.id.proceed);
        TextView notNow = view.findViewById(R.id.notNow);

        permissionHead.setText(getString(heading));
        permissionDesc.setText(getString(desc));
        permissionStep1.setText(getString(step1));
        permissionStep2.setText(getString(step2));

        if (step3) {
            SpannableString spannableShort = new SpannableString(mainText + moreInfo);
            ClickableSpan moreInfoSpan = new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    // expanded version
                    SpannableString spannableLong = new SpannableString(longText + showLess);
                    ClickableSpan showLessSpan = new ClickableSpan() {
                        @Override
                        public void onClick(@NonNull View widget) {
                            // back to collapsed
                            permissionDesc.setText(spannableShort);
                            permissionDesc.setMovementMethod(LinkMovementMethod.getInstance());
                            permissionDesc.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                        }

                        @Override
                        public void updateDrawState(@NonNull TextPaint ds) {
                            super.updateDrawState(ds);
                            ds.setColor(ContextCompat.getColor(OnBoarding.this, R.color.cyan));
                            ds.setUnderlineText(false);
                        }
                    };

                    spannableLong.setSpan(showLessSpan, longText.length(), longText.length() + showLess.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    permissionDesc.setText(spannableLong);
                    permissionDesc.setMovementMethod(LinkMovementMethod.getInstance());
                    permissionDesc.setTextAlignment(View.TEXT_ALIGNMENT_CENTER); // expanded = left
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setColor(ContextCompat.getColor(OnBoarding.this, R.color.cyan));
                    ds.setUnderlineText(false);
                }
            };

            spannableShort.setSpan(moreInfoSpan, mainText.length(), mainText.length() + moreInfo.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            permissionDesc.setText(spannableShort);
            permissionDesc.setMovementMethod(LinkMovementMethod.getInstance());
            permissionDesc.setTextAlignment(View.TEXT_ALIGNMENT_CENTER); // collapsed = center
            permissionStep3.setVisibility(View.VISIBLE);
        } else {
            permissionStep3.setVisibility(View.GONE);
        }

        if (pro.equals("ass")) {
            proceed.setOnClickListener(v -> {
                if (!isAccessibilityPermissionGranted()) {
                    accessibilityLauncher.launch(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    if (bottomSheetDialog.isShowing()) {
                        bottomSheetDialog.dismiss();
                    }
                } else {
                    if (bottomSheetDialog.isShowing()) {
                        bottomSheetDialog.dismiss();
                    }
                    accessibilityGranted = true;
                    Toast.makeText(this, "Already Granted", Toast.LENGTH_SHORT).show();
                }
            });
        } else if (pro.equals("battery")) {
            proceed.setOnClickListener(v -> requestBattery());
        }

        notNow.setOnClickListener(v -> bottomSheetDialog.dismiss());

        bottomSheetDialog.setContentView(view);
        bottomSheetDialog.show();
    }
}





















