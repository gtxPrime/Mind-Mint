package com.gxdevs.mindmint.Activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.viewpager.widget.ViewPager;

import com.gxdevs.mindmint.Adapters.SliderAdapter;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Utils.Utils;

public class OnBoarding extends AppCompatActivity {
    private ViewPager viewPager;
    private TextView permissionBtn;
    private int currentPosition;
    private Animation buttonAnimation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.applyAppThemeFromPrefs(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
            splashScreen.setKeepOnScreenCondition(() -> false);
        }

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        boolean isFirstRun = getSharedPreferences("PREFERENCE", MODE_PRIVATE).getBoolean("isFirstRun", true);
        if (isFirstRun) {
            setContentView(R.layout.activity_on_boarding);
            Utils.setPad(findViewById(R.id.main), "bottom", this);
            viewPager = findViewById(R.id.slider);
            permissionBtn = findViewById(R.id.accessibilityBtn);

            SliderAdapter sliderAdapter = new SliderAdapter(this);
            viewPager.setAdapter(sliderAdapter);

            viewPager.addOnPageChangeListener(onPageChangeListener);
            buttonAnimation = AnimationUtils.loadAnimation(OnBoarding.this, R.anim.button_anim);
            permissionBtn.setText(ContextCompat.getString(OnBoarding.this, R.string.next));
            permissionBtn.setOnClickListener(v -> viewPager.setCurrentItem(viewPager.getCurrentItem() + 1));

        } else {
            // If it's not the first run, go directly to MainActivity
            startActivity(new Intent(OnBoarding.this, HomeActivity.class));
            finish();
        }
    }


    ViewPager.OnPageChangeListener onPageChangeListener = new ViewPager.OnPageChangeListener() {
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            currentPosition = position;
            permissionBtn.startAnimation(buttonAnimation);

            if (position == 0 || position == 1) {
                permissionBtn.setText(ContextCompat.getString(OnBoarding.this, R.string.next));
                permissionBtn.setOnClickListener(v -> viewPager.setCurrentItem(currentPosition + 1));
            } else if (position == 2) {
                permissionBtn.setText(ContextCompat.getString(OnBoarding.this, R.string.lets_go));
                permissionBtn.setOnClickListener(v -> moveToNextActivity());
                //batteryBtn.setOnClickListener(v -> showBottomSheet(R.string.batter_permission, R.string.why_battery, R.string.click_on_proceed, R.string.step2allow, "battery", false));
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {

        }
    };

    private void moveToNextActivity() {
        getSharedPreferences("PREFERENCE", MODE_PRIVATE).edit().putBoolean("isFirstRun", false).apply();
        startActivity(new Intent(OnBoarding.this, HomeActivity.class));
        finish();
    }
}





















