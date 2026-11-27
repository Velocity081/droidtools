package com.droidbubble.app.tools

import android.app.ActivityManager
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.droidbubble.app.droidbar.BarAppInfo
import com.droidbubble.app.droidbar.MostUsedAppsHelper
import com.droidbubble.app.ui.main.CookieIconBox
import com.droidbubble.app.ui.main.GroupPosition
import com.droidbubble.app.ui.main.ProgressiveTopBlurBox
import com.droidbubble.app.ui.main.SectionHeader
import com.droidbubble.app.ui.main.SettingsCardItem
import com.droidbubble.app.ui.main.SliderSettingItem
import com.droidbubble.app.ui.main.getHorizontalSegmentShape
import com.droidbubble.app.util.ShizukuHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private fun formatTime24(hour: Int, minute: Int): String {
    return String.format(Locale.US, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))
}

@Composable
private fun WriteSettingsPermissionBanner(
    canWriteSettings: Boolean,
    hasPrivilegedAccess: Boolean,
    requireShizukuBanner: Boolean = false,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!canWriteSettings && !requireShizukuBanner) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            ) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(
                            "Modify System Settings",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    },
                    leadingContent = {
                        CookieIconBox(icon = Icons.Default.Tune)
                    },
                    trailingContent = {
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    runCatching {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                            Uri.parse("package:${context.packageName}")
                                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                        context.startActivity(intent)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Grant", fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
            }
        }

        if (!hasPrivilegedAccess) {
            val isShizukuRunning = ShizukuHelper.isShizukuAvailable()
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(
                            if (isShizukuRunning) "Authorize Shizuku" else "Shizuku Recommended",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingContent = {
                        CookieIconBox(icon = Icons.Default.Security)
                    },
                    trailingContent = {
                        FilledTonalButton(
                            onClick = {
                                if (isShizukuRunning) {
                                    if (!ShizukuHelper.hasShizukuPermission()) {
                                        ShizukuHelper.requestShizukuPermission()
                                    } else {
                                        ShizukuHelper.grantWriteSecureSettings(context.packageName) { ok ->
                                            onRefresh()
                                            Toast.makeText(
                                                context,
                                                if (ok) "Privileged access granted!" else "Shizuku active",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                } else {
                                    ShizukuHelper.openShizukuOrStore(context)
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                if (isShizukuRunning) "Connect" else "Shizuku",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )
            }
        }
    }
}

// =============================================================================
// 1. DISPLAY SETTINGS PAGE
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettingsPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember { SystemToolsManager.getInstance(context) }

    val routines by manager.brightnessRoutines.collectAsState()
    val nightLightConfig by manager.nightLightConfig.collectAsState()

    var canWriteSettings by remember { mutableStateOf(manager.canWriteSystemSettings()) }
    var hasPrivileged by remember { mutableStateOf(manager.hasPrivilegedAccess()) }

    var brightnessPercent by remember { mutableFloatStateOf(manager.getCurrentBrightnessPercent().toFloat()) }
    var aodEnabled by remember { mutableStateOf(manager.isAlwaysOnDisplayEnabled()) }
    var statusBarHidden by remember { mutableStateOf(manager.isStatusBarHidden()) }
    var timeoutStepIndex by remember { mutableIntStateOf(manager.getCurrentScreenTimeoutIndex()) }
    var windowScale by remember { mutableFloatStateOf(manager.getWindowAnimationScale()) }
    var transitionScale by remember { mutableFloatStateOf(manager.getTransitionAnimationScale()) }
    var animatorScale by remember { mutableFloatStateOf(manager.getAnimatorDurationScale()) }

    var showRoutineDialog by remember { mutableStateOf(false) }
    var editingRoutine by remember { mutableStateOf<BrightnessThemeRoutine?>(null) }
    var showNightLightAppsSheet by remember { mutableStateOf(false) }

    fun refreshSystemValues() {
        canWriteSettings = manager.canWriteSystemSettings()
        hasPrivileged = manager.hasPrivilegedAccess()
        brightnessPercent = manager.getCurrentBrightnessPercent().toFloat()
        aodEnabled = manager.isAlwaysOnDisplayEnabled()
        statusBarHidden = manager.isStatusBarHidden()
        timeoutStepIndex = manager.getCurrentScreenTimeoutIndex()
        windowScale = manager.getWindowAnimationScale()
        transitionScale = manager.getTransitionAnimationScale()
        animatorScale = manager.getAnimatorDurationScale()
        if (ShizukuHelper.isShizukuAvailable() && ShizukuHelper.hasShizukuPermission() && !ShizukuHelper.hasWriteSecureSettings(context)) {
            ShizukuHelper.grantWriteSecureSettings(context.packageName) {
                hasPrivileged = manager.hasPrivilegedAccess()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshSystemValues()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Display Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        val displayListState = rememberLazyListState()
        val displayIsScrolled by remember {
            derivedStateOf {
                displayListState.firstVisibleItemIndex > 0 || displayListState.firstVisibleItemScrollOffset > 6
            }
        }
        ProgressiveTopBlurBox(
            isScrolled = displayIsScrolled,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        LazyColumn(
            state = displayListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!canWriteSettings || !hasPrivileged) {
                item {
                    WriteSettingsPermissionBanner(
                        canWriteSettings = canWriteSettings,
                        hasPrivilegedAccess = hasPrivileged,
                        requireShizukuBanner = false,
                        onRefresh = { refreshSystemValues() }
                    )
                }
            }

            // SECTION 1: BRIGHTNESS
            item {
                SectionHeader(title = "Brightness")
            }

            item {
                SettingsCardItem(position = GroupPosition.SINGLE) {
                    SliderSettingItem(
                        label = "Brightness",
                        value = brightnessPercent,
                        unit = "%",
                        valueRange = 0f..100f,
                        onValueChange = { newPct ->
                            brightnessPercent = newPct
                            manager.setSystemBrightnessPercent(newPct.roundToInt())
                        }
                    )
                }
            }

            // SECTION 2: BRIGHTNESS ROUTINES
            item {
                SectionHeader(
                    title = "Brightness Routines",
                    actionLabel = "Add Routine",
                    actionIcon = Icons.Default.Add,
                    onActionClick = {
                        editingRoutine = null
                        showRoutineDialog = true
                    }
                )
            }

            item {
                if (routines.isEmpty()) {
                    SettingsCardItem(position = GroupPosition.SINGLE) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("No routines configured", fontWeight = FontWeight.SemiBold) },
                            leadingContent = {
                                CookieIconBox(icon = Icons.Default.Schedule)
                            },
                            trailingContent = {
                                FilledTonalButton(
                                    onClick = {
                                        editingRoutine = null
                                        showRoutineDialog = true
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Create")
                                }
                            }
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        routines.forEachIndexed { idx, routine ->
                            val pos = when {
                                routines.size == 1 -> GroupPosition.SINGLE
                                idx == 0 -> GroupPosition.TOP
                                idx == routines.size - 1 -> GroupPosition.BOTTOM
                                else -> GroupPosition.MIDDLE
                            }
                            SettingsCardItem(position = pos, isActive = routine.enabled) {
                                ListItem(
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.clickable {
                                        editingRoutine = routine
                                        showRoutineDialog = true
                                    },
                                    headlineContent = {
                                        Text(routine.name, fontWeight = FontWeight.SemiBold)
                                    },
                                    leadingContent = {
                                        CookieIconBox(
                                            icon = Icons.Default.Schedule,
                                            isChecked = routine.enabled
                                        )
                                    },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    manager.runBrightnessRoutineNow(routine)
                                                    brightnessPercent = routine.brightnessPercent.toFloat()
                                                    Toast.makeText(context, "Applied ${routine.name}", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Apply now",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Switch(
                                                checked = routine.enabled,
                                                onCheckedChange = { en ->
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    manager.saveBrightnessRoutine(routine.copy(enabled = en))
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 3: NIGHT LIGHT (SCHEDULE + APP TRIGGER)
            item {
                SectionHeader(title = "Night Light")
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SettingsCardItem(position = GroupPosition.TOP, isActive = nightLightConfig.isCurrentlyOn) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("Night Light", fontWeight = FontWeight.SemiBold) },
                            leadingContent = {
                                CookieIconBox(
                                    icon = Icons.Default.NightlightRound,
                                    isChecked = nightLightConfig.isCurrentlyOn
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = nightLightConfig.isCurrentlyOn,
                                    onCheckedChange = { on ->
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        manager.setNightLightActivated(on)
                                    }
                                )
                            }
                        )
                    }

                    SettingsCardItem(position = GroupPosition.MIDDLE) {
                        SliderSettingItem(
                            label = "Night Light Warmth",
                            value = nightLightConfig.intensityPercent.toFloat(),
                            unit = "%",
                            valueRange = 0f..100f,
                            onValueChange = { pct ->
                                manager.setNightLightIntensity(pct.roundToInt())
                            }
                        )
                    }

                    SettingsCardItem(position = GroupPosition.MIDDLE, isActive = nightLightConfig.scheduleEnabled) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Custom Routined Night Light", fontWeight = FontWeight.SemiBold) },
                                leadingContent = {
                                    CookieIconBox(
                                        icon = Icons.Default.Alarm,
                                        isChecked = nightLightConfig.scheduleEnabled
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = nightLightConfig.scheduleEnabled,
                                        onCheckedChange = { sched ->
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            manager.updateNightLightConfig { it.copy(scheduleEnabled = sched) }
                                        }
                                    )
                                }
                            )

                            if (nightLightConfig.scheduleEnabled) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            TimePickerDialog(
                                                context,
                                                { _, h, m ->
                                                    manager.updateNightLightConfig { it.copy(startHour = h, startMinute = m) }
                                                },
                                                nightLightConfig.startHour,
                                                nightLightConfig.startMinute,
                                                true
                                            ).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Start: ${formatTime24(nightLightConfig.startHour, nightLightConfig.startMinute)}")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            TimePickerDialog(
                                                context,
                                                { _, h, m ->
                                                    manager.updateNightLightConfig { it.copy(endHour = h, endMinute = m) }
                                                },
                                                nightLightConfig.endHour,
                                                nightLightConfig.endMinute,
                                                true
                                            ).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("End: ${formatTime24(nightLightConfig.endHour, nightLightConfig.endMinute)}")
                                    }
                                }
                            }
                        }
                    }

                    val appsTriggerOn = nightLightConfig.turnOnForAppsEnabled
                    SettingsCardItem(
                        position = if (appsTriggerOn) GroupPosition.MIDDLE else GroupPosition.BOTTOM,
                        isActive = appsTriggerOn
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("Turn On When Entering Apps", fontWeight = FontWeight.SemiBold) },
                            leadingContent = {
                                CookieIconBox(
                                    icon = Icons.Default.Apps,
                                    isChecked = nightLightConfig.turnOnForAppsEnabled
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = nightLightConfig.turnOnForAppsEnabled,
                                    onCheckedChange = { enabled ->
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        manager.updateNightLightConfig { it.copy(turnOnForAppsEnabled = enabled) }
                                    }
                                )
                            }
                        )
                    }

                    if (appsTriggerOn) {
                        SettingsCardItem(position = GroupPosition.BOTTOM) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { showNightLightAppsSheet = true },
                                headlineContent = { Text("Select Trigger Apps", fontWeight = FontWeight.SemiBold) },
                                leadingContent = {
                                    CookieIconBox(icon = Icons.Default.Tune)
                                },
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = "Select Apps",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // SECTION 4: SCREEN & LOCK DISPLAY (AOD + HIDE STATUS BAR + TIMEOUT)
            item {
                SectionHeader(title = "Screen & Lock Display")
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SettingsCardItem(position = GroupPosition.TOP, isActive = aodEnabled) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("Always-On Display", fontWeight = FontWeight.SemiBold) },
                            supportingContent = {
                                Text(
                                    text = "Only available if your device supports AOD",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = {
                                CookieIconBox(
                                    icon = Icons.Default.Smartphone,
                                    isChecked = aodEnabled
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = aodEnabled,
                                    onCheckedChange = { enabled ->
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        aodEnabled = enabled
                                        manager.setAlwaysOnDisplay(enabled)
                                    }
                                )
                            }
                        )
                    }

                    SettingsCardItem(position = GroupPosition.MIDDLE, isActive = statusBarHidden) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("Hide Status Bar", fontWeight = FontWeight.SemiBold) },
                            leadingContent = {
                                CookieIconBox(
                                    icon = Icons.Default.VisibilityOff,
                                    isChecked = statusBarHidden
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = statusBarHidden,
                                    onCheckedChange = { hidden ->
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        statusBarHidden = hidden
                                        manager.setStatusBarHidden(hidden)
                                    }
                                )
                            }
                        )
                    }

                    SettingsCardItem(position = GroupPosition.BOTTOM) {
                        val currentMs = manager.timeoutStepsMs.getOrElse(timeoutStepIndex) { 60_000 }
                        val formattedLabel = manager.formatTimeoutStep(currentMs)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Screen Timeout",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = formattedLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = timeoutStepIndex.toFloat(),
                                onValueChange = { idxFloat ->
                                    val newIdx = idxFloat.roundToInt().coerceIn(0, manager.timeoutStepsMs.lastIndex)
                                    if (newIdx != timeoutStepIndex) {
                                        timeoutStepIndex = newIdx
                                        manager.setScreenTimeoutByIndex(newIdx)
                                    }
                                },
                                valueRange = 0f..manager.timeoutStepsMs.lastIndex.toFloat(),
                                steps = manager.timeoutStepsMs.size - 2,
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                }
            }

            // SECTION 5: SYSTEM ANIMATION SCALES
            item {
                SectionHeader(
                    title = "Animation Scales",
                    actionLabel = "Reset 1x",
                    actionIcon = Icons.Default.Refresh,
                    onActionClick = {
                        windowScale = 1.0f
                        transitionScale = 1.0f
                        animatorScale = 1.0f
                        manager.setAnimationScale(Settings.Global.WINDOW_ANIMATION_SCALE, 1.0f)
                        manager.setAnimationScale(Settings.Global.TRANSITION_ANIMATION_SCALE, 1.0f)
                        manager.setAnimationScale(Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f)
                    }
                )
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SettingsCardItem(position = GroupPosition.TOP) {
                        SliderSettingItem(
                            label = "Window Animation Scale",
                            value = windowScale,
                            unit = "x",
                            valueRange = 0f..2.0f,
                            steps = 19,
                            onValueChange = { v ->
                                val rounded = (v * 10f).roundToInt() / 10f
                                windowScale = rounded
                                manager.setAnimationScale(Settings.Global.WINDOW_ANIMATION_SCALE, rounded)
                            }
                        )
                    }

                    SettingsCardItem(position = GroupPosition.MIDDLE) {
                        SliderSettingItem(
                            label = "Transition Animation Scale",
                            value = transitionScale,
                            unit = "x",
                            valueRange = 0f..2.0f,
                            steps = 19,
                            onValueChange = { v ->
                                val rounded = (v * 10f).roundToInt() / 10f
                                transitionScale = rounded
                                manager.setAnimationScale(Settings.Global.TRANSITION_ANIMATION_SCALE, rounded)
                            }
                        )
                    }

                    SettingsCardItem(position = GroupPosition.BOTTOM) {
                        SliderSettingItem(
                            label = "Animator Duration Scale",
                            value = animatorScale,
                            unit = "x",
                            valueRange = 0f..2.0f,
                            steps = 19,
                            onValueChange = { v ->
                                val rounded = (v * 10f).roundToInt() / 10f
                                animatorScale = rounded
                                manager.setAnimationScale(Settings.Global.ANIMATOR_DURATION_SCALE, rounded)
                            }
                        )
                    }
                }
            }
        }
        }
    }

    if (showRoutineDialog) {
        BrightnessRoutineEditorDialog(
            initialRoutine = editingRoutine,
            onDismiss = {
                showRoutineDialog = false
                editingRoutine = null
            },
            onSave = { saved ->
                manager.saveBrightnessRoutine(saved)
                showRoutineDialog = false
                editingRoutine = null
            },
            onDelete = if (editingRoutine != null) {
                {
                    manager.deleteBrightnessRoutine(editingRoutine!!.id)
                    showRoutineDialog = false
                    editingRoutine = null
                }
            } else null
        )
    }

    if (showNightLightAppsSheet) {
        MultiAppSelectorDialog(
            title = "Night Light Trigger Apps",
            selectedPackages = nightLightConfig.triggerApps,
            onToggleApp = { app ->
                val current = nightLightConfig.triggerApps.toMutableSet()
                if (current.contains(app.packageName)) current.remove(app.packageName) else current.add(app.packageName)
                manager.updateNightLightConfig { it.copy(triggerApps = current) }
            },
            onDismiss = { showNightLightAppsSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrightnessRoutineEditorDialog(
    initialRoutine: BrightnessThemeRoutine?,
    onDismiss: () -> Unit,
    onSave: (BrightnessThemeRoutine) -> Unit,
    onDelete: (() -> Unit)?
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialRoutine?.name ?: "Night Routine") }
    var startHour by remember { mutableIntStateOf(initialRoutine?.startHour ?: 22) }
    var startMinute by remember { mutableIntStateOf(initialRoutine?.startMinute ?: 0) }
    var endHour by remember { mutableIntStateOf(initialRoutine?.endHour ?: 7) }
    var endMinute by remember { mutableIntStateOf(initialRoutine?.endMinute ?: 0) }
    var brightnessPct by remember { mutableFloatStateOf((initialRoutine?.brightnessPercent ?: 20).toFloat()) }
    var restoreBrightnessPct by remember { mutableFloatStateOf((initialRoutine?.restoreBrightnessPercent ?: 65).toFloat()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (initialRoutine == null) "New Brightness Routine" else "Edit Routine",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Routine Name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(context, { _, h, m ->
                            startHour = h
                            startMinute = m
                        }, startHour, startMinute, true).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Start: ${formatTime24(startHour, startMinute)}")
                }
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(context, { _, h, m ->
                            endHour = h
                            endMinute = m
                        }, endHour, endMinute, true).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("End: ${formatTime24(endHour, endMinute)}")
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Routine Brightness", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text("${brightnessPct.roundToInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = brightnessPct,
                        onValueChange = { brightnessPct = it },
                        valueRange = 0f..100f
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Restore Brightness After End", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Text("${restoreBrightnessPct.roundToInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        value = restoreBrightnessPct,
                        onValueChange = { restoreBrightnessPct = it },
                        valueRange = 0f..100f
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Delete", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }
                }
                Button(
                    onClick = {
                        onSave(
                            BrightnessThemeRoutine(
                                id = initialRoutine?.id ?: java.util.UUID.randomUUID().toString(),
                                name = name.ifBlank { "Routine" },
                                enabled = initialRoutine?.enabled ?: true,
                                startHour = startHour,
                                startMinute = startMinute,
                                endHour = endHour,
                                endMinute = endMinute,
                                brightnessPercent = brightnessPct.roundToInt(),
                                themeAction = RoutineThemeAction.NO_CHANGE,
                                restoreBrightnessPercent = restoreBrightnessPct.roundToInt(),
                                restoreThemeAction = RoutineThemeAction.NO_CHANGE
                            )
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiAppSelectorDialog(
    title: String,
    selectedPackages: Set<String>,
    onToggleApp: (BarAppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var allApps by remember { mutableStateOf<List<BarAppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            MostUsedAppsHelper.getAllInstalledApps(context)
        }
        allApps = loaded
        isLoading = false
    }

    val filteredApps = remember(allApps, searchQuery) {
        if (searchQuery.isBlank()) allApps
        else allApps.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search apps...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isSelected = selectedPackages.contains(app.packageName)
                        val iconImg = remember(app.iconBitmap) { app.iconBitmap?.asImageBitmap() }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onToggleApp(app) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (iconImg != null) {
                                    Image(
                                        bitmap = iconImg,
                                        contentDescription = app.label,
                                        modifier = Modifier.size(36.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Apps,
                                        contentDescription = app.label,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onToggleApp(app) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 2. SOUND AND VIBRATIONS PAGE
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundAndVibrationsPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember { SystemToolsManager.getInstance(context) }

    val soundConfig by manager.soundConfig.collectAsState()
    val perAppRules by manager.perAppVolumeRules.collectAsState()

    var hasPrivileged by remember { mutableStateOf(manager.hasPrivilegedAccess()) }
    var showAddAppPicker by remember { mutableStateOf(false) }

    fun refreshState() {
        hasPrivileged = manager.hasPrivilegedAccess()
        if (ShizukuHelper.isShizukuAvailable() && ShizukuHelper.hasShizukuPermission() && !ShizukuHelper.hasWriteSecureSettings(context)) {
            ShizukuHelper.grantWriteSecureSettings(context.packageName) {
                hasPrivileged = manager.hasPrivilegedAccess()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Sound & Vibrations", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        val displayListState = rememberLazyListState()
        val displayIsScrolled by remember {
            derivedStateOf {
                displayListState.firstVisibleItemIndex > 0 || displayListState.firstVisibleItemScrollOffset > 6
            }
        }
        ProgressiveTopBlurBox(
            isScrolled = displayIsScrolled,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        LazyColumn(
            state = displayListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!hasPrivileged) {
                item {
                    WriteSettingsPermissionBanner(
                        canWriteSettings = true,
                        hasPrivilegedAccess = hasPrivileged,
                        requireShizukuBanner = true,
                        onRefresh = { refreshState() }
                    )
                }
            }

            // SECTION 1: VOLUME ENHANCEMENTS
            item {
                SectionHeader(title = "Volume Controls")
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val fasterActive = soundConfig.fasterVolumeSwitching
                    SettingsCardItem(
                        position = GroupPosition.TOP,
                        isActive = fasterActive
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("Faster Volume Switching", fontWeight = FontWeight.SemiBold) },
                            leadingContent = {
                                CookieIconBox(
                                    icon = Icons.Default.Speed,
                                    isChecked = soundConfig.fasterVolumeSwitching
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = soundConfig.fasterVolumeSwitching,
                                    onCheckedChange = { enabled ->
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        manager.updateSoundConfig { it.copy(fasterVolumeSwitching = enabled) }
                                    }
                                )
                            }
                        )
                    }

                    if (fasterActive) {
                        SettingsCardItem(position = GroupPosition.MIDDLE) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Volume Step Multiplier",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val steps = listOf(2 to "2x Fast", 3 to "3x Turbo")
                                    steps.forEachIndexed { idx, (mult, label) ->
                                        val isSelected = soundConfig.fasterVolumeStepSize == mult
                                        val shape = getHorizontalSegmentShape(idx, steps.size, isSelected)
                                        val bg by animateColorAsState(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            label = "step_bg"
                                        )
                                        val fg by animateColorAsState(
                                            if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            label = "step_fg"
                                        )
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(shape)
                                                .clickable {
                                                    manager.updateSoundConfig { it.copy(fasterVolumeStepSize = mult) }
                                                },
                                            shape = shape,
                                            color = bg
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = fg
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SettingsCardItem(
                        position = GroupPosition.BOTTOM,
                        isActive = soundConfig.disableSafeVolumeWarning
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("Disable Safe Volume Warning", fontWeight = FontWeight.SemiBold) },
                            leadingContent = {
                                CookieIconBox(
                                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                                    isChecked = soundConfig.disableSafeVolumeWarning
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = soundConfig.disableSafeVolumeWarning,
                                    onCheckedChange = { disable ->
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        manager.updateSoundConfig { it.copy(disableSafeVolumeWarning = disable) }
                                    }
                                )
                            }
                        )
                    }
                }
            }

            // SECTION 2: PER-APP VOLUME USING SHIZUKU
            item {
                SectionHeader(
                    title = "Per-App Volume (Shizuku)",
                    actionLabel = "Add Apps",
                    actionIcon = Icons.Default.Add,
                    onActionClick = { showAddAppPicker = true }
                )
            }

            item {
                SettingsCardItem(position = GroupPosition.SINGLE, isActive = soundConfig.perAppVolumeEnabled) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("Enable Per-App Volume", fontWeight = FontWeight.SemiBold) },
                        leadingContent = {
                            CookieIconBox(
                                icon = Icons.Default.Tune,
                                isChecked = soundConfig.perAppVolumeEnabled
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = soundConfig.perAppVolumeEnabled,
                                onCheckedChange = { enabled ->
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    manager.updateSoundConfig { it.copy(perAppVolumeEnabled = enabled) }
                                }
                            )
                        }
                    )
                }
            }

            item {
                if (perAppRules.isEmpty()) {
                    SettingsCardItem(position = GroupPosition.SINGLE) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("No per-app volume rules yet", fontWeight = FontWeight.SemiBold) },
                            leadingContent = {
                                CookieIconBox(icon = Icons.Default.Apps)
                            },
                            trailingContent = {
                                FilledTonalButton(
                                    onClick = { showAddAppPicker = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Add Apps")
                                }
                            }
                        )
                    }
                } else {
                    val pm = context.packageManager
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        perAppRules.forEachIndexed { idx, rule ->
                            val pos = when {
                                perAppRules.size == 1 -> GroupPosition.SINGLE
                                idx == 0 -> GroupPosition.TOP
                                idx == perAppRules.size - 1 -> GroupPosition.BOTTOM
                                else -> GroupPosition.MIDDLE
                            }
                            val appIcon = remember(rule.packageName) {
                                runCatching {
                                    MostUsedAppsHelper.drawableToBitmap(pm.getApplicationIcon(rule.packageName))
                                }.getOrNull()
                            }

                            SettingsCardItem(position = pos, isActive = rule.enabled) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        if (appIcon != null) {
                                            Image(
                                                bitmap = appIcon.asImageBitmap(),
                                                contentDescription = rule.appLabel,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        } else {
                                            CookieIconBox(icon = Icons.Default.Apps)
                                        }

                                        Text(
                                            text = rule.appLabel,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (rule.volumePercent == 0) "Muted" else "${rule.volumePercent}%",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                val newPct = if (rule.volumePercent == 0) 70 else 0
                                                manager.savePerAppVolumeRule(rule.copy(volumePercent = newPct))
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (rule.volumePercent == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = "Toggle Mute",
                                                tint = if (rule.volumePercent == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        IconButton(
                                            onClick = { manager.deletePerAppVolumeRule(rule.packageName) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Rule",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Slider(
                                        value = rule.volumePercent.toFloat(),
                                        onValueChange = { newVal ->
                                            manager.savePerAppVolumeRule(rule.copy(volumePercent = newVal.roundToInt()))
                                        },
                                        valueRange = 0f..100f,
                                        modifier = Modifier.height(28.dp)
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

    if (showAddAppPicker) {
        val selectedPkgs = remember(perAppRules) {
            perAppRules.map { it.packageName }.toSet()
        }
        MultiAppSelectorDialog(
            title = "Select Apps for Per-App Volume",
            selectedPackages = selectedPkgs,
            onToggleApp = { app ->
                if (selectedPkgs.contains(app.packageName)) {
                    manager.deletePerAppVolumeRule(app.packageName)
                } else {
                    manager.savePerAppVolumeRule(
                        PerAppVolumeRule(
                            packageName = app.packageName,
                            appLabel = app.label,
                            volumePercent = 75,
                            enabled = true
                        )
                    )
                    if (!soundConfig.perAppVolumeEnabled) {
                        manager.updateSoundConfig { it.copy(perAppVolumeEnabled = true) }
                    }
                }
            },
            onDismiss = { showAddAppPicker = false }
        )
    }
}

// =============================================================================
// 3. DEVICE INFO TAB CONTENT
// =============================================================================

private fun formatBytesToGb(bytes: Long): String {
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f GB", gb)
}

private fun formatUptime(ms: Long): String {
    val totalMinutes = ms / 60_000
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60
    return buildString {
        if (days > 0) append("${days}d ")
        if (hours > 0 || days > 0) append("${hours}h ")
        append("${minutes}m")
    }
}

@Composable
fun DeviceInfoTabContent(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)
) {
    val context = LocalContext.current
    val view = LocalView.current

    val manufacturer = remember {
        Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
    val brand = remember {
        Build.BRAND.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
    val model = remember { Build.MODEL }
    val deviceName = remember {
        val globalName = runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()
        val secureBtName = runCatching {
            Settings.Secure.getString(context.contentResolver, "bluetooth_name")
        }.getOrNull()
        val raw = when {
            !globalName.isNullOrBlank() -> globalName
            !secureBtName.isNullOrBlank() -> secureBtName
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
        raw.trim()
    }
    val deviceCodename = remember { Build.DEVICE }
    val product = remember { Build.PRODUCT }
    val hardware = remember {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.SOC_MODEL != Build.UNKNOWN) {
            Build.SOC_MODEL
        } else null
        listOfNotNull(soc, Build.HARDWARE, Build.BOARD).distinct().joinToString(" • ")
    }
    val androidVersion = remember { "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" }
    val securityPatch = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH.ifBlank { "Unknown" } else "Unknown"
    }
    val buildDisplay = remember { Build.DISPLAY }
    val abiList = remember { Build.SUPPORTED_ABIS.joinToString(", ") }
    val kernelVersion = remember { System.getProperty("os.version") ?: "Unknown" }

    // Memory (RAM)
    val memInfo = remember {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)
        info
    }
    val totalRam = memInfo.totalMem.coerceAtLeast(1L)
    val availRam = memInfo.availMem.coerceAtLeast(0L)
    val usedRam = (totalRam - availRam).coerceAtLeast(0L)
    val ramFraction = (usedRam.toFloat() / totalRam.toFloat()).coerceIn(0f, 1f)

    // Internal Storage
    val storageStats = remember {
        runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.totalBytes.coerceAtLeast(1L)
            val free = stat.availableBytes.coerceAtLeast(0L)
            val used = (total - free).coerceAtLeast(0L)
            Triple(used, total, free)
        }.getOrElse { Triple(0L, 1L, 0L) }
    }
    val usedStorage = storageStats.first
    val totalStorage = storageStats.second
    val freeStorage = storageStats.third
    val storageFraction = (usedStorage.toFloat() / totalStorage.toFloat()).coerceIn(0f, 1f)

    // Display Info
    val displaySummary = remember {
        val dm = context.resources.displayMetrics
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        @Suppress("DEPRECATION")
        val refreshRate = wm?.defaultDisplay?.refreshRate?.roundToInt() ?: 60
        "${dm.widthPixels} × ${dm.heightPixels} px • ${dm.densityDpi} dpi • ${refreshRate} Hz"
    }

    // Battery Info
    val batterySummary = remember {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).roundToInt() else 0
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempTenths / 10f
        val stateLabel = if (isCharging) "Charging" else "Discharging"
        "$pct% ($stateLabel) • ${String.format(Locale.US, "%.1f°C", tempC)}"
    }

    val uptimeText = remember { formatUptime(SystemClock.elapsedRealtime()) }

    fun copyInfo(label: String, value: String) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        runCatching {
            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cb?.setPrimaryClip(ClipData.newPlainText(label, value))
            Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
        }
    }

    val deviceInfoScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 6
        }
    }

    ProgressiveTopBlurBox(
        isScrolled = deviceInfoScrolled,
        modifier = modifier.fillMaxSize()
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // BIG SCROLLING DEVICE NAME HEADER AT TOP
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 18.dp, bottom = 6.dp, start = 4.dp, end = 4.dp)
            ) {
                Text(
                    text = deviceName,
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(iterations = Int.MAX_VALUE)
                )
            }
        }

        // SECTION 1: STORAGE & MEMORY
        item {
            SectionHeader(title = "Storage & Memory")
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SettingsCardItem(position = GroupPosition.TOP) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                copyInfo("Memory (RAM)", "${formatBytesToGb(usedRam)} / ${formatBytesToGb(totalRam)}")
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CookieIconBox(icon = Icons.Default.Memory)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Memory (RAM)", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${formatBytesToGb(usedRam)} used of ${formatBytesToGb(totalRam)} (${formatBytesToGb(availRam)} free)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${(ramFraction * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { ramFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }

                SettingsCardItem(position = GroupPosition.BOTTOM) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                copyInfo("Internal Storage", "${formatBytesToGb(usedStorage)} / ${formatBytesToGb(totalStorage)}")
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CookieIconBox(icon = Icons.Default.Storage)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Internal Storage", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${formatBytesToGb(usedStorage)} used of ${formatBytesToGb(totalStorage)} (${formatBytesToGb(freeStorage)} free)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${(storageFraction * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { storageFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }

        // SECTION 2: DEVICE IDENTITY
        item {
            SectionHeader(title = "Device")
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                DeviceInfoRowCard(
                    position = GroupPosition.TOP,
                    icon = Icons.Default.Business,
                    label = "Manufacturer",
                    value = "$manufacturer ($brand)",
                    onClick = { copyInfo("Manufacturer", "$manufacturer ($brand)") }
                )
                DeviceInfoRowCard(
                    position = GroupPosition.MIDDLE,
                    icon = Icons.Default.Smartphone,
                    label = "Model",
                    value = "$model ($deviceCodename / $product)",
                    onClick = { copyInfo("Model", "$model ($deviceCodename)") }
                )
                DeviceInfoRowCard(
                    position = GroupPosition.MIDDLE,
                    icon = Icons.Default.DeveloperBoard,
                    label = "Hardware & Board",
                    value = hardware,
                    onClick = { copyInfo("Hardware", hardware) }
                )
                DeviceInfoRowCard(
                    position = GroupPosition.MIDDLE,
                    icon = Icons.Default.FitScreen,
                    label = "Display",
                    value = displaySummary,
                    onClick = { copyInfo("Display", displaySummary) }
                )
                DeviceInfoRowCard(
                    position = GroupPosition.BOTTOM,
                    icon = Icons.Default.BatteryStd,
                    label = "Battery",
                    value = batterySummary,
                    onClick = { copyInfo("Battery", batterySummary) }
                )
            }
        }

        // SECTION 3: ANDROID & SYSTEM
        item {
            SectionHeader(title = "Android & System")
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                DeviceInfoRowCard(
                    position = GroupPosition.TOP,
                    icon = Icons.Default.Android,
                    label = "Android Version",
                    value = androidVersion,
                    onClick = { copyInfo("Android Version", androidVersion) }
                )
                DeviceInfoRowCard(
                    position = GroupPosition.MIDDLE,
                    icon = Icons.Default.Security,
                    label = "Security Patch",
                    value = securityPatch,
                    onClick = { copyInfo("Security Patch", securityPatch) }
                )
                DeviceInfoRowCard(
                    position = GroupPosition.MIDDLE,
                    icon = Icons.Default.Build,
                    label = "Build Number",
                    value = buildDisplay,
                    onClick = { copyInfo("Build Number", buildDisplay) }
                )
                DeviceInfoRowCard(
                    position = GroupPosition.MIDDLE,
                    icon = Icons.Default.Code,
                    label = "CPU Architecture & Kernel",
                    value = "$abiList • Linux $kernelVersion",
                    onClick = { copyInfo("Architecture & Kernel", "$abiList • Linux $kernelVersion") }
                )
                DeviceInfoRowCard(
                    position = GroupPosition.BOTTOM,
                    icon = Icons.Default.Schedule,
                    label = "System Uptime",
                    value = uptimeText,
                    onClick = { copyInfo("System Uptime", uptimeText) }
                )
            }
        }
    }
    }
}

@Composable
private fun DeviceInfoRowCard(
    position: GroupPosition,
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    SettingsCardItem(position = position) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable(onClick = onClick),
            headlineContent = {
                Text(label, fontWeight = FontWeight.SemiBold)
            },
            supportingContent = {
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                CookieIconBox(icon = icon)
            }
        )
    }
}
