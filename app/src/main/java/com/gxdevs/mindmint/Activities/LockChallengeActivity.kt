package com.gxdevs.mindmint.Activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gxdevs.mindmint.R
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.gxdevs.mindmint.Services.AppUsageAccessibilityService
import com.gxdevs.mindmint.Utils.ChallengeLockManager
import com.gxdevs.mindmint.Utils.SettingsLockManager
import com.gxdevs.mindmint.Utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LockChallengeActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        private const val TAG = "LockChallengeActivity"
        const val EXTRA_LOCK_TYPE = "extra_lock_type"
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
        const val EXTRA_IS_SETTINGS_LOCK = "extra_is_settings_lock"
        
        private val QUOTES = arrayOf(
            "Focus, consistency, & discipline—these are the cornerstones of success; without them, goals are just dreams!",
            "The secret of focus is simple: find out what's important, discard the rest, and do NOT check your phone!",
            "Success doesn't just 'happen'—you have to design it, sweat for it, & ignore 1,000 distractions every single day."
        )
        private const val PIN_RESET_BYPASS_PARAGRAPH =
            "Discipline is the bridge between goals and accomplishment. I choose to resist short-term impulses and focus on my long-term growth. This 250-character paragraph exists to ensure I make conscious choices—not impulsive ones!"
    }

    private var lockType: String = "math"
    private var targetPackage: String? = null
    private var isSettingsLock: Boolean = false
    private var blockScope: String? = null

    private lateinit var challengeLockMgr: ChallengeLockManager
    private val handler = Handler(Looper.getMainLooper())
    private var vibrator: Vibrator? = null
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    // Scream Challenge Properties
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var screamSustainedMs = 0.0
    private val SCREAM_TARGET_MS = 3000.0
    private var lastScreamUpdateMs: Long = 0L  // B6: tracks actual timestamp between updates

    // Scream States
    private val screamVolume = mutableIntStateOf(0)
    private val screamTimeLeft = mutableStateOf("Hold it: 3.0s")

    // Breath Hold States
    private val breathRemainingMs = mutableDoubleStateOf(10000.0)
    private val isHoldingBreath = mutableStateOf(false)
    private var breathSucceeded = false  // E7: race-condition guard

    // Shake Challenge States
    private val shakeProgress = mutableFloatStateOf(0f)
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastSensorUpdate: Long = 0

    // Math Challenge States
    private val mathEquationText = mutableStateOf("")
    private var mathCorrectAnswer = 0
    private val mathAnswerInput = mutableStateOf("")

    // Typing Challenge States
    private val quoteToType = mutableStateOf("")
    private val typedInputText = mutableStateOf("")
    private val isPinResetTypingMode = mutableStateOf(false)

    // One Day Lock States
    private val oneDayCountdownText = mutableStateOf("24h 00m remaining")
    private var oneDayRunnable: Runnable? = null  // E3: stored for cancellation

    // Permission Launcher for Microphone (Scream challenge)
    private val micPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            startScreamDetection()
        } else {
            Toast.makeText(this, "Microphone permission required for Scream challenge.", Toast.LENGTH_LONG).show()
            handleCancel()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        challengeLockMgr = ChallengeLockManager(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorManager?.let {
            accelerometer = it.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }

        // Parse Intent
        lockType = intent.getStringExtra(EXTRA_LOCK_TYPE) ?: "math"
        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
        isSettingsLock = intent.getBooleanExtra(EXTRA_IS_SETTINGS_LOCK, false)
        blockScope = intent.getStringExtra("extra_block_scope")

        updateSecureFlag()
        initChallengeData()

        setContent {
            val appBg = themeColor(R.attr.app_bg, Color(0xFFF2F4F6))
            val textPrimary = themeColor(R.attr.text_primary, Color(0xFF1F2937))
            val textSecondary = themeColor(R.attr.text_secondary, Color(0xFF64748B))
            val textTertiary = themeColor(R.attr.text_tertiary, Color(0xFF94A3B8))
            val brandPink = themeColor(R.attr.brand_pink, Color(0xFFFF6B6B))

            val PoppinsFamily = remember { FontFamily(Font(R.font.poppins_semibold)) }
            val InterFamily = remember { FontFamily(Font(R.font.inter18regular)) }

            BackHandler {
                handleCancel()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appBg)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(30.dp))
                    
                    // Header Area
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "VERIFICATION REQUIRED",
                            fontFamily = PoppinsFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = textTertiary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val title = if (isSettingsLock) "Settings Protection" else {
                            val appName = targetPackage?.let { getAppName(it) } ?: "App"
                            if ("section" == blockScope) {
                                when {
                                    targetPackage?.contains("instagram") == true -> "Instagram Reels"
                                    targetPackage?.contains("youtube") == true -> "YouTube Shorts"
                                    targetPackage?.contains("snapchat") == true -> "Snapchat Highlights"
                                    else -> "$appName Section"
                                } + " Restricted"
                            } else {
                                "$appName is Restricted"
                            }
                        }
                        Text(
                            text = title,
                            fontFamily = PoppinsFamily,
                            fontSize = 28.sp,
                            color = textPrimary,
                            lineHeight = 34.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Challenge UI Card
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = themeColor(R.attr.surface_card, Color.White)
                        ),
                        border = BorderStroke(1.dp, themeColor(R.attr.glass_stroke, Color(0xFFE2E8F0))),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.shield),
                                contentDescription = "Shield Icon",
                                tint = brandPink,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            val subtitle = when (lockType) {
                                "math" -> "Solve the equation to proceed"
                                "scream" -> "Hold a loud scream or blow for 3 continuous seconds"
                                "breath" -> "Hold down the button for 10 seconds to think"
                                "text" -> "Type the quote exactly to proceed"
                                "shake" -> "Shake your phone vigorously for 12 seconds"
                                "oneday" -> "This target is locked under strict lockout protection."
                                "window10" -> {
                                    val mins = challengeLockMgr.bypassDurationMinutes
                                    "Activate a single $mins minute bypass window."
                                }
                                "pin_reset" -> "Enforcing high-friction PIN Reset options"
                                else -> "Complete the challenge to unlock access"
                            }

                            Text(
                                text = subtitle,
                                fontFamily = InterFamily,
                                fontSize = 14.sp,
                                color = textSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Switch on the selected challenge type
                            when (lockType) {
                                "math" -> MathChallengeCompose(PoppinsFamily, InterFamily, brandPink, textPrimary)
                                "scream" -> ScreamChallengeCompose(InterFamily, brandPink, textPrimary)
                                "breath" -> BreathChallengeCompose(PoppinsFamily, brandPink, textPrimary)
                                "text" -> TextChallengeCompose(PoppinsFamily, InterFamily, brandPink, textPrimary, textSecondary)
                                "shake" -> ShakeChallengeCompose(InterFamily, brandPink, textPrimary)
                                "oneday" -> OneDayChallengeCompose(PoppinsFamily, brandPink, textPrimary)
                                "window10" -> Window10ChallengeCompose(InterFamily, brandPink, textPrimary)
                                "pin_reset" -> PinResetChallengeCompose(PoppinsFamily, InterFamily, brandPink, textPrimary, textSecondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Footer Buttons
                    if (lockType == "math" || (lockType == "text" && !isPinResetTypingMode.value)) {
                        Button(
                            onClick = { verifyAndSubmit() },
                            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = brandPink)
                        ) {
                            Text("Verify Challenge", fontFamily = PoppinsFamily, fontSize = 16.sp, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    TextButton(
                        onClick = { handleCancel() },
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = "Go Back",
                            fontFamily = PoppinsFamily,
                            fontSize = 15.sp,
                            color = brandPink
                        )
                    }
                }
            }
        }
    }

    private fun initChallengeData() {
        when (lockType) {
            "math" -> setupMathChallenge()
            "scream" -> setupScreamChallenge()
            "breath" -> setupBreathChallenge()
            "text" -> setupTextChallenge(false)
            "shake" -> setupShakeChallenge()
            "oneday" -> setupOneDayLockout()
            "window10" -> {} // no-op init
            "pin_reset" -> {} // no-op init
        }
    }

    private fun updateSecureFlag() {
        if (lockType == "math" || lockType == "text" || lockType == "pin_reset") {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // ================= 1. MATHS CHALLENGE =================

    private fun setupMathChallenge() {
        val type = (0..4).random()
        when (type) {
            0 -> {
                // A² ± B
                val a = (4..15).random()
                val b = (10..100).random()
                val isSubtract = Math.random() > 0.5
                if (isSubtract) {
                    mathCorrectAnswer = (a * a) - b
                    mathEquationText.value = "${a}² - $b = ?"
                } else {
                    mathCorrectAnswer = (a * a) + b
                    mathEquationText.value = "${a}² + $b = ?"
                }
            }
            1 -> {
                // A³ ± B
                val a = (3..8).random()
                val b = (5..50).random()
                val isSubtract = Math.random() > 0.5
                if (isSubtract) {
                    mathCorrectAnswer = (a * a * a) - b
                    mathEquationText.value = "${a}³ - $b = ?"
                } else {
                    mathCorrectAnswer = (a * a * a) + b
                    mathEquationText.value = "${a}³ + $b = ?"
                }
            }
            2 -> {
                // √A ± B²
                val baseA = (4..15).random()
                val a = baseA * baseA
                val b = (3..10).random()
                val isSubtract = Math.random() > 0.5
                if (isSubtract) {
                    mathCorrectAnswer = baseA - (b * b)
                    mathEquationText.value = "√$a - ${b}² = ?"
                } else {
                    mathCorrectAnswer = baseA + (b * b)
                    mathEquationText.value = "√$a + ${b}² = ?"
                }
            }
            3 -> {
                // A³ + √B
                val a = (2..6).random()
                val baseB = (4..12).random()
                val b = baseB * baseB
                mathCorrectAnswer = (a * a * a) + baseB
                mathEquationText.value = "${a}³ + √$b = ?"
            }
            else -> {
                // Keep it simple: (A × B) ± C
                val a = (2..12).random()
                val b = (2..12).random()
                val c = (10..50).random()
                val isSubtract = Math.random() > 0.5
                if (isSubtract) {
                    mathCorrectAnswer = (a * b) - c
                    mathEquationText.value = "($a × $b) - $c = ?"
                } else {
                    mathCorrectAnswer = (a * b) + c
                    mathEquationText.value = "($a × $b) + $c = ?"
                }
            }
        }
        mathAnswerInput.value = ""
    }

    @Composable
    private fun MathChallengeCompose(
        poppinsFamily: FontFamily,
        interFamily: FontFamily,
        brandPink: Color,
        textPrimary: Color
    ) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = mathEquationText.value,
                fontFamily = poppinsFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = textPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = mathAnswerInput.value,
                onValueChange = { mathAnswerInput.value = it },
                label = { Text("Answer", fontFamily = interFamily) },
                singleLine = true,
                modifier = Modifier.width(180.dp).focusRequester(focusRequester),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = brandPink,
                    unfocusedBorderColor = textPrimary.copy(alpha = 0.3f),
                    focusedLabelColor = brandPink,
                    unfocusedLabelColor = textPrimary.copy(alpha = 0.6f),
                    cursorColor = brandPink,
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary
                )
            )
        }
    }

    // ================= 2. SCREAM CHALLENGE =================

    private fun setupScreamChallenge() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Request permissions using standard compose/accessibility utils
            Utils.showPermissionSheet(this, Utils.PermissionType.AUDIO,
                object : Utils.PermissionLauncher {
                    override fun launchAccessibility(intent: Intent?) {}
                    override fun launchBattery(intent: Intent?) {}
                    override fun launchNotification(permission: String) {
                        micPermissionLauncher.launch(permission)
                    }
                }) {
                Toast.makeText(this, "Microphone permission required for Scream challenge.", Toast.LENGTH_LONG).show()
                handleCancel()
            }
        } else {
            startScreamDetection()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScreamDetection() {
        if (isRecording) return
        isRecording = true

        val sampleRate = 8000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBufferSize * 2)

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed.")
            isRecording = false
            return
        }

        audioRecord?.startRecording()

        recordingThread = Thread({
            val buffer = ShortArray(minBufferSize)
            while (isRecording) {
                val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readSize > 0) {
                    var sum = 0.0
                    for (i in 0 until readSize) {
                        sum += (buffer[i] * buffer[i]).toDouble()
                    }
                    val amplitude = Math.sqrt(sum / readSize)
                    val progress = Math.min(100, ((amplitude / 4000.0) * 100).toInt())

                    handler.post { updateScreamProgress(progress) }
                }
            }
        }, "ScreamDetectionThread")

        recordingThread?.start()
    }

    private fun updateScreamProgress(currentVolume: Int) {
        if (!isRecording) return

        screamVolume.intValue = currentVolume

        // B6: Use actual elapsed time between updates instead of a fixed 100ms increment
        val now = System.currentTimeMillis()
        val delta = if (lastScreamUpdateMs > 0L) (now - lastScreamUpdateMs).toDouble() else 100.0
        lastScreamUpdateMs = now

        if (currentVolume >= 80) {
            screamSustainedMs += delta
            val timeLeft = Math.max(0.0, (SCREAM_TARGET_MS - screamSustainedMs) / 1000.0)
            screamTimeLeft.value = String.format(Locale.US, "Hold it: %.1fs", timeLeft)

            if (screamSustainedMs >= SCREAM_TARGET_MS) {
                stopScreamDetection()
                triggerSuccess()
            }
        } else {
            screamSustainedMs = 0.0
            lastScreamUpdateMs = 0L  // Reset tracking when interrupted
            screamTimeLeft.value = "Hold it: 3.0s"
        }
    }

    private fun stopScreamDetection() {
        isRecording = false
        recordingThread?.interrupt()
        recordingThread = null
        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio recorder", e)
        }
        audioRecord = null
    }

    @Composable
    private fun ScreamChallengeCompose(
        interFamily: FontFamily,
        brandPink: Color,
        textPrimary: Color
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Scream or blow into mic!",
                fontFamily = interFamily,
                fontSize = 16.sp,
                color = textPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LinearProgressIndicator(
                progress = { screamVolume.intValue.toFloat() / 100f },
                modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp)),
                color = brandPink,
                trackColor = themeColor(R.attr.surface_nested, Color(0xFFF1F5F9))
            )
            
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = screamTimeLeft.value,
                fontFamily = interFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = textPrimary
            )
        }
    }

    // ================= 3. HOLD BREATH CHALLENGE =================

    private fun setupBreathChallenge() {
        breathRemainingMs.doubleValue = 10000.0
        isHoldingBreath.value = false
    }

    private fun startBreathHoldCountdown() {
        handler.post(object : Runnable {
            override fun run() {
                if (!isHoldingBreath.value) return

                breathRemainingMs.doubleValue -= 100.0
                // B9: removed dead `val displaySec` — composable reads breathRemainingMs directly

                if (breathRemainingMs.doubleValue <= 0) {
                    isHoldingBreath.value = false
                    breathSucceeded = true  // E7: set flag before triggerSuccess
                    triggerSuccess()
                } else {
                    handler.postDelayed(this, 100)
                }
            }
        })
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    private fun BreathChallengeCompose(
        poppinsFamily: FontFamily,
        brandPink: Color,
        textPrimary: Color
    ) {
        val displaySec = Math.max(0.0, breathRemainingMs.doubleValue / 1000.0)
        val buttonScale by animateFloatAsState(
            targetValue = if (isHoldingBreath.value) 0.9f else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = String.format(Locale.US, "%.1fs", displaySec),
                fontFamily = poppinsFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                color = textPrimary,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(buttonScale)
                    .clip(CircleShape)
                    .background(brandPink)
                    .pointerInteropFilter { event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                isHoldingBreath.value = true
                                vibrate(40)
                                startBreathHoldCountdown()
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                isHoldingBreath.value = false
                                // E7: check breathSucceeded flag to avoid reset-after-success race
                                if (breathRemainingMs.doubleValue > 0 && !breathSucceeded) {
                                    vibrate(120)
                                    Toast.makeText(this@LockChallengeActivity, "Don't let go! Resetting...", Toast.LENGTH_SHORT).show()
                                    breathRemainingMs.doubleValue = 10000.0
                                }
                            }
                        }
                        true
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "HOLD",
                    fontFamily = poppinsFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )
            }
        }
    }

    // ================= 4. TEXT TYPING CHALLENGE =================

    private fun setupTextChallenge(pinResetMode: Boolean) {
        isPinResetTypingMode.value = pinResetMode
        val baseQuote = if (pinResetMode) {
            PIN_RESET_BYPASS_PARAGRAPH
        } else {
            val index = QUOTES.indices.random()
            QUOTES[index]
        }
        quoteToType.value = makeTextFrictionPremium(baseQuote)
        typedInputText.value = ""
    }

    private fun makeTextFrictionPremium(input: String): String {
        var text = input.replace("—", " ").replace("-", " ")
        val words = text.split(" ")
        val processedWords = words.map { word ->
            if (word.length < 3) return@map word
            val sb = java.lang.StringBuilder()
            for (i in word.indices) {
                val c = word[i]
                if (c.isLetter()) {
                    if (i > 0 && (0..3).random() == 0) {
                        sb.append(c.uppercaseChar())
                    } else {
                        sb.append(c)
                    }
                } else {
                    sb.append(c)
                }
            }
            sb.toString()
        }
        
        val sb = java.lang.StringBuilder()
        val symbols = arrayOf("@", "#", "%")
        for (i in processedWords.indices) {
            sb.append(processedWords[i])
            if (i < processedWords.size - 1) {
                if ((0..6).random() == 0) {
                    sb.append(" ").append(symbols.random()).append(" ")
                } else {
                    sb.append(" ")
                }
            }
        }
        return sb.toString().trim().replace("  ", " ")
    }

    @Composable
    private fun TextChallengeCompose(
        poppinsFamily: FontFamily,
        interFamily: FontFamily,
        brandPink: Color,
        textPrimary: Color,
        textSecondary: Color
    ) {
        // F2/F3: Compute live match stats for progress feedback
        val typed = typedInputText.value
        val target = quoteToType.value
        val matchedChars = typed.zip(target).count { (a, b) -> a == b }
        val progressFraction = if (target.isNotEmpty()) matchedChars.toFloat() / target.length else 0f
        
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(themeColor(R.attr.surface_nested, Color(0xFFF8FAFC)))
                    .padding(16.dp)
            ) {
                Text(
                    text = target,
                    fontFamily = interFamily,
                    fontSize = 15.sp,
                    color = textSecondary,
                    textAlign = TextAlign.Start
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // F3: Progress bar + match counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = if (progressFraction >= 1f) Color(0xFF22C55E) else brandPink,
                    trackColor = themeColor(R.attr.surface_nested, Color(0xFFF1F5F9))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "$matchedChars / ${target.length}",
                    fontFamily = interFamily,
                    fontSize = 12.sp,
                    color = if (progressFraction >= 1f) Color(0xFF22C55E) else textSecondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = typedInputText.value,
                onValueChange = { typedInputText.value = it },
                label = { Text("Type the text above exactly...", fontFamily = interFamily) },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = brandPink,
                    unfocusedBorderColor = textPrimary.copy(alpha = 0.3f),
                    focusedLabelColor = brandPink,
                    unfocusedLabelColor = textPrimary.copy(alpha = 0.6f),
                    cursorColor = brandPink,
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary
                )
            )

            if (isPinResetTypingMode.value) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { verifyAndSubmit() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = brandPink)
                ) {
                    Text("Verify Bypass Text", fontFamily = poppinsFamily, fontSize = 16.sp, color = Color.White)
                }
            }
        }
    }

    // ================= 5. SHAKE TO UNLOCK CHALLENGE =================

    private fun setupShakeChallenge() {
        shakeProgress.floatValue = 0f
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)

        // Decay handler loop
        handler.post(object : Runnable {
            override fun run() {
                if (lockType == "shake" && shakeProgress.floatValue > 0) {
                    shakeProgress.floatValue = Math.max(0f, shakeProgress.floatValue - 0.5f)
                }
                handler.postDelayed(this, 100)
            }
        })
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || lockType != "shake") return

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val curTime = System.currentTimeMillis()
            if ((curTime - lastSensorUpdate) > 100) {
                val diffTime = curTime - lastSensorUpdate
                lastSensorUpdate = curTime

                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val speed = Math.abs(x + y + z - lastX - lastY - lastZ) / diffTime * 10000

                if (speed > 800) {
                    shakeProgress.floatValue = Math.min(100.0f, shakeProgress.floatValue + 8.0f)

                    if (shakeProgress.floatValue >= 100.0f) {
                        sensorManager?.unregisterListener(this)
                        triggerSuccess()
                    }
                }
                lastX = x
                lastY = y
                lastZ = z
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @Composable
    private fun ShakeChallengeCompose(
        interFamily: FontFamily,
        brandPink: Color,
        textPrimary: Color
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Shake your phone vigorously!",
                fontFamily = interFamily,
                fontSize = 16.sp,
                color = textPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LinearProgressIndicator(
                progress = { shakeProgress.floatValue / 100f },
                modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp)),
                color = brandPink,
                trackColor = themeColor(R.attr.surface_nested, Color(0xFFF1F5F9))
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "${shakeProgress.floatValue.toInt()}%",
                fontFamily = interFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = textPrimary
            )
        }
    }

    // ================= 6. 1-DAY LOCKOUT =================

    private fun setupOneDayLockout() {
        // E3: Store runnable reference so it can be cancelled in onDestroy
        val runnable = object : Runnable {
            override fun run() {
                var isLockActive = false
                var remainingMs = 0L
                if (isSettingsLock) {
                    isLockActive = challengeLockMgr.isSettingsOneDayLockActive
                    remainingMs = challengeLockMgr.settingsOneDayLockRemainingMs
                } else if (targetPackage != null) {
                    isLockActive = challengeLockMgr.isBlockerOneDayLockActive(targetPackage)
                    remainingMs = challengeLockMgr.getBlockerOneDayLockRemainingMs(targetPackage)
                }

                if (!isLockActive) {
                    triggerSuccess()
                    return
                }

                if (remainingMs <= 0) {
                    oneDayCountdownText.value = "Clock tampering detected! Cooldown locked."
                    handler.postDelayed(this, 1000)
                    return
                }

                val hours = remainingMs / (60 * 60 * 1000L)
                val minutes = (remainingMs / (60 * 1000L)) % 60
                val seconds = (remainingMs / 1000L) % 60

                oneDayCountdownText.value = String.format(Locale.US, "%02dh %02dm %02ds remaining", hours, minutes, seconds)
                handler.postDelayed(this, 1000)
            }
        }
        oneDayRunnable = runnable
        handler.post(runnable)
    }

    @Composable
    private fun OneDayChallengeCompose(
        poppinsFamily: FontFamily,
        brandPink: Color,
        textPrimary: Color
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Strict Lock Active",
                fontFamily = poppinsFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = brandPink,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = oneDayCountdownText.value,
                fontFamily = poppinsFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = textPrimary
            )
        }
    }

    // ================= 7. ONE-TIME WINDOW CHALLENGE =================

    @Composable
    private fun Window10ChallengeCompose(
        interFamily: FontFamily,
        brandPink: Color,
        textPrimary: Color
    ) {
        val mins = challengeLockMgr.bypassDurationMinutes
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "This can only be activated once per day. Access will close automatically when the timer finishes.",
                fontFamily = interFamily,
                fontSize = 15.sp,
                color = textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            Button(
                onClick = {
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    val keyPkg = targetPackage ?: "settings"
                    
                    if (challengeLockMgr.hasUsed10MinWindowToday(keyPkg, today)) {
                        vibrate(150)
                        Toast.makeText(this@LockChallengeActivity, "You have already used your bypass window for today!", Toast.LENGTH_LONG).show()
                    } else {
                        challengeLockMgr.mark10MinWindowUsed(keyPkg, today)
                        triggerSuccess()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = brandPink)
            ) {
                Text(
                    text = "Activate $mins Min Window",
                    fontFamily = remember { FontFamily(Font(R.font.poppins_semibold)) },
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
        }
    }

    // ================= 8. PIN RESET CHALLENGE =================

    @Composable
    private fun PinResetChallengeCompose(
        poppinsFamily: FontFamily,
        interFamily: FontFamily,
        brandPink: Color,
        textPrimary: Color,
        textSecondary: Color
    ) {
        // F1: Show a live countdown if the cooldown is already active
        var cooldownActive by remember { mutableStateOf(challengeLockMgr.isPinResetCooldownActive()) }
        var cooldownText by remember { mutableStateOf("") }
        var showCooldownConfirm by remember { mutableStateOf(false) }  // F5

        LaunchedEffect(cooldownActive) {
            if (cooldownActive) {
                while (true) {
                    val remaining = challengeLockMgr.pinResetRemainingMs
                    if (remaining <= 0) { cooldownActive = false; break }
                    val h = remaining / 3_600_000L
                    val m = (remaining / 60_000L) % 60
                    val s = (remaining / 1_000L) % 60
                    cooldownText = "%02dh %02dm %02ds".format(h, m, s)
                    delay(1000L)
                }
            }
        }

        // F5: Confirmation dialog for starting cooldown
        if (showCooldownConfirm) {
            AlertDialog(
                onDismissRequest = { showCooldownConfirm = false },
                title = { Text("Start 24h Cooldown?", fontFamily = poppinsFamily, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "This will lock PIN reset for 24 hours. You will not be able to reset your PIN until the cooldown expires. Are you sure?",
                        fontFamily = interFamily, fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showCooldownConfirm = false
                            challengeLockMgr.startPinResetCooldown()
                            cooldownActive = true
                            Toast.makeText(this@LockChallengeActivity, "24-Hour Cooldown started. Check back tomorrow.", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = brandPink)
                    ) { Text("Start Cooldown", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showCooldownConfirm = false }) { Text("Cancel", color = brandPink) }
                }
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (cooldownActive) {
                // F1: Show countdown-only screen when cooldown is already running
                Icon(
                    painter = painterResource(R.drawable.shield),
                    contentDescription = null,
                    tint = brandPink,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "PIN Reset Cooldown Active",
                    fontFamily = poppinsFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = brandPink
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = cooldownText,
                    fontFamily = poppinsFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = textPrimary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Your PIN reset cooldown is in progress. Please wait until it completes, or type the bypass paragraph below.",
                    fontFamily = interFamily,
                    fontSize = 13.sp,
                    color = textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { 
                        lockType = "text"
                        updateSecureFlag()
                        setupTextChallenge(true) 
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor(R.attr.surface_nested, Color(0xFFF1F5F9))),
                    border = BorderStroke(1.dp, brandPink)
                ) {
                    Text("Type Bypass Paragraph Instead", fontFamily = poppinsFamily, fontSize = 15.sp, color = brandPink)
                }
            } else {
                Text(
                    text = "Choose to start a 24-hour wait, or bypass it by typing a very long paragraph correctly.",
                    fontFamily = interFamily,
                    fontSize = 14.sp,
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                Button(
                    onClick = { showCooldownConfirm = true },  // F5: show confirm dialog
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = brandPink)
                ) {
                    Text("Start 24h Cooldown", fontFamily = poppinsFamily, fontSize = 16.sp, color = Color.White)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "— OR —",
                    fontFamily = poppinsFamily,
                    fontSize = 12.sp,
                    color = textSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { 
                        lockType = "text"
                        updateSecureFlag()
                        setupTextChallenge(true) 
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor(R.attr.surface_nested, Color(0xFFF1F5F9))),
                    border = BorderStroke(1.dp, brandPink)
                ) {
                    Text("Type Bypass Paragraph", fontFamily = poppinsFamily, fontSize = 16.sp, color = brandPink)
                }
            }
        }
    }

    // ================= VERIFICATION & LIFECYCLE =================

    private fun verifyAndSubmit() {
        when (lockType) {
            "math" -> {
                val input = mathAnswerInput.value.trim()
                if (input.isEmpty()) {
                    Toast.makeText(this, "Please enter an answer", Toast.LENGTH_SHORT).show()
                    return
                }
                try {
                    val userAns = input.toInt()
                    if (userAns == mathCorrectAnswer) {
                        triggerSuccess()
                    } else {
                        vibrate(150)
                        Toast.makeText(this, "Incorrect. Generating a new equation.", Toast.LENGTH_SHORT).show()
                        setupMathChallenge()
                    }
                } catch (e: NumberFormatException) {
                    Toast.makeText(this, "Invalid format", Toast.LENGTH_SHORT).show()
                }
            }
            "text" -> {
                val input = typedInputText.value.trim()
                if (input == quoteToType.value.trim()) {  // B7: trim both sides
                    if (isPinResetTypingMode.value) {
                        // F6: Clear PIN then immediately prompt for a new one
                        val lockMgr = SettingsLockManager(this)
                        lockMgr.clearCustomPin()
                        challengeLockMgr.clearPinResetCooldown()
                        Toast.makeText(this, "Old PIN cleared. Please set a new PIN to re-enable your lock.", Toast.LENGTH_SHORT).show()
                        // Show set-PIN dialog; on completion re-enable the lock
                        lockMgr.showSetCustomPinDialog(this@LockChallengeActivity, false, {
                            lockMgr.isLockEnabled = true
                            lockMgr.setLockType(SettingsLockManager.LOCK_TYPE_CUSTOM)
                            Toast.makeText(this@LockChallengeActivity, "New PIN set! Settings lock re-enabled.", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        }, {
                            // User cancelled PIN setup — lock stays disabled
                            Toast.makeText(this@LockChallengeActivity, "PIN not set. Settings lock has been disabled.", Toast.LENGTH_LONG).show()
                            setResult(RESULT_OK)
                            finish()
                        })
                    } else {
                        triggerSuccess()
                    }
                } else {
                    vibrate(150)
                    Toast.makeText(this, "Text mismatch. Pay attention to spaces and symbols.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun triggerSuccess() {
        vibrate(80)
        Toast.makeText(this, "Verification Successful!", Toast.LENGTH_SHORT).show()

        if (isSettingsLock) {
            val intent = Intent("com.gxdevs.mindmint.action.CHALLENGE_RESOLVED")
            intent.setPackage(packageName)
            sendBroadcast(intent)
        } else {
            targetPackage?.let { pkg ->
                val durationMinutes = challengeLockMgr.bypassDurationMinutes
                // Grant bypass synchronously first to prevent race conditions during activity transition
                AppUsageAccessibilityService.grantBypass(pkg, durationMinutes)
                val intent = Intent("com.gxdevs.mindmint.action.APP_BYPASS_GRANTED").apply {
                    putExtra("package_name", pkg)
                    putExtra("duration_minutes", durationMinutes)
                    setPackage(packageName)
                }
                sendBroadcast(intent)
            }
        }

        setResult(RESULT_OK)
        finish()
    }

    private fun handleCancel() {
        if (!isSettingsLock) {
            val closeAppIntent = Intent(AppUsageAccessibilityService.ACTION_PERFORM_GLOBAL_HOME_FROM_OVERLAY)
            closeAppIntent.setPackage(packageName)
            sendBroadcast(closeAppIntent)
        }
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun vibrate(ms: Long) {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(ms)
            }
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            "This App"
        }
    }

    @Composable
    fun themeColor(attr: Int, default: Color): Color {
        val context = LocalContext.current
        val typedValue = remember { android.util.TypedValue() }
        val resolved = context.theme.resolveAttribute(attr, typedValue, true)
        return if (resolved) {
            Color(typedValue.data)
        } else {
            default
        }
    }

    override fun onStop() {
        super.onStop()
        stopScreamDetection()
        sensorManager?.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        // E3: Cancel the one-day countdown loop to prevent handler leaks
        oneDayRunnable?.let { handler.removeCallbacks(it) }
    }
}
