package com.gxdevs.mindmint.Activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.TypedValue
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.preference.PreferenceManager
import com.gxdevs.mindmint.R
import com.gxdevs.mindmint.Utils.ChallengeLockManager
import com.gxdevs.mindmint.Utils.SettingsLockManager
import kotlinx.coroutines.delay

class LockTypeSelectionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SELECTION_MODE = "extra_selection_mode"
        const val EXTRA_CURRENT_VALUE = "extra_current_value"
        const val EXTRA_ALLOWED_TYPES = "extra_allowed_types"
        
        const val MODE_SETTINGS = "settings"
        const val MODE_BLOCKER = "blocker"
    }

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var mode: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        mode = intent.getStringExtra(EXTRA_SELECTION_MODE) ?: MODE_SETTINGS
        val initialValue = intent.getStringExtra(EXTRA_CURRENT_VALUE)
            ?: if (MODE_SETTINGS == mode) "device" else "none"
        val allowedTypes = intent.getStringArrayExtra(EXTRA_ALLOWED_TYPES)

        setContent {
            var currentValue by remember { mutableStateOf(initialValue) }
            var showWarningDialog by remember { mutableStateOf(false) }
            var pendingOption by remember { mutableStateOf<String?>(null) }
            
            var bypassMinutes by remember {
                val saved = sharedPrefs.getInt(ChallengeLockManager.PREF_BLOCKER_BYPASS_DURATION_MIN, 10)
                mutableStateOf(saved.coerceIn(5, 60))
            }

            val options = remember {
                allowedTypes ?: if (MODE_SETTINGS == mode) {
                    arrayOf("device", "custom", "math", "scream", "breath", "text", "shake", "oneday", "window10")
                } else {
                    arrayOf("none", "math", "scream", "breath", "text", "shake", "oneday", "window10")
                }
            }

            // Custom Font Families matching resource folder
            val PoppinsFamily = remember { FontFamily(Font(R.font.poppins_semibold)) }
            val InterFamily = remember { FontFamily(Font(R.font.inter18regular)) }

            // Dynamic color helper
            @Composable
            fun themeColor(attr: Int, default: Color): Color {
                val context = LocalContext.current
                return remember(attr) {
                    val typedValue = TypedValue()
                    if (context.theme.resolveAttribute(attr, typedValue, true)) {
                        Color(typedValue.data)
                    } else {
                        default
                    }
                }
            }

            // Selection Handler
            fun saveChoice(option: String) {
                val editor = sharedPrefs.edit()
                if (MODE_SETTINGS == mode) {
                    editor.putString(ChallengeLockManager.PREF_SETTINGS_LOCK_TYPE, option)
                    if (option == "oneday") {
                        val clm = ChallengeLockManager(this@LockTypeSelectionActivity)
                        clm.startSettingsOneDayLock()
                    }
                } else {
                    editor.putString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, option)
                }
                editor.apply()

                val resultData = Intent().apply {
                    putExtra("selected_value", option)
                }
                setResult(RESULT_OK, resultData)
            }

            fun handleOptionSelected(option: String) {
                if (option == currentValue) {
                    finish()
                    return
                }

                if (option == "oneday") {
                    pendingOption = option
                    showWarningDialog = true
                } else if (MODE_SETTINGS == mode && option == "custom") {
                    val lockMgr = SettingsLockManager(this@LockTypeSelectionActivity)
                    if (!lockMgr.hasCustomPin()) {
                        lockMgr.showSetCustomPinDialog(this@LockTypeSelectionActivity, false) {
                            saveChoice(option)
                            finish()
                        }
                    } else {
                        saveChoice(option)
                        finish()
                    }
                } else if (option == "window10") {
                    saveChoice(option)
                    currentValue = option
                } else {
                    saveChoice(option)
                    finish()
                }
            }

            val appBg = themeColor(R.attr.app_bg, Color(0xFFF2F4F6))
            val textPrimary = themeColor(R.attr.text_primary, Color(0xFF1F2937))
            val textTertiary = themeColor(R.attr.text_tertiary, Color(0xFF94A3B8))
            val brandPink = themeColor(R.attr.brand_pink, Color(0xFFFF6B6B))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appBg)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp)
                ) {
                    // Custom Toolbar Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { finish() }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_chevron_left),
                                contentDescription = "Back",
                                tint = textPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (MODE_SETTINGS == mode) "SECURITY LOCK" else "CHALLENGE MODE",
                                fontFamily = PoppinsFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = textTertiary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (MODE_SETTINGS == mode) "Settings Lock" else "Blocker Challenge",
                                fontFamily = PoppinsFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 28.sp,
                                color = textPrimary
                            )
                        }
                    }

                    // Options Card
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = themeColor(R.attr.surface_card, Color.White)
                        ),
                        border = BorderStroke(1.dp, themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0))),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column {
                            options.forEachIndexed { index, option ->
                                val label = when (option) {
                                    "none" -> "Normal Blocker"
                                    "device" -> "Device Lock"
                                    "custom" -> "Custom PIN"
                                    "math" -> "Math Equation"
                                    "scream" -> "Scream (Voice)"
                                    "breath" -> "Hold Breath (10s)"
                                    "text" -> "Type Quote"
                                    "shake" -> "Shake to Unlock"
                                    "oneday" -> "1-Day Lock"
                                    "window10" -> "${bypassMinutes}-Min Bypass Window"
                                    else -> option
                                }

                                val description = when (option) {
                                    "none" -> "Normal blocker behavior by default."
                                    "device" -> "Use your phone's PIN, pattern, or biometrics."
                                    "custom" -> "Require a custom 6-digit PIN."
                                    "math" -> "Solve a moderately difficult math equation."
                                    "scream" -> "Hold a loud scream into the mic for 3 seconds."
                                    "breath" -> "Continuously press a button for 10 seconds."
                                    "text" -> "Type a long quote with special symbols."
                                    "shake" -> "Continuously shake your device for 12 seconds."
                                    "oneday" -> "Strictly lock settings/apps for 24 hours."
                                    "window10" -> "Use a single ${bypassMinutes}-minute bypass window per day."
                                    else -> ""
                                }

                                val iconRes = when (option) {
                                    "none" -> R.drawable.eye_off
                                    "device" -> R.drawable.smartphone
                                    "custom" -> R.drawable.shield
                                    "math" -> R.drawable.shapes
                                    "scream" -> R.drawable.bell
                                    "breath" -> R.drawable.clock
                                    "text" -> R.drawable.scroll_text
                                    "shake" -> R.drawable.zap
                                    "oneday" -> R.drawable.shield
                                    "window10" -> R.drawable.hourglass
                                    else -> R.drawable.shield
                                }

                                val iconColor = when (option) {
                                    "none", "oneday" -> Color(0xFFF77381)
                                    "device", "math" -> Color(0xFF61A2F2)
                                    "custom" -> Color(0xFF009688)
                                    "text" -> Color(0xFF3DD7A5)
                                    "window10" -> Color(0xFFBF83FB)
                                    else -> Color(0xFFABABAB)
                                }

                                val bgTintAttr = when (option) {
                                    "none" -> R.attr.eye_bg
                                    "device", "custom", "math", "text" -> R.attr.mobile_bg
                                    "oneday" -> R.attr.block_bg
                                    "window10" -> R.attr.popup_bg
                                    else -> R.attr.popup_bg
                                }
                                val bgTint = themeColor(bgTintAttr, Color(0xFFEFF6FF))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { handleOptionSelected(option) }
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(bgTint),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = iconRes),
                                            contentDescription = label,
                                            tint = iconColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = label,
                                            fontFamily = PoppinsFamily,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 15.sp,
                                            color = textPrimary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = description,
                                            fontFamily = InterFamily,
                                            fontSize = 13.sp,
                                            color = themeColor(R.attr.text_secondary, Color(0xFF64748B))
                                        )
                                    }

                                    if (option == currentValue) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_check_circle),
                                            contentDescription = "Selected",
                                            tint = themeColor(R.attr.tintGlobalColor, Color(0xFF4A4A4A)),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                if (index < options.size - 1) {
                                    HorizontalDivider(
                                        color = themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0)),
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Duration Seekbar Card (window10)
                    if (MODE_BLOCKER == mode && currentValue == "window10") {
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = themeColor(R.attr.surface_nested, Color(0xFFF8FAFC))
                            ),
                            border = BorderStroke(2.dp, themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0))),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(themeColor(R.attr.glass_circle, Color(0xFFF1F5F9))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.hourglass),
                                            contentDescription = "Bypass Duration",
                                            tint = textPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Bypass Duration",
                                            fontFamily = PoppinsFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            color = textPrimary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Bypass window for blocked apps",
                                            fontFamily = InterFamily,
                                            fontSize = 12.sp,
                                            color = textTertiary
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .border(1.dp, themeColor(R.attr.tintGlobalColor, Color(0xFF4A4A4A)), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "${bypassMinutes}m",
                                            fontFamily = PoppinsFamily,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = themeColor(R.attr.tintGlobalColor, Color(0xFF4A4A4A))
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Slider(
                                    value = bypassMinutes.toFloat(),
                                    onValueChange = { value ->
                                        bypassMinutes = value.toInt()
                                        sharedPrefs.edit().putInt(ChallengeLockManager.PREF_BLOCKER_BYPASS_DURATION_MIN, bypassMinutes).apply()
                                    },
                                    valueRange = 5f..60f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = themeColor(R.attr.tintGlobalColor, Color(0xFF4A4A4A)),
                                        activeTrackColor = themeColor(R.attr.tintGlobalColor, Color(0xFF4A4A4A)),
                                        inactiveTrackColor = themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0))
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "5m",
                                        fontFamily = InterFamily,
                                        fontSize = 11.sp,
                                        color = textTertiary,
                                        modifier = Modifier.alpha(0.6f)
                                    )
                                    Text(
                                        text = "60m",
                                        fontFamily = InterFamily,
                                        fontSize = 11.sp,
                                        color = textTertiary,
                                        modifier = Modifier.alpha(0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Warning dialog for 1-Day lockout
            if (showWarningDialog) {
                var secondsLeft by remember { mutableStateOf(5) }
                LaunchedEffect(Unit) {
                    while (secondsLeft > 0) {
                        delay(1000L)
                        secondsLeft--
                    }
                }

                Dialog(onDismissRequest = { showWarningDialog = false }) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = themeColor(R.attr.surface_card, Color.White)
                        ),
                        border = BorderStroke(1.dp, themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0))),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Text(
                                text = "⚠️ Strict 1-Day Lockout",
                                fontFamily = PoppinsFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                color = textPrimary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Text(
                                text = "WARNING: Enabling the 1-Day Lock will strictly lock you out of settings/this app for 24 hours. " +
                                       "This is system-enforced and CANNOT be undone, paused, or bypassed by changing the device clock or resetting PINs.\n\n" +
                                       "Do you want to proceed?",
                                fontFamily = InterFamily,
                                fontSize = 14.sp,
                                color = themeColor(R.attr.text_secondary, Color(0xFF64748B)),
                                modifier = Modifier.padding(bottom = 24.dp),
                                lineHeight = 20.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { showWarningDialog = false },
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = "Cancel",
                                        fontFamily = PoppinsFamily,
                                        fontWeight = FontWeight.Medium,
                                        color = brandPink
                                    )
                                }

                                Button(
                                    onClick = {
                                        showWarningDialog = false
                                        pendingOption?.let {
                                            saveChoice(it)
                                            finish()
                                        }
                                    },
                                    enabled = secondsLeft <= 0,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = brandPink,
                                        disabledContainerColor = brandPink.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = if (secondsLeft > 0) "Understand (${secondsLeft}s)" else "Understand",
                                        fontFamily = PoppinsFamily,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
