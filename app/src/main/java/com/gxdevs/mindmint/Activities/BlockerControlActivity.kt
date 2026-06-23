package com.gxdevs.mindmint.Activities

import android.app.AlertDialog
import android.app.Dialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog as ComposeDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.gxdevs.mindmint.Common.IntentActions
import com.gxdevs.mindmint.R
import com.gxdevs.mindmint.Services.AppUsageAccessibilityService
import com.gxdevs.mindmint.Utils.ChallengeLockManager
import com.gxdevs.mindmint.Utils.SettingsLockManager
import com.gxdevs.mindmint.Utils.Utils
import com.gxdevs.mindmint.Activities.LockChallengeActivity
import com.gxdevs.mindmint.db.MindMintRoomDatabase
import com.gxdevs.mindmint.db.dao.BlockedAppDao
import com.gxdevs.mindmint.db.entities.BlockedAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.FragmentActivity
import com.gxdevs.mindmint.Utils.AdultDomainListManager
import com.gxdevs.mindmint.Utils.BlockedSitesManager
import kotlin.time.Duration.Companion.milliseconds

class BlockerControlActivity : AppCompatActivity() {

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var database: MindMintRoomDatabase
    private lateinit var blockedAppDao: BlockedAppDao
    private lateinit var settingsLockMgr: SettingsLockManager
    private lateinit var challengeLockMgr: ChallengeLockManager

    private val isAuthenticatedState = mutableStateOf(false)

    private val challengeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            isAuthenticatedState.value = true
        } else {
            finish()
        }
    }

    private fun showAdultListDownloadDialogAndEnsure(onFinished: (Boolean) -> Unit) {
        val builder = AlertDialog.Builder(this)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_adult_list_progress, null)
        builder.setView(dialogView)
        builder.setCancelable(false)
        val progressDialog = builder.create()
        progressDialog.window?.setBackgroundDrawable(0.toDrawable())
        progressDialog.show()

        AdultDomainListManager.downloadAndBuildList(this,
            object : AdultDomainListManager.OnDownloadCompleteListener {
                override fun onSuccess(mergedFileBytes: Long, sha256Hex: String?, deduped: Boolean) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        Toast.makeText(this@BlockerControlActivity, "List updated successfully", Toast.LENGTH_SHORT).show()
                        onFinished(true)
                    }
                }

                override fun onError(e: Exception) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        Toast.makeText(this@BlockerControlActivity, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        onFinished(false)
                    }
                }
            }, false)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        database = MindMintRoomDatabase.getInstance(this)
        blockedAppDao = database.blockedAppDao()
        settingsLockMgr = SettingsLockManager(this)
        challengeLockMgr = ChallengeLockManager(this)

        // B1/S1: Properly gate entry with real auth — no more hardcoded = true
        if (settingsLockMgr.isLockEnabled) {
            val lockType = sharedPrefs.getString(ChallengeLockManager.PREF_SETTINGS_LOCK_TYPE, "device") ?: "device"
            when (lockType) {
                "device", "custom" -> {
                    settingsLockMgr.authenticate(this, "Access Blocker & Lock Center", object : SettingsLockManager.AuthCallback {
                        override fun onSuccess() { isAuthenticatedState.value = true }
                        override fun onFailure(reason: String?) { finish() }
                    })
                }
                "oneday" -> {
                    if (challengeLockMgr.isSettingsOneDayLockActive) {
                        Toast.makeText(this, "Settings are locked under a 1-Day lockout.", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        isAuthenticatedState.value = true
                    }
                }
                else -> {
                    // Math, text, shake, etc. — launch the challenge screen
                    challengeLauncher.launch(
                        Intent(this, LockChallengeActivity::class.java).apply {
                            putExtra(LockChallengeActivity.EXTRA_LOCK_TYPE, lockType)
                            putExtra(LockChallengeActivity.EXTRA_IS_SETTINGS_LOCK, true)
                        }
                    )
                }
            }
        } else {
            isAuthenticatedState.value = true
        }

        setContent {
            val isAuthenticated by remember { isAuthenticatedState }

            if (isAuthenticated) {
                BlockerControlScreen(
                    onBackClick = { finish() },
                    sharedPrefs = sharedPrefs,
                    blockedAppDao = blockedAppDao,
                    settingsLockMgr = settingsLockMgr,
                    activityContext = this@BlockerControlActivity,
                    onShowAdultListDownload = { onFinished ->
                        showAdultListDownloadDialogAndEnsure(onFinished)
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(themeColor(R.attr.app_bg, Color(0xFFF2F4F6))),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = themeColor(R.attr.brand_pink, Color(0xFFFF6B6B))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockerControlScreen(
    onBackClick: () -> Unit,
    sharedPrefs: SharedPreferences,
    blockedAppDao: BlockedAppDao?,
    settingsLockMgr: SettingsLockManager,
    activityContext: FragmentActivity?,
    onShowAdultListDownload: ((Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current
    // B4: removed unused rememberCoroutineScope()

    val PoppinsFamily = remember { FontFamily(Font(R.font.poppins_semibold)) }
    val InterFamily = remember { FontFamily(Font(R.font.inter18regular)) }

    val appBg = themeColor(R.attr.app_bg, Color(0xFFF2F4F6))
    val textPrimary = themeColor(R.attr.text_primary, Color(0xFF1F2937))
    val textTertiary = themeColor(R.attr.text_tertiary, Color(0xFF94A3B8))
    val brandPink = themeColor(R.attr.brand_pink, Color(0xFFFF6B6B))

    // Preferences States
    var isServicePaused by remember { mutableStateOf(sharedPrefs.getBoolean("isServicePaused", false)) }
    var blockerIntensity by remember { mutableIntStateOf(sharedPrefs.getInt(ChallengeLockManager.PREF_BLOCKER_INTENSITY, 0)) }
    var blockWebsites by remember { mutableStateOf(sharedPrefs.getBoolean(AppUsageAccessibilityService.PREF_BLOCK_BROWSERS_DOOMSCROLLING_ENABLED, false)) }
    var blockAdultContent by remember { mutableStateOf(sharedPrefs.getBoolean(AppUsageAccessibilityService.PREF_BLOCK_ADULT_SITES_ENABLED, false)) }
    var settingsLockEnabled by remember { mutableStateOf(settingsLockMgr.isLockEnabled) }
    var deviceAdminActive by remember {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val component = ComponentName(context, "com.gxdevs.mindmint.Receivers.MindMintDeviceAdminReceiver")
        mutableStateOf(dpm?.isAdminActive(component) == true)
    }

    // Apps state from Room Database
    var appsList by remember { mutableStateOf<List<BlockedAppEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            blockedAppDao?.let { dao ->
                val allApps = dao.getAllSync()
                val displayApps = allApps.filter { !isModPackage(it.packageName) }
                withContext(Dispatchers.Main) {
                    appsList = displayApps
                }
            }
        }
    }

    // Pause Dialog States
    var showPauseDialog by remember { mutableStateOf(false) }
    var showNuclearWarningDialog by remember { mutableStateOf(false) }

    // U3: Live countdown text shown while the blocker is paused
    var pauseCountdownText by remember { mutableStateOf("") }
    LaunchedEffect(isServicePaused) {
        if (isServicePaused) {
            while (true) {
                val resumeTime = sharedPrefs.getLong("resumeTime", 0L)
                val remaining = resumeTime - System.currentTimeMillis()
                if (remaining <= 0) { isServicePaused = false; pauseCountdownText = ""; break }
                val h = remaining / 3_600_000L
                val m = (remaining / 60_000L) % 60
                val s = (remaining / 1_000L) % 60
                pauseCountdownText = "%02dh %02dm %02ds".format(h, m, s)
                delay(1000L)
            }
        } else {
            pauseCountdownText = ""
        }
    }

    fun notifyServiceConfigChanged() {
        activityContext?.let {
            val intent = Intent(IntentActions.getActionUpdatePackages(it)).apply {
                setPackage(it.packageName)
            }
            it.sendBroadcast(intent)
        }
    }

    fun setServicePauseState(paused: Boolean, duration: Long) {
        activityContext?.let {
            val intent = Intent(IntentActions.getActionPauseService(it)).apply {
                putExtra("pause_duration", duration)
                setPackage(it.packageName)
            }
            it.sendBroadcast(intent)
        }
        sharedPrefs.edit {
            putBoolean("isServicePaused", paused)
            putLong("resumeTime", if (paused) System.currentTimeMillis() + duration else 0)
        }
        isServicePaused = paused
    }

    fun saveIntensity(level: Int) {
        // B3: Combined into a single atomic edit{} call (was two separate calls)
        sharedPrefs.edit {
            putInt(ChallengeLockManager.PREF_BLOCKER_INTENSITY, level)
            when (level) {
                2 -> {
                    putBoolean(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_ENABLED, true)
                    putBoolean(AppUsageAccessibilityService.PREF_BLOCK_AFTER_WASTED_TIME_ENABLED, false)
                }
                3 -> {
                    putBoolean(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_ENABLED, true)
                    putBoolean(AppUsageAccessibilityService.PREF_BLOCK_AFTER_WASTED_TIME_ENABLED, true)
                }
                4 -> {
                    putBoolean(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_ENABLED, false)
                    putBoolean(AppUsageAccessibilityService.PREF_BLOCK_AFTER_WASTED_TIME_ENABLED, false)
                    putString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, "oneday")
                }
                else -> {
                    putBoolean(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_ENABLED, false)
                    putBoolean(AppUsageAccessibilityService.PREF_BLOCK_AFTER_WASTED_TIME_ENABLED, false)
                }
            }
        }
        blockerIntensity = level
        notifyServiceConfigChanged()
    }

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
                .animateContentSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "APP BLOCKER",
                        fontFamily = PoppinsFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = textTertiary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Blocker Control",
                        fontFamily = PoppinsFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 28.sp,
                        color = textPrimary
                    )
                }
            }

            // Blocker Switch Card
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(themeColor(R.attr.block_bg, Color(0xFFFFF1F2))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.shield),
                            contentDescription = "Blocker Status",
                            tint = brandPink,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Blocker Status",
                            fontFamily = PoppinsFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = textPrimary
                        )
                        Text(
                            text = if (isServicePaused) {
                                if (pauseCountdownText.isNotEmpty()) "Paused — resumes in $pauseCountdownText"
                                else "Blocker is currently paused"
                            } else "Active protection enabled",
                            fontFamily = InterFamily,
                            fontSize = 13.sp,
                            color = textTertiary
                        )
                    }
                    Switch(
                        checked = !isServicePaused,
                        onCheckedChange = { checked ->
                            if (!checked) {
                                // S5: PERMANENT mode cannot be paused
                                if (blockerIntensity == 4) {
                                    Toast.makeText(activityContext, "Cannot pause — Permanent lock is active.", Toast.LENGTH_SHORT).show()
                                } else if (settingsLockMgr.isLockEnabled && activityContext != null) {
                                    settingsLockMgr.authenticate(activityContext, "Pause Blocker Protection", object : SettingsLockManager.AuthCallback {
                                        override fun onSuccess() { showPauseDialog = true }
                                        override fun onFailure(reason: String?) {}
                                    })
                                } else {
                                    showPauseDialog = true
                                }
                            } else {
                                setServicePauseState(false, 0)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = brandPink,
                            uncheckedThumbColor = textTertiary,
                            uncheckedTrackColor = themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0))
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Intensity Card
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
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Blocker Intensity",
                        fontFamily = PoppinsFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (blockerIntensity) {
                            0 -> "NONE: Blocker is completely inactive."
                            1 -> "FRICTION: Complete a challenge to unlock temporarily."
                            2 -> "REMINDER: Popup warnings show periodically."
                            3 -> "TEMP LOCK: Hard lock when daily limit is reached."
                            4 -> "PERMANENT: Complete block with no challenge bypasses."
                            else -> ""
                        },
                        fontFamily = InterFamily,
                        fontSize = 13.sp,
                        color = themeColor(R.attr.text_secondary, Color(0xFF64748B)),
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    val intensityInteractionSource = remember { MutableInteractionSource() }
                    val isPressed by intensityInteractionSource.collectIsPressedAsState()
                    val isDragged by intensityInteractionSource.collectIsDraggedAsState()
                    val isSliderActive = isPressed || isDragged

                    val thumbHeight by animateDpAsState(
                        targetValue = if (isSliderActive) 30.dp else 24.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    )
                    val trackHeight by animateDpAsState(
                        targetValue = if (isSliderActive) 18.dp else 16.dp,
                        animationSpec = tween(200)
                    )

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = blockerIntensity.toFloat(),
                            onValueChange = { valInt ->
                                blockerIntensity = valInt.toInt()
                            },
                            onValueChangeFinished = {
                                val currentIntensity = sharedPrefs.getInt(ChallengeLockManager.PREF_BLOCKER_INTENSITY, 0)
                                if (blockerIntensity != currentIntensity) {
                                    if (blockerIntensity < currentIntensity) {
                                        if (settingsLockMgr.isLockEnabled && activityContext != null) {
                                            settingsLockMgr.authenticate(activityContext, "Decrease Blocker Intensity", object : SettingsLockManager.AuthCallback {
                                                override fun onSuccess() {
                                                    if (blockerIntensity == 4) {
                                                        showNuclearWarningDialog = true
                                                    } else {
                                                        saveIntensity(blockerIntensity)
                                                    }
                                                }
                                                override fun onFailure(reason: String?) {
                                                    blockerIntensity = currentIntensity
                                                }
                                            })
                                        } else {
                                            if (blockerIntensity == 4) {
                                                showNuclearWarningDialog = true
                                            } else {
                                                saveIntensity(blockerIntensity)
                                            }
                                        }
                                    } else {
                                        if (blockerIntensity == 4) {
                                            showNuclearWarningDialog = true
                                        } else {
                                            saveIntensity(blockerIntensity)
                                        }
                                    }
                                }
                            },
                            valueRange = 0f..4f,
                            steps = 3,
                            interactionSource = intensityInteractionSource,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = brandPink,
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent,
                                activeTickColor = Color.Transparent,
                                inactiveTickColor = Color.Transparent
                            ),
                            track = { state ->
                                val fraction = state.coercedValueAsFraction
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(trackHeight)
                                        .clip(RoundedCornerShape(trackHeight / 2))
                                        .background(themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0)))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction = fraction)
                                            .clip(RoundedCornerShape(trackHeight / 2))
                                            .background(brandPink)
                                    )
                                    if (fraction < 0.95f) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .padding(end = 8.dp)
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(brandPink)
                                        )
                                    }
                                }
                            },
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .size(width = 4.dp, height = thumbHeight)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(brandPink)
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val labels = listOf("NONE", "FRICTION", "REMINDER", "TEMP LOCK", "PERMANENT")
                            labels.forEachIndexed { index, label ->
                                val isSelected = blockerIntensity == index
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            val currentIntensity = sharedPrefs.getInt(ChallengeLockManager.PREF_BLOCKER_INTENSITY, 0)
                                            if (index != currentIntensity) {
                                                if (index < currentIntensity) {
                                                    if (settingsLockMgr.isLockEnabled && activityContext != null) {
                                                        settingsLockMgr.authenticate(activityContext, "Decrease Blocker Intensity", object : SettingsLockManager.AuthCallback {
                                                            override fun onSuccess() {
                                                                if (index == 4) {
                                                                    showNuclearWarningDialog = true
                                                                } else {
                                                                    blockerIntensity = index
                                                                    saveIntensity(index)
                                                                }
                                                            }
                                                            // B2: Revert label to saved value on auth failure
                                                            override fun onFailure(reason: String?) {
                                                                blockerIntensity = currentIntensity
                                                            }
                                                        })
                                                    } else {
                                                        if (index == 4) {
                                                            showNuclearWarningDialog = true
                                                        } else {
                                                            blockerIntensity = index
                                                            saveIntensity(index)
                                                        }
                                                    }
                                                } else {
                                                    if (index == 4) {
                                                        showNuclearWarningDialog = true
                                                    } else {
                                                        blockerIntensity = index
                                                        saveIntensity(index)
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontFamily = PoppinsFamily,
                                        fontSize = 7.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) brandPink else textTertiary,
                                        modifier = Modifier.alpha(if (isSelected) 1f else 0.6f),
                                        textAlign = TextAlign.Center,
                                        softWrap = false,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Dynamic Level Settings Card
            AnimatedVisibility(
                visible = blockerIntensity > 0,
                enter = expandVertically(animationSpec = tween(400)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(20.dp))
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = themeColor(R.attr.surface_nested, Color(0xFFF8FAFC))
                        ),
                        border = BorderStroke(1.dp, themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0))),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            when (blockerIntensity) {
                                1 -> { // FRICTION
                                    ChallengeSelectorSection(
                                        sharedPrefs = sharedPrefs,
                                        settingsLockMgr = settingsLockMgr,
                                        activityContext = activityContext,
                                        PoppinsFamily = PoppinsFamily,
                                        brandPink = brandPink,
                                        textPrimary = textPrimary,
                                        textTertiary = textTertiary,
                                        onChanged = { notifyServiceConfigChanged() }
                                    )
                                }
                                2 -> { // REMINDER
                                    ReminderIntervalSliders(
                                        sharedPrefs = sharedPrefs,
                                        settingsLockMgr = settingsLockMgr,
                                        activityContext = activityContext,
                                        PoppinsFamily = PoppinsFamily,
                                        InterFamily = InterFamily,
                                        brandPink = brandPink,
                                        textPrimary = textPrimary,
                                        textTertiary = textTertiary,
                                        onChanged = { notifyServiceConfigChanged() }
                                    )
                                }
                                3 -> { // TEMP LOCK
                                    TempLockSliders(
                                        sharedPrefs = sharedPrefs,
                                        settingsLockMgr = settingsLockMgr,
                                        activityContext = activityContext,
                                        PoppinsFamily = PoppinsFamily,
                                        InterFamily = InterFamily,
                                        brandPink = brandPink,
                                        textPrimary = textPrimary,
                                        textTertiary = textTertiary,
                                        onChanged = { notifyServiceConfigChanged() }
                                    )
                                }
                                4 -> { // PERMANENT
                                    Text(
                                        text = "⚠️ WARNING: Selected apps are permanently locked. You cannot bypass or disable blocker features while this mode is active.",
                                        fontFamily = InterFamily,
                                        fontSize = 13.sp,
                                        color = brandPink,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Other Blocker Switches Card
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
                    // Web Blocker
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (activityContext != null && !Utils.isAccessibilityPermissionGranted(activityContext)) {
                                    Toast.makeText(activityContext, "Accessibility permission is required.", Toast.LENGTH_SHORT).show()
                                } else if (activityContext != null) {
                                    val intent = ComponentName(activityContext, "com.gxdevs.mindmint.Activities.SiteBlockerActivity")
                                    activityContext.startActivity(Intent().setComponent(intent))
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Web Blocker", fontFamily = PoppinsFamily, fontSize = 15.sp, color = textPrimary)
                            Text(
                                text = if (blockWebsites) "Block specified websites and urls (Tap to edit sites)" else "Block specified websites and urls",
                                fontFamily = InterFamily,
                                fontSize = 12.sp,
                                color = textTertiary
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = blockWebsites,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (activityContext != null && !Utils.isAccessibilityPermissionGranted(activityContext)) {
                                        Toast.makeText(activityContext, "Accessibility permission is required.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        sharedPrefs.edit {
                                            putBoolean(
                                                AppUsageAccessibilityService.PREF_BLOCK_BROWSERS_DOOMSCROLLING_ENABLED,
                                                true
                                            )
                                        }
                                        blockWebsites = true
                                        if (activityContext != null) {
                                            BlockedSitesManager.seedDefaultsIfFirstTimeAndEmpty(activityContext)
                                        }
                                        notifyServiceConfigChanged()
                                    }
                                } else {
                                    if (settingsLockMgr.isLockEnabled && activityContext != null) {
                                        settingsLockMgr.authenticate(activityContext, "Disable Web Blocker", object : SettingsLockManager.AuthCallback {
                                            override fun onSuccess() {
                                                sharedPrefs.edit {
                                                    putBoolean(
                                                        AppUsageAccessibilityService.PREF_BLOCK_BROWSERS_DOOMSCROLLING_ENABLED,
                                                        false
                                                    )
                                                }
                                                blockWebsites = false
                                                notifyServiceConfigChanged()
                                            }
                                            override fun onFailure(reason: String?) {}
                                        })
                                    } else {
                                        sharedPrefs.edit {
                                            putBoolean(
                                                AppUsageAccessibilityService.PREF_BLOCK_BROWSERS_DOOMSCROLLING_ENABLED,
                                                false
                                            )
                                        }
                                        blockWebsites = false
                                        notifyServiceConfigChanged()
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = brandPink,
                                uncheckedThumbColor = textTertiary,
                                uncheckedTrackColor = themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0))
                            )
                        )
                    }

                    HorizontalDivider(color = themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0)), modifier = Modifier.padding(horizontal = 16.dp))

                    // Adult Blocker
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = blockAdultContent) {
                                onShowAdultListDownload { }
                            }
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Block Adult Sites", fontFamily = PoppinsFamily, fontSize = 15.sp, color = textPrimary)
                            Text(
                                text = if (blockAdultContent) "Filters adult urls automatically (Tap to update list)" else "Filters adult urls automatically",
                                fontFamily = InterFamily,
                                fontSize = 12.sp,
                                color = textTertiary
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = blockAdultContent,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (activityContext != null && !Utils.isAccessibilityPermissionGranted(activityContext)) {
                                        Toast.makeText(
                                            activityContext,
                                            "Accessibility permission is required.",
                                            Toast.LENGTH_SHORT).show()
                                    } else {
                                        onShowAdultListDownload { success ->
                                            if (success) {
                                                sharedPrefs.edit {putBoolean(AppUsageAccessibilityService.PREF_BLOCK_ADULT_SITES_ENABLED, true)}
                                                blockAdultContent = true
                                                notifyServiceConfigChanged()
                                            } else {
                                                sharedPrefs.edit {putBoolean(AppUsageAccessibilityService.PREF_BLOCK_ADULT_SITES_ENABLED, false)}
                                                blockAdultContent = false
                                            }
                                        }
                                    }
                                } else {
                                    if (settingsLockMgr.isLockEnabled && activityContext != null) {
                                        settingsLockMgr.authenticate(activityContext, "Disable Adult Sites Blocker", object : SettingsLockManager.AuthCallback {
                                            override fun onSuccess() {
                                                sharedPrefs.edit {putBoolean(AppUsageAccessibilityService.PREF_BLOCK_ADULT_SITES_ENABLED, false)}
                                                blockAdultContent = false
                                                notifyServiceConfigChanged()
                                            }
                                            override fun onFailure(reason: String?) {}
                                        })
                                    } else {
                                        sharedPrefs.edit {putBoolean(AppUsageAccessibilityService.PREF_BLOCK_ADULT_SITES_ENABLED, false)}
                                        blockAdultContent = false
                                        notifyServiceConfigChanged()
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = brandPink,
                                uncheckedThumbColor = textTertiary,
                                uncheckedTrackColor = themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0))
                            )
                        )
                    }
 
                    HorizontalDivider(color = themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0)), modifier = Modifier.padding(horizontal = 16.dp))
 
                    // Settings Lock
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Settings Lock", fontFamily = PoppinsFamily, fontSize = 15.sp, color = textPrimary)
                            Text("Protect control settings with lock type", fontFamily = InterFamily, fontSize = 12.sp, color = textTertiary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = settingsLockEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    val currentType = sharedPrefs.getString(ChallengeLockManager.PREF_SETTINGS_LOCK_TYPE, "device")
                                    if ("custom" == currentType && !settingsLockMgr.hasCustomPin() && activityContext != null) {
                                        settingsLockMgr.showSetCustomPinDialog(activityContext, false) {
                                            settingsLockMgr.isLockEnabled = true
                                            settingsLockEnabled = true
                                        }
                                    } else {
                                        settingsLockMgr.isLockEnabled = true
                                        settingsLockEnabled = true
                                    }
                                } else {
                                    settingsLockEnabled = true // temporary revert until authed
                                    activityContext?.let { act ->
                                        settingsLockMgr.authenticate(act, "Disable Settings Lock", object : SettingsLockManager.AuthCallback {
                                            override fun onSuccess() {
                                                settingsLockMgr.isLockEnabled = false
                                                settingsLockEnabled = false
                                            }
                                            override fun onFailure(reason: String?) {}
                                        })
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = brandPink,
                                uncheckedThumbColor = textTertiary,
                                uncheckedTrackColor = themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0))
                            )
                        )
                    }

                    AnimatedVisibility(
                        visible = settingsLockEnabled,
                        enter = expandVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + fadeIn(animationSpec = tween(200)),
                        exit = shrinkVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + fadeOut(animationSpec = tween(150))
                    ) {
                        Box(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp)) {
                            SettingsLockTypeSelectorSection(
                                sharedPrefs = sharedPrefs,
                                settingsLockMgr = settingsLockMgr,
                                activityContext = activityContext,
                                PoppinsFamily = PoppinsFamily,
                                InterFamily = InterFamily,
                                brandPink = brandPink,
                                textPrimary = textPrimary,
                                textTertiary = textTertiary,
                                onChanged = { notifyServiceConfigChanged() }
                            )
                        }
                    }
 
                    HorizontalDivider(color = themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0)), modifier = Modifier.padding(horizontal = 16.dp))
 
                    // Device Admin
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Uninstall Protection", fontFamily = PoppinsFamily, fontSize = 15.sp, color = textPrimary)
                            Text("Uses Device Admin privileges to secure app", fontFamily = InterFamily, fontSize = 12.sp, color = textTertiary)
                        }
                        Switch(
                            checked = deviceAdminActive,
                            onCheckedChange = { checked ->
                                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                                val component = ComponentName(context, "com.gxdevs.mindmint.Receivers.MindMintDeviceAdminReceiver")
                                if (checked) {
                                    if (dpm?.isAdminActive(component) == false && activityContext != null) {
                                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Grants Mind Mint Device Admin rights to prevent uninstall.")
                                        }
                                        activityContext.startActivity(intent)
                                    }
                                } else {
                                    deviceAdminActive = true // temp revert
                                    activityContext?.let { act ->
                                        settingsLockMgr.authenticate(act, "Disable Uninstall Protection", object : SettingsLockManager.AuthCallback {
                                            override fun onSuccess() {
                                                sharedPrefs.edit {putLong(
                                                    AppUsageAccessibilityService.PREF_ADMIN_GUARD_TRUSTED_TOKEN,
                                                    System.currentTimeMillis()
                                                )
                                                }
                                                Toast.makeText(act, "Disable it from security settings.", Toast.LENGTH_LONG).show()
                                                act.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                                            }
                                            override fun onFailure(reason: String?) {}
                                        })
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = brandPink,
                                uncheckedThumbColor = textTertiary,
                                uncheckedTrackColor = themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0))
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Restricted Apps Section Header
            Text(
                text = "RESTRICTED APPS",
                fontFamily = PoppinsFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = textTertiary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                letterSpacing = 1.sp
            )

            // Restricted Apps Card List
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
                    appsList.forEachIndexed { index, app ->
                        BlockedAppRowItem(
                            app = app,
                            blockedAppDao = blockedAppDao,
                            notifyService = { notifyServiceConfigChanged() },
                            PoppinsFamily = PoppinsFamily,
                            InterFamily = InterFamily,
                            textPrimary = textPrimary,
                            textTertiary = textTertiary,
                            brandPink = brandPink,
                            hasDivider = index > 0
                        )
                    }
                }
            }
        }

        // Blocker Pause Dialog Picker
        if (showPauseDialog) {
            ComposeDialog(onDismissRequest = {
                setServicePauseState(false, 0)
                showPauseDialog = false
            }) {
                var selectedHour by remember { mutableIntStateOf(0) }
                var selectedMinute by remember { mutableIntStateOf(30) }

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
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Pause Blocker Protection",
                            fontFamily = PoppinsFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            color = textPrimary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Hour selector
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Hours", fontFamily = InterFamily, fontSize = 12.sp, color = textTertiary)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(themeColor(R.attr.brand_tint_bg, Color(0xFFFFF1F2)))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "$selectedHour h",
                                        fontFamily = PoppinsFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = brandPink
                                    )
                                }
                                Row {
                                    IconButton(onClick = { if (selectedHour > 0) selectedHour-- }) {
                                        Text("-", fontSize = 20.sp, color = textPrimary)
                                    }
                                    IconButton(onClick = { if (selectedHour < 23) selectedHour++ }) {
                                        Text("+", fontSize = 20.sp, color = textPrimary)
                                    }
                                }
                            }

                            // Minute selector
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Minutes", fontFamily = InterFamily, fontSize = 12.sp, color = textTertiary)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(themeColor(R.attr.brand_tint_bg, Color(0xFFFFF1F2)))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "$selectedMinute m",
                                        fontFamily = PoppinsFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = brandPink
                                    )
                                }
                                Row {
                                    IconButton(onClick = { if (selectedMinute >= 5) selectedMinute -= 5 }) {
                                        Text("-", fontSize = 20.sp, color = textPrimary)
                                    }
                                    IconButton(onClick = { if (selectedMinute <= 50) selectedMinute += 5 }) {
                                        Text("+", fontSize = 20.sp, color = textPrimary)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    setServicePauseState(false, 0)
                                    showPauseDialog = false
                                }
                            ) {
                                Text("Cancel", color = textTertiary)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    val pauseDuration = (selectedHour * 3600L + selectedMinute * 60L) * 1000L
                                    // E4: Prevent zero-duration from silently calling resume
                                    if (pauseDuration > 0) {
                                        setServicePauseState(true, pauseDuration)
                                        Toast.makeText(activityContext, "Blocker paused for ${selectedHour}h ${selectedMinute}m", Toast.LENGTH_SHORT).show()
                                        showPauseDialog = false
                                    } else {
                                        Toast.makeText(activityContext, "Please select a pause duration greater than 0 minutes.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = brandPink),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Pause Blocker", color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Strict Level 4 Nuclear Warning Dialog
        if (showNuclearWarningDialog) {
            var warnSecondsLeft by remember { mutableIntStateOf(5) }
            LaunchedEffect(Unit) {
                while (warnSecondsLeft > 0) {
                    delay(1000L.milliseconds)
                    warnSecondsLeft--
                }
            }

            ComposeDialog(onDismissRequest = {
                blockerIntensity = sharedPrefs.getInt(ChallengeLockManager.PREF_BLOCKER_INTENSITY, 0)
                showNuclearWarningDialog = false
            }) {
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
                            text = "⚠️ Enable Permanent Block?",
                            fontFamily = PoppinsFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            color = textPrimary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Text(
                            text = "Are you sure? Once Permanent lock level is active, you CANNOT bypass any locks, pauses, or change control settings. This lock is permanent until you manually change the intensity level.",
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
                                onClick = {
                                    blockerIntensity = sharedPrefs.getInt(ChallengeLockManager.PREF_BLOCKER_INTENSITY, 0)
                                    showNuclearWarningDialog = false
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text("Cancel", color = textTertiary)
                            }

                            Button(
                                onClick = {
                                    saveIntensity(4)
                                    showNuclearWarningDialog = false
                                },
                                enabled = warnSecondsLeft <= 0,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = brandPink,
                                    disabledContainerColor = brandPink.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (warnSecondsLeft > 0) "Understand (${warnSecondsLeft}s)" else "Understand",
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

@Composable
fun ChallengeSelectorSection(
    sharedPrefs: SharedPreferences,
    settingsLockMgr: SettingsLockManager,
    activityContext: FragmentActivity?,
    PoppinsFamily: FontFamily,
    brandPink: Color,
    textPrimary: Color,
    textTertiary: Color,
    onChanged: () -> Unit
) {
    var selectedChallenge by remember {
        mutableStateOf(sharedPrefs.getString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, "math") ?: "math")
    }

    Text(
        text = "Challenge Type",
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = textPrimary,
        modifier = Modifier.padding(bottom = 10.dp)
    )

    val challenges = listOf("math", "shake", "scream", "breath")
    val labels = listOf("Math", "Shake", "Scream", "10 sec")
    val tabCount = challenges.size
    val selectedIndex = challenges.indexOf(selectedChallenge).coerceAtLeast(0)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0)))
            .padding(2.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val indicatorWidth = maxWidth / tabCount
            val animatedOffset by animateDpAsState(
                targetValue = indicatorWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "challengeOffset"
            )

            Box(
                modifier = Modifier
                    .width(indicatorWidth)
                    .fillMaxHeight()
                    .offset(x = animatedOffset)
                    .clip(RoundedCornerShape(18.dp))
                    .background(brandPink)
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
            challenges.forEachIndexed { i, key ->
                val isSelected = selectedChallenge == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (selectedChallenge != key) {
                                if (settingsLockMgr.isLockEnabled && activityContext != null) {
                                    settingsLockMgr.authenticate(activityContext, "Change Challenge Type", object : SettingsLockManager.AuthCallback {
                                        override fun onSuccess() {
                                            sharedPrefs.edit { putString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, key) }
                                            selectedChallenge = key
                                            onChanged()
                                        }
                                        override fun onFailure(reason: String?) {}
                                    })
                                } else {
                                    sharedPrefs.edit { putString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, key) }
                                    selectedChallenge = key
                                    onChanged()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labels[i],
                        fontFamily = PoppinsFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else textTertiary
                    )
                }
            }
        }
    }
}

@Composable
fun ReminderIntervalSliders(
    sharedPrefs: SharedPreferences,
    settingsLockMgr: SettingsLockManager,
    activityContext: FragmentActivity?,
    PoppinsFamily: FontFamily,
    InterFamily: FontFamily,
    brandPink: Color,
    textPrimary: Color,
    textTertiary: Color,
    onChanged: () -> Unit
) {
    var intervalMinutes by remember {
        mutableIntStateOf(sharedPrefs.getInt(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_MINUTES, 5))
    }
    var popupSeconds by remember {
        mutableIntStateOf(sharedPrefs.getInt(AppUsageAccessibilityService.PREF_BLOCKING_POPUP_DURATION_SEC, 5))
    }
    var requireFriction by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_reminder_friction_enabled", false))
    }

    // 1. Reminder Interval
    CustomLabelSlider(
        title = "Reminder Interval",
        subtitle = "Popup warning interval frequency",
        value = intervalMinutes,
        valueRange = 1f..60f,
        unit = "m",
        brandPink = brandPink,
        textPrimary = textPrimary,
        textTertiary = textTertiary,
        PoppinsFamily = PoppinsFamily,
        InterFamily = InterFamily,
        onValueChange = { intervalMinutes = it },
        onFinished = {
            sharedPrefs.edit {putInt(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_MINUTES, intervalMinutes)}
            onChanged()
        }
    )

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0)))
    Spacer(modifier = Modifier.height(16.dp))

    // 2. Warning Popup Duration
    CustomLabelSlider(
        title = "Popup Warning Duration",
        subtitle = "Display length before auto-dismiss",
        value = popupSeconds,
        valueRange = 3f..15f,
        unit = "s",
        brandPink = brandPink,
        textPrimary = textPrimary,
        textTertiary = textTertiary,
        PoppinsFamily = PoppinsFamily,
        InterFamily = InterFamily,
        onValueChange = { popupSeconds = it },
        onFinished = {
            sharedPrefs.edit {putInt(AppUsageAccessibilityService.PREF_BLOCKING_POPUP_DURATION_SEC, popupSeconds)}
            onChanged()
        }
    )

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0)))
    Spacer(modifier = Modifier.height(12.dp))

    // 3. Friction switch for Reminder
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Require Friction on Open", fontFamily = PoppinsFamily, fontSize = 14.sp, color = textPrimary)
            Text("Force challenge when opening app", fontFamily = InterFamily, fontSize = 11.sp, color = textTertiary)
        }
        Switch(
            checked = requireFriction,
            onCheckedChange = { checked ->
                sharedPrefs.edit {putBoolean("pref_reminder_friction_enabled", checked)}
                requireFriction = checked
                onChanged()
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = brandPink,
                uncheckedThumbColor = textTertiary,
                uncheckedTrackColor = themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0))
            )
        )
    }

    if (requireFriction) {
        Spacer(modifier = Modifier.height(16.dp))
        ChallengeSelectorSection(
            sharedPrefs = sharedPrefs,
            settingsLockMgr = settingsLockMgr,
            activityContext = activityContext,
            PoppinsFamily = PoppinsFamily,
            brandPink = brandPink,
            textPrimary = textPrimary,
            textTertiary = textTertiary,
            onChanged = onChanged
        )
    }
}

@Composable
fun TempLockSliders(
    sharedPrefs: SharedPreferences,
    settingsLockMgr: SettingsLockManager,
    activityContext: FragmentActivity?,
    PoppinsFamily: FontFamily,
    InterFamily: FontFamily,
    brandPink: Color,
    textPrimary: Color,
    textTertiary: Color,
    onChanged: () -> Unit
) {
    var limitType by remember {
        mutableStateOf(sharedPrefs.getString("pref_temp_lock_limit_type", "both") ?: "both")
    }
    var scrollLimit by remember {
        mutableIntStateOf(sharedPrefs.getLong("pref_daily_scroll_limit", 100).toInt())
    }
    var timeLimitMinutes by remember {
        mutableIntStateOf((sharedPrefs.getFloat(AppUsageAccessibilityService.PREF_BLOCK_AFTER_WASTED_TIME_HOURS, 1f) * 60).toInt())
    }
    var requireReminders by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_temp_lock_reminders_enabled", false))
    }
    var requireFriction by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_temp_lock_friction_enabled", false))
    }

    Text(
        text = "Temp Lock Trigger Type",
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = textPrimary,
        modifier = Modifier.padding(bottom = 10.dp)
    )

    val options = listOf("scroll", "time", "both")
    val labels = listOf("Scroll Limit", "Time Limit", "Both Limits")
    val tabCount = options.size
    val selectedIndex = options.indexOf(limitType).coerceAtLeast(0)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0)))
            .padding(2.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val indicatorWidth = maxWidth / tabCount
            val animatedOffset by animateDpAsState(
                targetValue = indicatorWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "tempLockOffset"
            )

            Box(
                modifier = Modifier
                    .width(indicatorWidth)
                    .fillMaxHeight()
                    .offset(x = animatedOffset)
                    .clip(RoundedCornerShape(18.dp))
                    .background(brandPink)
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { i, key ->
                val isSelected = limitType == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (limitType != key) {
                                if (settingsLockMgr.isLockEnabled && activityContext != null) {
                                    settingsLockMgr.authenticate(activityContext, "Change Temp Lock Trigger Type", object : SettingsLockManager.AuthCallback {
                                        override fun onSuccess() {
                                            sharedPrefs.edit {
                                                putString("pref_temp_lock_limit_type", key)
                                                // Auto-enable scroll counting when scroll limit is active
                                                if (key == "scroll" || key == "both") {
                                                    putBoolean("pref_scroll_counter_enabled", true)
                                                }
                                            }
                                            limitType = key
                                            onChanged()
                                        }
                                        override fun onFailure(reason: String?) {}
                                    })
                                } else {
                                    sharedPrefs.edit {
                                        putString("pref_temp_lock_limit_type", key)
                                        // Auto-enable scroll counting when scroll limit is active
                                        if (key == "scroll" || key == "both") {
                                            putBoolean("pref_scroll_counter_enabled", true)
                                        }
                                    }
                                    limitType = key
                                    onChanged()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labels[i],
                        fontFamily = PoppinsFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else textTertiary
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // 1. Daily Scrolls Limit
    if (limitType == "scroll" || limitType == "both") {
        CustomLabelSlider(
            title = "Daily Scroll Limit",
            subtitle = "Max scroll limit before strict lockout (Applied per app individually)",
            value = scrollLimit,
            valueRange = 10f..500f,
            unit = " scrolls",
            brandPink = brandPink,
            textPrimary = textPrimary,
            textTertiary = textTertiary,
            PoppinsFamily = PoppinsFamily,
            InterFamily = InterFamily,
            onValueChange = { scrollLimit = it },
            onFinished = {
                sharedPrefs.edit { putLong("pref_daily_scroll_limit", scrollLimit.toLong()) }
                onChanged()
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    // 2. Daily Time Limit
    if (limitType == "time" || limitType == "both") {
        if (limitType == "both") {
            HorizontalDivider(color = themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0)))
            Spacer(modifier = Modifier.height(16.dp))
        }
        CustomLabelSlider(
            title = "Daily Time Limit",
            subtitle = "Time usage allowed per day (Combined across all blocked apps)",
            value = timeLimitMinutes,
            valueRange = 1f..240f,
            unit = "m",
            brandPink = brandPink,
            textPrimary = textPrimary,
            textTertiary = textTertiary,
            PoppinsFamily = PoppinsFamily,
            InterFamily = InterFamily,
            onValueChange = { timeLimitMinutes = it },
            onFinished = {
                sharedPrefs.edit { putFloat(AppUsageAccessibilityService.PREF_BLOCK_AFTER_WASTED_TIME_HOURS, timeLimitMinutes.toFloat() / 60) }
                onChanged()
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    HorizontalDivider(color = themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0)))
    Spacer(modifier = Modifier.height(12.dp))

    // 3. Reminders switch for Temp Lock
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Show Reminder Popups", fontFamily = PoppinsFamily, fontSize = 14.sp, color = textPrimary)
            Text("Popup warning alerts show periodically under limit", fontFamily = InterFamily, fontSize = 11.sp, color = textTertiary)
        }
        Switch(
            checked = requireReminders,
            onCheckedChange = { checked ->
                sharedPrefs.edit {putBoolean("pref_temp_lock_reminders_enabled", checked)}
                requireReminders = checked
                onChanged()
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = brandPink,
                uncheckedThumbColor = textTertiary,
                uncheckedTrackColor = themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0))
            )
        )
    }

    if (requireReminders) {
        Spacer(modifier = Modifier.height(16.dp))
        ReminderIntervalSliders(
            sharedPrefs = sharedPrefs,
            settingsLockMgr = settingsLockMgr,
            activityContext = activityContext,
            PoppinsFamily = PoppinsFamily,
            InterFamily = InterFamily,
            brandPink = brandPink,
            textPrimary = textPrimary,
            textTertiary = textTertiary,
            onChanged = onChanged
        )
    }

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0)))
    Spacer(modifier = Modifier.height(12.dp))

    // 4. Hybrid Friction Switch Row
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Require Friction on Open", fontFamily = PoppinsFamily, fontSize = 14.sp, color = textPrimary)
            Text("Force challenge when opening under limit", fontFamily = InterFamily, fontSize = 11.sp, color = textTertiary)
        }
        Switch(
            checked = requireFriction,
            onCheckedChange = { checked ->
                sharedPrefs.edit {putBoolean("pref_temp_lock_friction_enabled", checked)}
                requireFriction = checked
                onChanged()
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = brandPink,
                uncheckedThumbColor = textTertiary,
                uncheckedTrackColor = themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0))
            )
        )
    }

    if (requireFriction) {
        Spacer(modifier = Modifier.height(16.dp))
        ChallengeSelectorSection(
            sharedPrefs = sharedPrefs,
            settingsLockMgr = settingsLockMgr,
            activityContext = activityContext,
            PoppinsFamily = PoppinsFamily,
            brandPink = brandPink,
            textPrimary = textPrimary,
            textTertiary = textTertiary,
            onChanged = onChanged
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomLabelSlider(
    title: String,
    subtitle: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    unit: String,
    brandPink: Color,
    textPrimary: Color,
    textTertiary: Color,
    PoppinsFamily: FontFamily,
    InterFamily: FontFamily,
    onValueChange: (Int) -> Unit,
    onFinished: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = PoppinsFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = textPrimary
                )
                Text(
                    text = subtitle,
                    fontFamily = InterFamily,
                    fontSize = 11.sp,
                    color = textTertiary
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(themeColor(R.attr.brand_tint_bg, Color(0xFFFFF1F2)))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$value$unit",
                    fontFamily = PoppinsFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = brandPink
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val isDragged by interactionSource.collectIsDraggedAsState()
        val isSliderActive = isPressed || isDragged

        val thumbHeight by animateDpAsState(
            targetValue = if (isSliderActive) 30.dp else 24.dp,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        )
        val trackHeight by animateDpAsState(
            targetValue = if (isSliderActive) 18.dp else 16.dp,
            animationSpec = tween(200)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${valueRange.start.toInt()}",
                fontFamily = PoppinsFamily,
                fontSize = 11.sp,
                color = textTertiary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                onValueChangeFinished = onFinished,
                valueRange = valueRange,
                interactionSource = interactionSource,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                colors = SliderDefaults.colors(
                    thumbColor = brandPink,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                ),
                track = { state ->
                    val fraction = state.coercedValueAsFraction
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(trackHeight)
                            .clip(RoundedCornerShape(trackHeight / 2))
                            .background(themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0)))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = fraction)
                                .clip(RoundedCornerShape(trackHeight / 2))
                                .background(brandPink)
                        )
                        if (fraction < 0.95f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 8.dp)
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(brandPink)
                            )
                        }
                    }
                },
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(width = 4.dp, height = thumbHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(brandPink)
                    )
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${valueRange.endInclusive.toInt()}",
                fontFamily = PoppinsFamily,
                fontSize = 11.sp,
                color = textTertiary
            )
        }
    }
}

@Composable
fun BlockedAppRowItem(
    app: BlockedAppEntity,
    blockedAppDao: BlockedAppDao?,
    notifyService: () -> Unit,
    PoppinsFamily: FontFamily,
    InterFamily: FontFamily,
    textPrimary: Color,
    textTertiary: Color,
    brandPink: Color,
    hasDivider: Boolean
) {
    val context = LocalContext.current
    val sharedPrefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val scope = rememberCoroutineScope()
    var isRestricted by remember { mutableStateOf(app.isRestricted) }
    var scopeSetting by remember { mutableStateOf(app.scope ?: "full") }
    var useModSetting by remember { mutableStateOf(app.useMod) }

    fun syncModPackagesLocal(parentApp: BlockedAppEntity) {
        scope.launch(Dispatchers.IO) {
            blockedAppDao?.let { dao ->
                val allApps = dao.getAllSync()
                for (a in allApps) {
                    if (isModPackage(a.packageName)) {
                        var match = false
                        if (parentApp.packageName == "com.google.android.youtube" && a.packageName.contains("youtube")) {
                            match = true
                        } else if (parentApp.packageName == "com.instagram.android" && (a.packageName.contains("insta") || a.packageName.contains("honista"))) {
                            match = true
                        } else if (parentApp.packageName == "com.facebook.katana" && a.packageName == "com.facebook.lite") {
                            match = true
                        } else if (parentApp.packageName == "com.ss.android.ugc.trill" &&
                            (a.packageName == "com.zhiliaoapp.musically" ||
                             a.packageName == "com.ss.android.ugc.aweme" ||
                             a.packageName == "com.ss.android.ugc.aweme.lite")) {
                            match = true
                        } else if (parentApp.packageName == "com.reddit.frontpage" &&
                            (a.packageName == "com.andrewshu.android.reddit" ||
                             a.packageName == "ml.docilealligator.infinityforreddit" ||
                             a.packageName == "free.reddit.news" ||
                             a.packageName == "com.laurencedawson.reddit_sync" ||
                             a.packageName == "com.reddit.frontpage.lite")) {
                            match = true
                        } else if (parentApp.packageName == "com.twitter.android" && a.packageName == "com.twitter.android.lite") {
                            match = true
                        }
                        if (match) {
                            a.isRestricted = parentApp.isRestricted && parentApp.useMod
                            a.scope = parentApp.scope
                            a.useMod = parentApp.useMod
                            dao.update(a)
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        if (hasDivider) {
            HorizontalDivider(
                color = themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0)),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon with glass colored backgrounds
            AppIconImage(packageName = app.packageName)

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    fontFamily = PoppinsFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = textPrimary
                )
            }

            Switch(
                checked = isRestricted,
                onCheckedChange = { checked ->
                    if (checked) {
                        isRestricted = true
                        app.isRestricted = true
                        sharedPrefs.edit {
                            when (app.packageName) {
                                "com.google.android.youtube", "com.rvx.android.youtube", "com.revance.android.youtube", "app.morphe.android.youtube" -> {
                                    putBoolean("ytSwitchState", true)
                                }
                                "com.instagram.android", "com.instagram.lite", "com.myinsta.android", "com.instafel.android", "com.instander.android", "com.instagold.android", "com.instapro2.android", "com.instaflow.android", "cc.honista.app", "com.instaprime.android" -> {
                                    putBoolean("instaSwitchState", true)
                                }
                                "com.snapchat.android" -> {
                                    putBoolean("snapSwitchState", true)
                                }
                                "com.facebook.katana" -> { putBoolean("facebookSwitchState", true) }
                                "com.linkedin.android" -> { putBoolean("linkedinSwitchState", true) }
                                "com.reddit.frontpage" -> { putBoolean("redditSwitchState", true) }
                                "com.ss.android.ugc.trill" -> { putBoolean("tiktokSwitchState", true) }
                                "com.twitter.android" -> { putBoolean("twitterSwitchState", true) }
                            }
                        }
                        scope.launch(Dispatchers.IO) {
                            blockedAppDao?.update(app)
                            syncModPackagesLocal(app)
                            withContext(Dispatchers.Main) {
                                notifyService()
                            }
                        }
                    } else {
                        val settingsLockMgr = SettingsLockManager(context)
                        val activityContext = context as? FragmentActivity
                        if (settingsLockMgr.isLockEnabled && activityContext != null) {
                            settingsLockMgr.authenticate(activityContext, "Remove App Restriction", object : SettingsLockManager.AuthCallback {
                                override fun onSuccess() {
                                    isRestricted = false
                                    app.isRestricted = false
                                    sharedPrefs.edit {
                                        when (app.packageName) {
                                            "com.google.android.youtube", "com.rvx.android.youtube", "com.revance.android.youtube", "app.morphe.android.youtube" -> {
                                                putBoolean("ytSwitchState", false)
                                            }
                                            "com.instagram.android", "com.instagram.lite", "com.myinsta.android", "com.instafel.android", "com.instander.android", "com.instagold.android", "com.instapro2.android", "com.instaflow.android", "cc.honista.app", "com.instaprime.android" -> {
                                                putBoolean("instaSwitchState", false)
                                            }
                                            "com.snapchat.android" -> {
                                                putBoolean("snapSwitchState", false)
                                            }
                                            "com.facebook.katana" -> { putBoolean("facebookSwitchState", false) }
                                            "com.linkedin.android" -> { putBoolean("linkedinSwitchState", false) }
                                            "com.reddit.frontpage" -> { putBoolean("redditSwitchState", false) }
                                            "com.ss.android.ugc.trill" -> { putBoolean("tiktokSwitchState", false) }
                                            "com.twitter.android" -> { putBoolean("twitterSwitchState", false) }
                                        }
                                    }
                                    scope.launch(Dispatchers.IO) {
                                        blockedAppDao?.update(app)
                                        syncModPackagesLocal(app)
                                        withContext(Dispatchers.Main) {
                                            notifyService()
                                        }
                                    }
                                }
                                override fun onFailure(reason: String?) {}
                            })
                        } else {
                            isRestricted = false
                            app.isRestricted = false
                            sharedPrefs.edit {
                                when (app.packageName) {
                                    "com.google.android.youtube", "com.rvx.android.youtube", "com.revance.android.youtube", "app.morphe.android.youtube" -> {
                                        putBoolean("ytSwitchState", false)
                                    }
                                    "com.instagram.android", "com.instagram.lite", "com.myinsta.android", "com.instafel.android", "com.instander.android", "com.instagold.android", "com.instapro2.android", "com.instaflow.android", "cc.honista.app", "com.instaprime.android" -> {
                                        putBoolean("instaSwitchState", false)
                                    }
                                    "com.snapchat.android" -> {
                                        putBoolean("snapSwitchState", false)
                                    }
                                    "com.facebook.katana" -> { putBoolean("facebookSwitchState", false) }
                                    "com.linkedin.android" -> { putBoolean("linkedinSwitchState", false) }
                                    "com.reddit.frontpage" -> { putBoolean("redditSwitchState", false) }
                                    "com.ss.android.ugc.trill" -> { putBoolean("tiktokSwitchState", false) }
                                    "com.twitter.android" -> { putBoolean("twitterSwitchState", false) }
                                }
                            }
                            scope.launch(Dispatchers.IO) {
                                blockedAppDao?.update(app)
                                syncModPackagesLocal(app)
                                withContext(Dispatchers.Main) {
                                    notifyService()
                                }
                            }
                        }
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = brandPink,
                    uncheckedThumbColor = textTertiary,
                    uncheckedTrackColor = themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0))
                )
            )
        }

        // App config expansion
        val hasViewId = app.sectionViewId != null && !app.sectionViewId.trim().isEmpty()
        AnimatedVisibility(
            visible = isRestricted && hasViewId,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 76.dp, end = 16.dp, bottom = 14.dp)
            ) {
                // Scope Row Toggle: Section vs Full
                Text(
                    text = "Blocking Scope",
                    fontFamily = PoppinsFamily,
                    fontSize = 12.sp,
                    color = textTertiary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                val options = listOf("section", "full")
                val selectedIndex = options.indexOf(scopeSetting).coerceAtLeast(0)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0)))
                        .padding(2.dp)
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val indicatorWidth = maxWidth / 2
                        val animatedOffset by animateDpAsState(
                            targetValue = indicatorWidth * selectedIndex,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "appScopeOffset"
                        )

                        Box(
                            modifier = Modifier
                                .width(indicatorWidth)
                                .fillMaxHeight()
                                .offset(x = animatedOffset)
                                .clip(RoundedCornerShape(14.dp))
                                .background(brandPink)
                        )
                    }

                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (scopeSetting != "section") {
                                        val settingsLockMgr = SettingsLockManager(context)
                                        val activityContext = context as? FragmentActivity
                                        if (settingsLockMgr.isLockEnabled && activityContext != null) {
                                            settingsLockMgr.authenticate(activityContext, "Change Blocking Scope", object : SettingsLockManager.AuthCallback {
                                                override fun onSuccess() {
                                                    scopeSetting = "section"
                                                    app.scope = "section"
                                                    scope.launch(Dispatchers.IO) {
                                                        blockedAppDao?.update(app)
                                                        syncModPackagesLocal(app)
                                                        withContext(Dispatchers.Main) {
                                                            notifyService()
                                                        }
                                                    }
                                                }
                                                override fun onFailure(reason: String?) {}
                                            })
                                        } else {
                                            scopeSetting = "section"
                                            app.scope = "section"
                                            scope.launch(Dispatchers.IO) {
                                                blockedAppDao?.update(app)
                                                syncModPackagesLocal(app)
                                                withContext(Dispatchers.Main) {
                                                    notifyService()
                                                }
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Doom-scrolling",
                                fontFamily = PoppinsFamily,
                                fontSize = 10.sp,
                                color = if (scopeSetting == "section") Color.White else textTertiary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (scopeSetting == "section") {
                                        val settingsLockMgr = SettingsLockManager(context)
                                        val activityContext = context as? FragmentActivity
                                        if (settingsLockMgr.isLockEnabled && activityContext != null) {
                                            settingsLockMgr.authenticate(activityContext, "Change Blocking Scope", object : SettingsLockManager.AuthCallback {
                                                override fun onSuccess() {
                                                    scopeSetting = "full"
                                                    app.scope = "full"
                                                    scope.launch(Dispatchers.IO) {
                                                        blockedAppDao?.update(app)
                                                        syncModPackagesLocal(app)
                                                        withContext(Dispatchers.Main) {
                                                            notifyService()
                                                        }
                                                    }
                                                }
                                                override fun onFailure(reason: String?) {}
                                            })
                                        } else {
                                            scopeSetting = "full"
                                            app.scope = "full"
                                            scope.launch(Dispatchers.IO) {
                                                blockedAppDao?.update(app)
                                                syncModPackagesLocal(app)
                                                withContext(Dispatchers.Main) {
                                                    notifyService()
                                                }
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Full App",
                                fontFamily = PoppinsFamily,
                                fontSize = 10.sp,
                                color = if (scopeSetting != "section") Color.White else textTertiary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mod Checkbox option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useModSetting,
                        onCheckedChange = { checked ->
                            if (checked) {
                                useModSetting = true
                                app.useMod = true
                                scope.launch(Dispatchers.IO) {
                                    blockedAppDao?.update(app)
                                    syncModPackagesLocal(app)
                                    withContext(Dispatchers.Main) {
                                        notifyService()
                                    }
                                }
                            } else {
                                val settingsLockMgr = SettingsLockManager(context)
                                val activityContext = context as? FragmentActivity
                                if (settingsLockMgr.isLockEnabled && activityContext != null) {
                                    settingsLockMgr.authenticate(activityContext, "Change Mod Client Blocking", object : SettingsLockManager.AuthCallback {
                                        override fun onSuccess() {
                                            useModSetting = false
                                            app.useMod = false
                                            scope.launch(Dispatchers.IO) {
                                                blockedAppDao?.update(app)
                                                syncModPackagesLocal(app)
                                                withContext(Dispatchers.Main) {
                                                    notifyService()
                                                }
                                            }
                                        }
                                        override fun onFailure(reason: String?) {}
                                    })
                                } else {
                                    useModSetting = false
                                    app.useMod = false
                                    scope.launch(Dispatchers.IO) {
                                        blockedAppDao?.update(app)
                                        syncModPackagesLocal(app)
                                        withContext(Dispatchers.Main) {
                                            notifyService()
                                        }
                                    }
                                }
                            }
                        },
                        colors = CheckboxDefaults.colors(checkedColor = brandPink)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Block Mod Client as well",
                            fontFamily = PoppinsFamily,
                            fontSize = 12.sp,
                            color = textPrimary
                        )
                        Text(
                            text = "Locks unofficial mod clones (ReVanced, Honista etc.)",
                            fontFamily = InterFamily,
                            fontSize = 10.sp,
                            color = textTertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppIconImage(packageName: String) {
    val context = LocalContext.current
    var drawableIcon by remember { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                val icon = context.packageManager.getApplicationIcon(packageName)
                withContext(Dispatchers.Main) {
                    drawableIcon = icon
                }
            } catch (_: Exception) {}
        }
    }

    if (drawableIcon != null) {
        val imageBitmap = remember(drawableIcon) {
            val bitmap = createBitmap(
                drawableIcon!!.intrinsicWidth.coerceAtLeast(1),
                drawableIcon!!.intrinsicHeight.coerceAtLeast(1)
            )
            val canvas = Canvas(bitmap)
            drawableIcon!!.setBounds(0, 0, canvas.width, canvas.height)
            drawableIcon!!.draw(canvas)
            bitmap.asImageBitmap()
        }
        Image(
            bitmap = imageBitmap,
            contentDescription = "App Icon",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        )
    } else {
        // Fallbacks
        val iconRes = when {
            packageName.contains("youtube")   -> R.drawable.youtube
            packageName.contains("insta")     -> R.drawable.instagram  // covers instagram.lite too
            packageName.contains("snap")      -> R.drawable.snapchat
            packageName.contains("katana") || packageName.contains("facebook") -> R.drawable.facebook
            packageName.contains("linkedin")  -> R.drawable.linkedin
            packageName.contains("reddit") || packageName.contains("andrewshu") ||
                packageName.contains("infinityforreddit") || packageName.contains("reddit_sync") ||
                packageName == "free.reddit.news" -> R.drawable.reddit
            packageName.contains("trill") || packageName.contains("musically") ||
                packageName.contains("ugc.aweme") -> R.drawable.tiktok
            packageName.contains("twitter")  -> R.drawable.twitter
            else -> R.drawable.shield
        }

        val iconColor = when {
            packageName.contains("youtube")   -> Color(0xFFFF0000)
            packageName.contains("insta")     -> Color(0xFFE1306C)
            packageName.contains("snap")      -> Color(0xFFFFFC00)
            packageName.contains("katana") || packageName.contains("facebook") -> Color(0xFF1877F2)
            packageName.contains("linkedin")  -> Color(0xFF0A66C2)
            packageName.contains("reddit") || packageName.contains("andrewshu") ||
                packageName.contains("infinityforreddit") || packageName.contains("reddit_sync") ||
                packageName == "free.reddit.news" -> Color(0xFFFF4500)
            packageName.contains("trill") || packageName.contains("musically") ||
                packageName.contains("ugc.aweme") -> Color(0xFF010101)
            packageName.contains("twitter")  -> Color(0xFF1DA1F2)
            else -> themeColor(R.attr.text_secondary, Color(0xFF64748B))
        }

        val bgTint = when {
            packageName.contains("youtube")   -> Color(0xFFFF0000).copy(alpha = 0.2f)
            packageName.contains("insta")     -> Color(0xFFE1306C).copy(alpha = 0.2f)
            packageName.contains("snap")      -> Color(0xFFFFFC00).copy(alpha = 0.2f)
            packageName.contains("katana") || packageName.contains("facebook") -> Color(0xFF1877F2).copy(alpha = 0.15f)
            packageName.contains("linkedin")  -> Color(0xFF0A66C2).copy(alpha = 0.15f)
            packageName.contains("reddit") || packageName.contains("andrewshu") ||
                packageName.contains("infinityforreddit") || packageName.contains("reddit_sync") ||
                packageName == "free.reddit.news" -> Color(0xFFFF4500).copy(alpha = 0.15f)
            packageName.contains("trill") || packageName.contains("musically") ||
                packageName.contains("ugc.aweme") -> Color(0xFF69C9D0).copy(alpha = 0.2f)
            packageName.contains("twitter")  -> Color(0xFF1DA1F2).copy(alpha = 0.15f)
            else -> Color.White.copy(alpha = 0.1f)
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(bgTint)
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = "App Icon Fallback",
                tint = iconColor,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun isModPackage(packageName: String?): Boolean {
    if (packageName == null) return false
    return packageName == "com.rvx.android.youtube" ||
           packageName == "com.revance.android.youtube" ||
           packageName == "app.morphe.android.youtube" ||
           packageName == "com.myinsta.android" ||
           packageName == "com.instafel.android" ||
           packageName == "com.instander.android" ||
           packageName == "com.instagold.android" ||
           packageName == "com.instapro2.android" ||
           packageName == "com.instaflow.android" ||
           packageName == "cc.honista.app" ||
           packageName == "com.instaprime.android" ||
           // Instagram Lite (grouped under main Instagram)
           packageName == "com.instagram.lite" ||
           // Facebook Lite (mod/lightweight variant of Facebook main)
           packageName == "com.facebook.lite" ||
           // TikTok legacy/regional variants
           packageName == "com.zhiliaoapp.musically" ||
           packageName == "com.ss.android.ugc.aweme" ||
           packageName == "com.ss.android.ugc.aweme.lite" ||
           // Reddit third-party clients
           packageName == "com.andrewshu.android.reddit" ||
           packageName == "ml.docilealligator.infinityforreddit" ||
           packageName == "free.reddit.news" ||
           packageName == "com.laurencedawson.reddit_sync" ||
           packageName == "com.reddit.frontpage.lite" ||
           // Twitter Lite
           packageName == "com.twitter.android.lite"
}

@Preview(showBackground = true)
@Composable
fun BlockerControlScreenPreview() {
    BlockerControlScreen(
        onBackClick = {},
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(LocalContext.current),
        blockedAppDao = null,
        settingsLockMgr = SettingsLockManager(LocalContext.current),
        activityContext = null,
        onShowAdultListDownload = {}
    )
}

@Composable
fun SettingsLockTypeSelectorSection(
    sharedPrefs: SharedPreferences,
    settingsLockMgr: SettingsLockManager,
    activityContext: FragmentActivity?,
    PoppinsFamily: FontFamily,
    InterFamily: FontFamily,
    brandPink: Color,
    textPrimary: Color,
    textTertiary: Color,
    onChanged: () -> Unit
) {
    var selectedType by remember { mutableStateOf(sharedPrefs.getString(ChallengeLockManager.PREF_SETTINGS_LOCK_TYPE, "device") ?: "device") }

    val options = listOf("device", "custom", "math", "text", "oneday")
    val labels = listOf("Device credentials", "Custom PIN", "Maths Challenge", "Long sentences", "1-Day Lockout")

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = "Select Lock Type",
            fontFamily = PoppinsFamily,
            fontSize = 12.sp,
            color = textTertiary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(themeColor(R.attr.surface_nested, Color(0xFFF8FAFC)))
                .padding(4.dp)
        ) {
            options.forEachIndexed { index, type ->
                val isSelected = selectedType == type
                val handleTypeSelection = {
                    if (selectedType != type) {
                        if (settingsLockMgr.isLockEnabled && activityContext != null) {
                            settingsLockMgr.authenticate(activityContext, "Change Settings Lock Type", object : SettingsLockManager.AuthCallback {
                                override fun onSuccess() {
                                    if (type == "custom" && !settingsLockMgr.hasCustomPin()) {
                                        settingsLockMgr.showSetCustomPinDialog(activityContext, false) {
                                            if (settingsLockMgr.hasCustomPin()) {
                                                sharedPrefs.edit { putString(ChallengeLockManager.PREF_SETTINGS_LOCK_TYPE, "custom") }
                                                selectedType = "custom"
                                                onChanged()
                                            }
                                        }
                                    } else {
                                        sharedPrefs.edit { putString(ChallengeLockManager.PREF_SETTINGS_LOCK_TYPE, type) }
                                        selectedType = type
                                        onChanged()
                                    }
                                }
                                override fun onFailure(reason: String?) {}
                            })
                        } else {
                            if (type == "custom" && !settingsLockMgr.hasCustomPin() && activityContext != null) {
                                settingsLockMgr.showSetCustomPinDialog(activityContext, false) {
                                    if (settingsLockMgr.hasCustomPin()) {
                                        sharedPrefs.edit { putString(ChallengeLockManager.PREF_SETTINGS_LOCK_TYPE, "custom") }
                                        selectedType = "custom"
                                        onChanged()
                                    }
                                }
                            } else {
                                sharedPrefs.edit { putString(ChallengeLockManager.PREF_SETTINGS_LOCK_TYPE, type) }
                                selectedType = type
                                onChanged()
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) brandPink.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { handleTypeSelection() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { handleTypeSelection() },
                        colors = RadioButtonDefaults.colors(selectedColor = brandPink)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = labels[index],
                            fontFamily = PoppinsFamily,
                            fontSize = 14.sp,
                            color = textPrimary
                        )
                        val subtitle = when (type) {
                            "device" -> "Use device PIN, pattern or biometric fingerprint"
                            "custom" -> "Use a custom 6-digit PIN specific to Mind Mint"
                            "math" -> "Solve a complex equation to gain access"
                            "text" -> "Type a long quote exactly to bypass"
                            "oneday" -> "Strict lockout: 24h cooldown timer on settings"
                            else -> ""
                        }
                        Text(
                            text = subtitle,
                            fontFamily = InterFamily,
                            fontSize = 11.sp,
                            color = textTertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun themeColor(attr: Int, default: Color): Color {
    val context = LocalContext.current
    val typedValue = remember { TypedValue() }
    val resolved = context.theme.resolveAttribute(attr, typedValue, true)
    return if (resolved) {
        Color(typedValue.data)
    } else {
        default
    }
}

