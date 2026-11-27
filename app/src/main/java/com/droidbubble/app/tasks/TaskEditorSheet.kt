package com.droidbubble.app.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidbubble.app.island.M3CloverShape
import com.droidbubble.app.ui.main.AppPickerDialog
import com.droidbubble.app.ui.main.CookieIconBox
import com.droidbubble.app.ui.main.GroupPosition
import com.droidbubble.app.ui.main.ProgressiveTopBlurBox
import com.droidbubble.app.ui.main.SettingsCardItem
import com.droidbubble.app.ui.main.getGroupedShape
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

private data class ActionCatalogEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val icon: ImageVector,
    val onSelect: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    initialShortcut: TaskShortcut?,
    onSave: (TaskShortcut) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initialShortcut?.name ?: "") }
    var selectedIconName by remember { mutableStateOf(initialShortcut?.iconName ?: "Bolt") }
    var showInBubble by remember { mutableStateOf(initialShortcut?.showInBubble ?: true) }
    var showInDroidbar by remember { mutableStateOf(initialShortcut?.showInDroidbar ?: false) }
    var automationEnabled by remember { mutableStateOf(initialShortcut?.automationEnabled ?: true) }
    var triggers by remember { mutableStateOf(initialShortcut?.triggers ?: emptyList()) }
    var actions by remember { mutableStateOf(initialShortcut?.actions ?: emptyList()) }

    var showIconPicker by remember { mutableStateOf(false) }
    var showAddTriggerSheet by remember { mutableStateOf(false) }
    var showAddActionDialog by remember { mutableStateOf(false) }
    var addActionSearchQuery by remember { mutableStateOf("") }
    var showSubActionDialog by remember { mutableStateOf<String?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }

    BackHandler {
        when {
            showIconPicker -> showIconPicker = false
            showAddTriggerSheet -> showAddTriggerSheet = false
            showSubActionDialog != null -> showSubActionDialog = null
            showAddActionDialog -> showAddActionDialog = false
            showAppPicker -> showAppPicker = false
            else -> onBack()
        }
    }

    // Calculate Repeat nesting depth for each block so inner blocks indent visually
    val nestingDepths = remember(actions) {
        val depths = IntArray(actions.size)
        var currentDepth = 0
        for (i in actions.indices) {
            val act = actions[i]
            if (act is TaskAction.RepeatEnd) {
                currentDepth = (currentDepth - 1).coerceAtLeast(0)
            }
            depths[i] = currentDepth
            if (act is TaskAction.RepeatStart) {
                currentDepth++
            }
        }
        depths.toList()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Text(
                        text = if (initialShortcut == null) "New Shortcut" else "Edit Shortcut",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    val canSave = name.isNotBlank() && actions.isNotEmpty()
                    val saveBtnContainer by animateColorAsState(
                        targetValue = if (canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        animationSpec = tween(180),
                        label = "saveBtnContainer"
                    )
                    Button(
                        onClick = {
                            if (canSave) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                onSave(
                                    TaskShortcut(
                                        id = initialShortcut?.id ?: java.util.UUID.randomUUID().toString(),
                                        name = name.trim(),
                                        iconName = selectedIconName,
                                        showInBubble = showInBubble,
                                        showInDroidbar = showInDroidbar,
                                        automationEnabled = automationEnabled,
                                        triggers = triggers,
                                        actions = actions
                                    )
                                )
                            }
                        },
                        enabled = canSave,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = saveBtnContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .height(36.dp)
                    ) {
                        Text("Save", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    }
                }
            )
        }
    ) { innerPadding ->
        val editorListState = rememberLazyListState()
        val editorIsScrolled by remember {
            derivedStateOf {
                editorListState.firstVisibleItemIndex > 0 || editorListState.firstVisibleItemScrollOffset > 6
            }
        }
        ProgressiveTopBlurBox(
            isScrolled = editorIsScrolled,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = editorListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Icon & Name Row
                item {
                    val iconPop = remember { Animatable(1f) }
                    LaunchedEffect(selectedIconName) {
                        iconPop.snapTo(0.84f)
                        iconPop.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(dampingRatio = 0.52f, stiffness = 480f)
                        )
                    }
                    SettingsCardItem(position = GroupPosition.SINGLE) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(56.dp)
                                    .graphicsLayer {
                                        scaleX = iconPop.value
                                        scaleY = iconPop.value
                                    }
                                    .clip(M3CloverShape)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        showIconPicker = true
                                    },
                                shape = M3CloverShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = TaskIcons.getIcon(selectedIconName),
                                        contentDescription = "Pick icon",
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Shortcut Name") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 2. Droidbubble & Droidbar toggles
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        SettingsCardItem(position = GroupPosition.TOP, isActive = showInBubble) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Show in Droidbubble", fontWeight = FontWeight.SemiBold) },
                                leadingContent = {
                                    CookieIconBox(
                                        icon = Icons.Default.Widgets,
                                        isChecked = showInBubble
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = showInBubble,
                                        onCheckedChange = { showInBubble = it }
                                    )
                                }
                            )
                        }

                        SettingsCardItem(position = GroupPosition.BOTTOM, isActive = showInDroidbar) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Add to Droidbar", fontWeight = FontWeight.SemiBold) },
                                leadingContent = {
                                    CookieIconBox(
                                        icon = Icons.Default.VerticalSplit,
                                        isChecked = showInDroidbar
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = showInDroidbar,
                                        onCheckedChange = { showInDroidbar = it }
                                    )
                                }
                            )
                        }
                    }
                }

                // 3. Automations & Triggers Section Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Automations",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${triggers.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (triggers.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (automationEnabled) "Active" else "Paused",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (automationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Switch(
                                    checked = automationEnabled,
                                    onCheckedChange = { automationEnabled = it }
                                )
                            }
                        }
                    }
                }

                // 4. Triggers List + "Add Automation Trigger" Item
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f)),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        triggers.forEachIndexed { idx, trigger ->
                            val pos = if (idx == 0) GroupPosition.TOP else GroupPosition.MIDDLE
                            SettingsCardItem(position = pos) {
                                ListItem(
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    headlineContent = {
                                        Text(
                                            text = trigger.getDisplayName(),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                    },
                                    supportingContent = {
                                        val sub = when (trigger) {
                                            is TaskTrigger.TimeSchedule -> "Recurring Time & Selected Days"
                                            is TaskTrigger.SpecificDate -> "One-Time Date & Time"
                                            is TaskTrigger.DeviceEvent -> trigger.eventType.description
                                        }
                                        Text(
                                            text = sub,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    leadingContent = {
                                        CookieIconBox(icon = trigger.getIcon(), isChecked = true)
                                    },
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                triggers = triggers.filterIndexed { i, _ -> i != idx }
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove Trigger",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        val addTriggerPos = if (triggers.isEmpty()) GroupPosition.SINGLE else GroupPosition.BOTTOM
                        SettingsCardItem(
                            position = addTriggerPos,
                            isActive = true,
                            modifier = Modifier.clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showAddTriggerSheet = true
                            }
                        ) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = {
                                    Text(
                                        text = "Add Automation",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.5.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                leadingContent = {
                                    CookieIconBox(icon = Icons.Default.AutoAwesome, isChecked = true)
                                },
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                // 5. Actions Section Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Actions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${actions.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // 6. Chained Action Blocks List + Integrated "Add Action" List Item
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f)),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        actions.forEachIndexed { index, action ->
                            val depth = nestingDepths.getOrElse(index) { 0 }
                            val isRepeatBoundary = action is TaskAction.RepeatStart || action is TaskAction.RepeatEnd
                            val position = when {
                                index == 0 -> GroupPosition.TOP
                                else -> GroupPosition.MIDDLE
                            }
                            val cardShape = getGroupedShape(position)
                            val cardBg = if (isRepeatBoundary) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = (depth * 14).coerceAtMost(42).dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (depth > 0) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }

                                Surface(
                                    shape = cardShape,
                                    color = cardBg,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        ListItem(
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                            headlineContent = {
                                                Text(
                                                    text = action.getDisplayName(),
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 15.sp
                                                )
                                            },
                                            leadingContent = {
                                                CookieIconBox(icon = action.getIcon(), isChecked = isRepeatBoundary)
                                            },
                                            trailingContent = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(
                                                        onClick = {
                                                            if (index > 0) {
                                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                                val m = actions.toMutableList()
                                                                val tmp = m[index - 1]
                                                                m[index - 1] = m[index]
                                                                m[index] = tmp
                                                                actions = m
                                                            }
                                                        },
                                                        enabled = index > 0,
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.KeyboardArrowUp,
                                                            contentDescription = "Move Up",
                                                            tint = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            if (index < actions.lastIndex) {
                                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                                val m = actions.toMutableList()
                                                                val tmp = m[index + 1]
                                                                m[index + 1] = m[index]
                                                                m[index] = tmp
                                                                actions = m
                                                            }
                                                        },
                                                        enabled = index < actions.lastIndex,
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.KeyboardArrowDown,
                                                            contentDescription = "Move Down",
                                                            tint = if (index < actions.lastIndex) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                            actions = actions.filterIndexed { i, _ -> i != index }
                                                        },
                                                        modifier = Modifier.size(30.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Remove",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        )

                                        // Inline customization for SetVariable
                                        if (action is TaskAction.SetVariable) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                ScopeSelectorRow(
                                                    selectedScope = action.scope,
                                                    onSelectScope = { newScope ->
                                                        actions = actions.toMutableList().also { list ->
                                                            list[index] = action.copy(scope = newScope)
                                                        }
                                                    }
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedTextField(
                                                        value = action.name,
                                                        onValueChange = { v ->
                                                            actions = actions.toMutableList().also { list ->
                                                                list[index] = action.copy(name = v)
                                                            }
                                                        },
                                                        label = { Text("Variable Name", fontSize = 11.sp) },
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    OutlinedTextField(
                                                        value = action.value,
                                                        onValueChange = { v ->
                                                            actions = actions.toMutableList().also { list ->
                                                                list[index] = action.copy(value = v)
                                                            }
                                                        },
                                                        label = { Text("Initial / New Value", fontSize = 11.sp) },
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }

                                        // Inline customization for AddToVariable
                                        if (action is TaskAction.AddToVariable) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                ScopeSelectorRow(
                                                    selectedScope = action.scope,
                                                    onSelectScope = { newScope ->
                                                        actions = actions.toMutableList().also { list ->
                                                            list[index] = action.copy(scope = newScope)
                                                        }
                                                    }
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedTextField(
                                                        value = action.name,
                                                        onValueChange = { v ->
                                                            actions = actions.toMutableList().also { list ->
                                                                list[index] = action.copy(name = v)
                                                            }
                                                        },
                                                        label = { Text("Variable Name", fontSize = 11.sp) },
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    OutlinedTextField(
                                                        value = action.amount,
                                                        onValueChange = { v ->
                                                            actions = actions.toMutableList().also { list ->
                                                                list[index] = action.copy(amount = v)
                                                            }
                                                        },
                                                        label = { Text("Add Amount (e.g. 1, -1, {step})", fontSize = 11.sp) },
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }

                                        // Inline customization for MathOperation
                                        if (action is TaskAction.MathOperation) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    OutlinedTextField(
                                                        value = action.targetVar,
                                                        onValueChange = { v ->
                                                            actions = actions.toMutableList().also { list ->
                                                                list[index] = action.copy(targetVar = v)
                                                            }
                                                        },
                                                        label = { Text("Save To Variable", fontSize = 11.sp) },
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    FilterChip(
                                                        selected = action.targetScope == VariableScope.GLOBAL,
                                                        onClick = {
                                                            val nextScope = if (action.targetScope == VariableScope.GLOBAL) VariableScope.LOCAL else VariableScope.GLOBAL
                                                            actions = actions.toMutableList().also { list ->
                                                                list[index] = action.copy(targetScope = nextScope)
                                                            }
                                                        },
                                                        label = { Text(if (action.targetScope == VariableScope.GLOBAL) "Global" else "Local") }
                                                    )
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    MathOperator.entries.forEach { op ->
                                                        val isSelected = action.operator == op
                                                        Surface(
                                                            shape = RoundedCornerShape(10.dp),
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .height(36.dp)
                                                                .clip(RoundedCornerShape(10.dp))
                                                                .clickable {
                                                                    actions = actions.toMutableList().also { list ->
                                                                        list[index] = action.copy(operator = op)
                                                                    }
                                                                }
                                                        ) {
                                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                                Text(op.symbol, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                                            }
                                                        }
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedTextField(
                                                        value = action.leftOperand,
                                                        onValueChange = { v ->
                                                            actions = actions.toMutableList().also { list ->
                                                                list[index] = action.copy(leftOperand = v)
                                                            }
                                                        },
                                                        label = { Text("First Value / {var}", fontSize = 11.sp) },
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    OutlinedTextField(
                                                        value = action.rightOperand,
                                                        onValueChange = { v ->
                                                            actions = actions.toMutableList().also { list ->
                                                                list[index] = action.copy(rightOperand = v)
                                                            }
                                                        },
                                                        label = { Text("Second Value / {var}", fontSize = 11.sp) },
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }

                                        // Inline customization for RepeatStart
                                        if (action is TaskAction.RepeatStart) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = action.countExpression,
                                                    onValueChange = { v ->
                                                        actions = actions.toMutableList().also { list ->
                                                            list[index] = action.copy(countExpression = v)
                                                        }
                                                    },
                                                    label = { Text("Repeat Count (Number or {variable})", fontSize = 11.sp) },
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Text(
                                                    text = "Place actions between Repeat Start and Repeat End. Use {iteration} or {index} inside.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        // Inline customization for LiveProgressNotification
                                        if (action is TaskAction.LiveProgressNotification) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedTextField(
                                                        value = action.title,
                                                        onValueChange = { v ->
                                                            actions = actions.toMutableList().also { list ->
                                                                list[index] = action.copy(title = v)
                                                            }
                                                        },
                                                        label = { Text("Title (supports {var})", fontSize = 11.sp) },
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    OutlinedTextField(
                                                        value = action.text,
                                                        onValueChange = { v ->
                                                            actions = actions.toMutableList().also { list ->
                                                                list[index] = action.copy(text = v)
                                                            }
                                                        },
                                                        label = { Text("Subtitle (e.g. {count}%)", fontSize = 11.sp) },
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedTextField(
                                                        value = action.currentValue,
                                                        onValueChange = { v ->
                                                            actions = actions.toMutableList().also { list ->
                                                                list[index] = action.copy(currentValue = v)
                                                            }
                                                        },
                                                        label = { Text("Progress Value / {var}", fontSize = 11.sp) },
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    OutlinedTextField(
                                                        value = action.maxValue,
                                                        onValueChange = { v ->
                                                            actions = actions.toMutableList().also { list ->
                                                                list[index] = action.copy(maxValue = v)
                                                            }
                                                        },
                                                        label = { Text("Max Value / {var}", fontSize = 11.sp) },
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    OutlinedTextField(
                                                        value = action.notificationId,
                                                        onValueChange = { v ->
                                                            actions = actions.toMutableList().also { list ->
                                                                list[index] = action.copy(notificationId = v)
                                                            }
                                                        },
                                                        label = { Text("Progress Tag ID (to update same bar)", fontSize = 11.sp) },
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    FilterChip(
                                                        selected = action.dismissWhenComplete,
                                                        onClick = {
                                                            actions = actions.toMutableList().also { list ->
                                                                list[index] = action.copy(dismissWhenComplete = !action.dismissWhenComplete)
                                                            }
                                                        },
                                                        label = { Text("Auto-Dismiss", fontSize = 11.sp) }
                                                    )
                                                }
                                            }
                                        }

                                        // Inline customization for RemoveNotification
                                        if (action is TaskAction.RemoveNotification) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    OutlinedTextField(
                                                        value = action.targetTagOrTitle,
                                                        onValueChange = { v ->
                                                            actions = actions.toMutableList().also { list ->
                                                                list[index] = action.copy(targetTagOrTitle = v)
                                                            }
                                                        },
                                                        enabled = !action.removeAll,
                                                        label = { Text("Progress Tag ID, Title, or App (e.g. progress_1)", fontSize = 11.sp) },
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    FilterChip(
                                                        selected = action.removeAll,
                                                        onClick = {
                                                            actions = actions.toMutableList().also { list ->
                                                                list[index] = action.copy(removeAll = !action.removeAll)
                                                            }
                                                        },
                                                        label = { Text("All", fontSize = 11.sp) }
                                                    )
                                                }
                                                Text(
                                                    text = "Removes matching notifications (including ongoing/live progress notifications so they don't remain when finished).",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        // Inline customization for WaitBlock
                                        if (action is TaskAction.WaitBlock) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedTextField(
                                                    value = action.durationValue,
                                                    onValueChange = { v ->
                                                        actions = actions.toMutableList().also { list ->
                                                            list[index] = action.copy(durationValue = v)
                                                        }
                                                    },
                                                    label = { Text("Duration (Number or {var})", fontSize = 11.sp) },
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                FilterChip(
                                                    selected = !action.isMilliseconds,
                                                    onClick = {
                                                        actions = actions.toMutableList().also { list ->
                                                            list[index] = action.copy(isMilliseconds = false)
                                                        }
                                                    },
                                                    label = { Text("sec") }
                                                )
                                                FilterChip(
                                                    selected = action.isMilliseconds,
                                                    onClick = {
                                                        actions = actions.toMutableList().also { list ->
                                                            list[index] = action.copy(isMilliseconds = true)
                                                        }
                                                    },
                                                    label = { Text("ms") }
                                                )
                                            }
                                        }

                                        // Inline customization for Send Notification
                                        if (action is TaskAction.SendNotification) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = action.title,
                                                    onValueChange = { newTitle ->
                                                        actions = actions.toMutableList().also { list ->
                                                            list[index] = action.copy(title = newTitle)
                                                        }
                                                    },
                                                    label = { Text("Notification Title (supports {var})", fontSize = 12.sp) },
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                OutlinedTextField(
                                                    value = action.text,
                                                    onValueChange = { newText ->
                                                        actions = actions.toMutableList().also { list ->
                                                            list[index] = action.copy(text = newText)
                                                        }
                                                    },
                                                    label = { Text("Notification Text (supports {var})", fontSize = 12.sp) },
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }

                                        // Inline slider for Set Brightness
                                        if (action is TaskAction.SetBrightness) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Slider(
                                                    value = action.level.toFloat(),
                                                    onValueChange = { lvl ->
                                                        actions = actions.toMutableList().also { list ->
                                                            list[index] = TaskAction.SetBrightness(lvl.roundToInt())
                                                        }
                                                    },
                                                    valueRange = 0f..100f,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "${action.level}%",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.width(42.dp)
                                                )
                                            }
                                        }

                                        // Inline slider for Set Volume
                                        if (action is TaskAction.SetVolume) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Slider(
                                                    value = action.level.toFloat(),
                                                    onValueChange = { lvl ->
                                                        actions = actions.toMutableList().also { list ->
                                                            list[index] = TaskAction.SetVolume(lvl.roundToInt())
                                                        }
                                                    },
                                                    valueRange = 0f..100f,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "${action.level}%",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.width(42.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // "Add Action" item as part of the grouped list
                        val addPosition = if (actions.isEmpty()) GroupPosition.SINGLE else GroupPosition.BOTTOM
                        SettingsCardItem(
                            position = addPosition,
                            isActive = true,
                            modifier = Modifier.clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                addActionSearchQuery = ""
                                showAddActionDialog = true
                            }
                        ) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = {
                                    Text(
                                        text = "Add Action",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                leadingContent = {
                                    CookieIconBox(icon = Icons.Default.Add, isChecked = true)
                                },
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for Adding Automation Trigger (Time, Date, or Device Event)
    if (showAddTriggerSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var selectedTab by remember { mutableIntStateOf(0) } // 0 = Time & Days, 1 = Specific Date, 2 = Device Event

        val nowCal = remember { Calendar.getInstance() }
        var schedHour by remember { mutableIntStateOf(8) }
        var schedMinute by remember { mutableIntStateOf(0) }
        var selectedDays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5, 6, 7)) }

        var specYear by remember { mutableIntStateOf(nowCal.get(Calendar.YEAR)) }
        var specMonth by remember { mutableIntStateOf(nowCal.get(Calendar.MONTH) + 1) }
        var specDay by remember { mutableIntStateOf(nowCal.get(Calendar.DAY_OF_MONTH)) }
        var specHour by remember { mutableIntStateOf(9) }
        var specMinute by remember { mutableIntStateOf(0) }

        var selectedEventType by remember { mutableStateOf(DeviceEventType.WIFI_CONNECTED) }
        var batteryThreshold by remember { mutableFloatStateOf(20f) }

        ModalBottomSheet(
            onDismissRequest = { showAddTriggerSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Add Task Automation",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                // 3-Way Tab Selector: Time Schedule / Date / Device Event
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf(
                        Triple(0, "Time & Days", Icons.Default.Schedule),
                        Triple(1, "Date", Icons.Default.Event),
                        Triple(2, "Device Event", Icons.Default.Sensors)
                    )
                    tabs.forEach { (idx, label, icon) ->
                        val isSel = selectedTab == idx
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedTab = idx },
                            label = { Text(label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1) },
                            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                when (selectedTab) {
                    0 -> {
                        // Recurring Time + Selected Days of Week
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Trigger Time", fontWeight = FontWeight.SemiBold)
                                        Text(
                                            String.format(Locale.US, "%02d:%02d", schedHour, schedMinute),
                                            fontSize = 26.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            TimePickerDialog(
                                                context,
                                                { _, h, m ->
                                                    schedHour = h
                                                    schedMinute = m
                                                },
                                                schedHour,
                                                schedMinute,
                                                true
                                            ).show()
                                        },
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pick Time", fontWeight = FontWeight.Bold)
                                    }
                                }

                                HorizontalDivider()

                                Text("Active Days", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)

                                val days = listOf(
                                    1 to "M",
                                    2 to "T",
                                    3 to "W",
                                    4 to "T",
                                    5 to "F",
                                    6 to "S",
                                    7 to "S"
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    days.forEach { (dayNum, shortLabel) ->
                                        val isChosen = selectedDays.contains(dayNum)
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isChosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                            contentColor = if (isChosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    selectedDays = if (isChosen && selectedDays.size > 1) {
                                                        selectedDays - dayNum
                                                    } else {
                                                        selectedDays + dayNum
                                                    }
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(shortLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AssistChip(
                                        onClick = { selectedDays = setOf(1, 2, 3, 4, 5, 6, 7) },
                                        label = { Text("Every Day", fontSize = 11.sp) }
                                    )
                                    AssistChip(
                                        onClick = { selectedDays = setOf(1, 2, 3, 4, 5) },
                                        label = { Text("Weekdays", fontSize = 11.sp) }
                                    )
                                    AssistChip(
                                        onClick = { selectedDays = setOf(6, 7) },
                                        label = { Text("Weekends", fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showAddTriggerSheet = false },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    triggers = triggers + TaskTrigger.TimeSchedule(
                                        hour = schedHour,
                                        minute = schedMinute,
                                        daysOfWeek = selectedDays
                                    )
                                    showAddTriggerSheet = false
                                },
                                modifier = Modifier.weight(1.4f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Add Time Schedule", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    1 -> {
                        // Specific Date & Time
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Date", fontWeight = FontWeight.SemiBold)
                                        Text(
                                            String.format(Locale.US, "%04d-%02d-%02d", specYear, specMonth, specDay),
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            DatePickerDialog(
                                                context,
                                                { _, y, m, d ->
                                                    specYear = y
                                                    specMonth = m + 1
                                                    specDay = d
                                                },
                                                specYear,
                                                specMonth - 1,
                                                specDay
                                            ).show()
                                        },
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pick Date", fontWeight = FontWeight.Bold)
                                    }
                                }

                                HorizontalDivider()

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Time", fontWeight = FontWeight.SemiBold)
                                        Text(
                                            String.format(Locale.US, "%02d:%02d", specHour, specMinute),
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            TimePickerDialog(
                                                context,
                                                { _, h, m ->
                                                    specHour = h
                                                    specMinute = m
                                                },
                                                specHour,
                                                specMinute,
                                                true
                                            ).show()
                                        },
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pick Time", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showAddTriggerSheet = false },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    triggers = triggers + TaskTrigger.SpecificDate(
                                        year = specYear,
                                        month = specMonth,
                                        dayOfMonth = specDay,
                                        hour = specHour,
                                        minute = specMinute
                                    )
                                    showAddTriggerSheet = false
                                },
                                modifier = Modifier.weight(1.4f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Add Date Trigger", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    else -> {
                        // Device Event
                        val allEvents = DeviceEventType.entries
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            itemsIndexed(allEvents) { idx, ev ->
                                val pos = when {
                                    idx == 0 -> GroupPosition.TOP
                                    idx == allEvents.lastIndex -> GroupPosition.BOTTOM
                                    else -> GroupPosition.MIDDLE
                                }
                                val shape = getGroupedShape(pos)
                                val isSel = selectedEventType == ev
                                Surface(
                                    shape = shape,
                                    color = if (isSel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(shape)
                                        .clickable { selectedEventType = ev }
                                ) {
                                    ListItem(
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        headlineContent = {
                                            Text(
                                                ev.displayName,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                        },
                                        supportingContent = {
                                            Text(ev.description, fontSize = 12.sp)
                                        },
                                        trailingContent = {
                                            RadioButton(
                                                selected = isSel,
                                                onClick = { selectedEventType = ev }
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        if (selectedEventType.requiresBatteryThreshold) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Battery Level Threshold", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(
                                            "${batteryThreshold.roundToInt()}%",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Slider(
                                        value = batteryThreshold,
                                        onValueChange = { batteryThreshold = it },
                                        valueRange = 5f..100f
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showAddTriggerSheet = false },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    triggers = triggers + TaskTrigger.DeviceEvent(
                                        eventType = selectedEventType,
                                        batteryThreshold = batteryThreshold.roundToInt().coerceIn(1, 100)
                                    )
                                    showAddTriggerSheet = false
                                },
                                modifier = Modifier.weight(1.4f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Add Event Trigger", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet Icon Picker (converted from AlertDialog)
    if (showIconPicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showIconPicker = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Choose Shortcut Icon",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(TaskIcons.icons) { (iconName, vector) ->
                            val isSelected = iconName == selectedIconName
                            Surface(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        selectedIconName = iconName
                                        showIconPicker = false
                                    }
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                                        } else Modifier
                                    ),
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = vector,
                                        contentDescription = iconName,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { showIconPicker = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }

    // Modal "Add Action" Bottom Sheet with Search Bar & Advanced Actions
    if (showAddActionDialog) {
        val allCatalogActions = remember {
            listOf(
                ActionCatalogEntry(
                    key = "variable",
                    title = "Variables",
                    subtitle = "Create or set a Local or Global variable",
                    category = "Logic & Variables",
                    icon = Icons.Default.DataObject,
                    onSelect = {
                        actions = actions + TaskAction.SetVariable(
                            name = "count",
                            value = "0",
                            scope = VariableScope.LOCAL
                        )
                        showAddActionDialog = false
                    }
                ),
                ActionCatalogEntry(
                    key = "add_to_variable",
                    title = "Add to variable",
                    subtitle = "Increment or add a value to a Local or Global variable",
                    category = "Logic & Variables",
                    icon = Icons.Default.ExposurePlus1,
                    onSelect = {
                        actions = actions + TaskAction.AddToVariable(
                            name = "count",
                            amount = "1",
                            scope = VariableScope.LOCAL
                        )
                        showAddActionDialog = false
                    }
                ),
                ActionCatalogEntry(
                    key = "math_operations",
                    title = "Math operations",
                    subtitle = "Calculate +, -, ×, ÷, % and store in a variable",
                    category = "Logic & Variables",
                    icon = Icons.Default.Calculate,
                    onSelect = {
                        actions = actions + TaskAction.MathOperation(
                            targetVar = "result",
                            targetScope = VariableScope.LOCAL,
                            leftOperand = "{count}",
                            operator = MathOperator.ADD,
                            rightOperand = "1"
                        )
                        showAddActionDialog = false
                    }
                ),
                ActionCatalogEntry(
                    key = "repeat",
                    title = "Repeat functions",
                    subtitle = "Adds Repeat Start & Repeat End blocks to loop actions between them",
                    category = "Logic & Variables",
                    icon = Icons.Default.Repeat,
                    onSelect = {
                        val blockId = java.util.UUID.randomUUID().toString()
                        actions = actions + listOf(
                            TaskAction.RepeatStart(countExpression = "5", blockId = blockId),
                            TaskAction.RepeatEnd(blockId = blockId)
                        )
                        showAddActionDialog = false
                    }
                ),
                ActionCatalogEntry(
                    key = "live_progress",
                    title = "Live progress notification",
                    subtitle = "Show or update a live progress bar notification from a number or variable",
                    category = "Logic & Variables",
                    icon = Icons.Default.LinearScale,
                    onSelect = {
                        actions = actions + TaskAction.LiveProgressNotification(
                            notificationId = "progress_1",
                            title = "Task Progress",
                            text = "{count}%",
                            currentValue = "{count}",
                            maxValue = "100",
                            dismissWhenComplete = false
                        )
                        showAddActionDialog = false
                    }
                ),
                ActionCatalogEntry(
                    key = "remove_notification",
                    title = "Remove notification",
                    subtitle = "Dismiss a specific or live progress notification so it doesn't remain",
                    category = "System & Device",
                    icon = Icons.Default.NotificationsOff,
                    onSelect = {
                        actions = actions + TaskAction.RemoveNotification(
                            targetTagOrTitle = "progress_1",
                            removeAll = false
                        )
                        showAddActionDialog = false
                    }
                ),
                ActionCatalogEntry(
                    key = "wait",
                    title = "Wait block",
                    subtitle = "Pause execution for a number of seconds or milliseconds",
                    category = "Logic & Variables",
                    icon = Icons.Default.HourglassBottom,
                    onSelect = {
                        actions = actions + TaskAction.WaitBlock(
                            durationValue = "1",
                            isMilliseconds = false
                        )
                        showAddActionDialog = false
                    }
                ),
                ActionCatalogEntry(
                    key = "open_app",
                    title = "Open an app",
                    subtitle = "Launch any installed application",
                    category = "System & Device",
                    icon = Icons.Default.Apps,
                    onSelect = {
                        showAddActionDialog = false
                        showAppPicker = true
                    }
                ),
                ActionCatalogEntry(
                    key = "wifi",
                    title = "Wi-Fi",
                    subtitle = "Toggle, turn on, or turn off Wi-Fi",
                    category = "System & Device",
                    icon = Icons.Default.Wifi,
                    onSelect = {
                        showAddActionDialog = false
                        showSubActionDialog = "wifi"
                    }
                ),
                ActionCatalogEntry(
                    key = "mobile_data",
                    title = "Mobile data",
                    subtitle = "Toggle, turn on, or turn off mobile data",
                    category = "System & Device",
                    icon = Icons.Default.SignalCellularAlt,
                    onSelect = {
                        showAddActionDialog = false
                        showSubActionDialog = "mobile_data"
                    }
                ),
                ActionCatalogEntry(
                    key = "flashlight",
                    title = "Flashlight",
                    subtitle = "Toggle, turn on, or turn off torch",
                    category = "System & Device",
                    icon = Icons.Default.FlashlightOn,
                    onSelect = {
                        showAddActionDialog = false
                        showSubActionDialog = "flashlight"
                    }
                ),
                ActionCatalogEntry(
                    key = "low_power",
                    title = "Low power mode",
                    subtitle = "Battery saver mode settings",
                    category = "System & Device",
                    icon = Icons.Default.BatteryChargingFull,
                    onSelect = {
                        showAddActionDialog = false
                        showSubActionDialog = "low_power"
                    }
                ),
                ActionCatalogEntry(
                    key = "airplane_mode",
                    title = "Aeroplane mode",
                    subtitle = "Toggle or configure aeroplane mode",
                    category = "System & Device",
                    icon = Icons.Default.AirplanemodeActive,
                    onSelect = {
                        showAddActionDialog = false
                        showSubActionDialog = "airplane_mode"
                    }
                ),
                ActionCatalogEntry(
                    key = "bluetooth",
                    title = "Bluetooth",
                    subtitle = "Toggle, turn on, or turn off Bluetooth",
                    category = "System & Device",
                    icon = Icons.Default.Bluetooth,
                    onSelect = {
                        showAddActionDialog = false
                        showSubActionDialog = "bluetooth"
                    }
                ),
                ActionCatalogEntry(
                    key = "ringer_mode",
                    title = "Ringer mode",
                    subtitle = "Set Ring, Vibrate, or Toggle",
                    category = "System & Device",
                    icon = Icons.Default.NotificationsActive,
                    onSelect = {
                        showAddActionDialog = false
                        showSubActionDialog = "ringer_mode"
                    }
                ),
                ActionCatalogEntry(
                    key = "set_brightness",
                    title = "Set brightness",
                    subtitle = "Adjust screen brightness percentage",
                    category = "System & Device",
                    icon = Icons.Default.BrightnessMedium,
                    onSelect = {
                        actions = actions + TaskAction.SetBrightness(50)
                        showAddActionDialog = false
                    }
                ),
                ActionCatalogEntry(
                    key = "set_volume",
                    title = "Set volume",
                    subtitle = "Adjust media volume percentage",
                    category = "System & Device",
                    icon = Icons.Default.VolumeUp,
                    onSelect = {
                        actions = actions + TaskAction.SetVolume(50)
                        showAddActionDialog = false
                    }
                ),
                ActionCatalogEntry(
                    key = "take_screenshot",
                    title = "Take screenshot",
                    subtitle = "Capture the current screen",
                    category = "System & Device",
                    icon = Icons.Default.Screenshot,
                    onSelect = {
                        actions = actions + TaskAction.TakeScreenshot
                        showAddActionDialog = false
                    }
                ),
                ActionCatalogEntry(
                    key = "go_home",
                    title = "Go to home screen",
                    subtitle = "Navigate to launcher home",
                    category = "System & Device",
                    icon = Icons.Default.Home,
                    onSelect = {
                        actions = actions + TaskAction.GoHome
                        showAddActionDialog = false
                    }
                ),
                ActionCatalogEntry(
                    key = "media_controls",
                    title = "Media controls",
                    subtitle = "Play, pause, toggle playback, or skip track",
                    category = "System & Device",
                    icon = Icons.Default.PlayCircleOutline,
                    onSelect = {
                        showAddActionDialog = false
                        showSubActionDialog = "media_controls"
                    }
                ),
                ActionCatalogEntry(
                    key = "send_notification",
                    title = "Send notification",
                    subtitle = "Post a custom alert notification",
                    category = "System & Device",
                    icon = Icons.Default.NotificationAdd,
                    onSelect = {
                        actions = actions + TaskAction.SendNotification(
                            title = "Notification",
                            text = "Shortcut executed"
                        )
                        showAddActionDialog = false
                    }
                )
            )
        }

        val filteredCatalog = remember(addActionSearchQuery, allCatalogActions) {
            val q = addActionSearchQuery.trim()
            if (q.isEmpty()) {
                allCatalogActions
            } else {
                allCatalogActions.filter {
                    it.title.contains(q, ignoreCase = true) ||
                        it.subtitle.contains(q, ignoreCase = true) ||
                        it.category.contains(q, ignoreCase = true)
                }
            }
        }

        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val sheetNestedScroll = remember {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset = available.copy(x = 0f)

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity
                ): Velocity = available.copy(x = 0f)
            }
        }
        ModalBottomSheet(
            onDismissRequest = { showAddActionDialog = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextField(
                    value = addActionSearchQuery,
                    onValueChange = { addActionSearchQuery = it },
                    placeholder = { Text("Search actions...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (addActionSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { addActionSearchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                val actionListState = rememberLazyListState()
                val actionListScrolled by remember {
                    derivedStateOf {
                        actionListState.firstVisibleItemIndex > 0 || actionListState.firstVisibleItemScrollOffset > 6
                    }
                }

                ProgressiveTopBlurBox(
                    isScrolled = actionListScrolled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyColumn(
                        state = actionListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(sheetNestedScroll),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (filteredCatalog.isEmpty()) {
                            item(key = "empty") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No matching actions found",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = filteredCatalog,
                                key = { _, entry -> entry.key }
                            ) { idx, entry ->
                                val pos = when {
                                    filteredCatalog.size == 1 -> GroupPosition.SINGLE
                                    idx == 0 -> GroupPosition.TOP
                                    idx == filteredCatalog.lastIndex -> GroupPosition.BOTTOM
                                    else -> GroupPosition.MIDDLE
                                }
                                ActionBlockRow(
                                    title = entry.title,
                                    icon = entry.icon,
                                    position = pos,
                                    onClick = entry.onSelect
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Sub-action ModalBottomSheet for Toggle, Turn on, Turn off OR Ringer mode OR Media controls
    if (showSubActionDialog != null) {
        val blockKey = showSubActionDialog!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSubActionDialog = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (blockKey) {
                    "ringer_mode" -> {
                        Text("Ringer Mode Action", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        ActionOptionButton("Set Ring Mode") {
                            actions = actions + TaskAction.RingerMode(RingerActionType.SET_RING)
                            showSubActionDialog = null
                        }
                        ActionOptionButton("Set Vibrate Mode") {
                            actions = actions + TaskAction.RingerMode(RingerActionType.SET_VIBRATE)
                            showSubActionDialog = null
                        }
                        ActionOptionButton("Toggle Ring / Vibrate") {
                            actions = actions + TaskAction.RingerMode(RingerActionType.TOGGLE)
                            showSubActionDialog = null
                        }
                    }
                    "media_controls" -> {
                        Text("Media Action", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        ActionOptionButton("Play") {
                            actions = actions + TaskAction.MediaControls(MediaActionType.PLAY)
                            showSubActionDialog = null
                        }
                        ActionOptionButton("Pause") {
                            actions = actions + TaskAction.MediaControls(MediaActionType.PAUSE)
                            showSubActionDialog = null
                        }
                        ActionOptionButton("Toggle Playback") {
                            actions = actions + TaskAction.MediaControls(MediaActionType.TOGGLE_PLAYBACK)
                            showSubActionDialog = null
                        }
                        ActionOptionButton("Skip Next") {
                            actions = actions + TaskAction.MediaControls(MediaActionType.SKIP)
                            showSubActionDialog = null
                        }
                    }
                    else -> {
                        val title = when (blockKey) {
                            "wifi" -> "Wi-Fi Action"
                            "mobile_data" -> "Mobile Data Action"
                            "flashlight" -> "Flashlight Action"
                            "low_power" -> "Low Power Mode Action"
                            "airplane_mode" -> "Aeroplane Mode Action"
                            "bluetooth" -> "Bluetooth Action"
                            else -> "Action"
                        }
                        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        ActionOptionButton("Toggle") {
                            val action = createToggleAction(blockKey, ToggleActionType.TOGGLE)
                            if (action != null) actions = actions + action
                            showSubActionDialog = null
                        }
                        ActionOptionButton("Turn On") {
                            val action = createToggleAction(blockKey, ToggleActionType.TURN_ON)
                            if (action != null) actions = actions + action
                            showSubActionDialog = null
                        }
                        ActionOptionButton("Turn Off") {
                            val action = createToggleAction(blockKey, ToggleActionType.TURN_OFF)
                            if (action != null) actions = actions + action
                            showSubActionDialog = null
                        }
                    }
                }
                OutlinedButton(
                    onClick = { showSubActionDialog = null },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
    }

    // App Picker Sheet for Open App action
    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onSelectApp = { barApp ->
                actions = actions + TaskAction.OpenApp(
                    packageName = barApp.packageName,
                    appLabel = barApp.label
                )
                showAppPicker = false
            }
        )
    }
}

@Composable
private fun ScopeSelectorRow(
    selectedScope: VariableScope,
    onSelectScope: (VariableScope) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedScope == VariableScope.LOCAL,
            onClick = { onSelectScope(VariableScope.LOCAL) },
            label = { Text("Local (This shortcut)", fontSize = 12.sp) },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = selectedScope == VariableScope.GLOBAL,
            onClick = { onSelectScope(VariableScope.GLOBAL) },
            label = { Text("Global (Retained)", fontSize = 12.sp) },
            modifier = Modifier.weight(1f)
        )
    }
}

private fun createToggleAction(key: String, op: ToggleActionType): TaskAction? {
    return when (key) {
        "wifi" -> TaskAction.Wifi(op)
        "mobile_data" -> TaskAction.MobileData(op)
        "flashlight" -> TaskAction.Flashlight(op)
        "low_power" -> TaskAction.LowPower(op)
        "airplane_mode" -> TaskAction.AirplaneMode(op)
        "bluetooth" -> TaskAction.Bluetooth(op)
        else -> null
    }
}

@Composable
private fun ActionBlockRow(
    title: String,
    icon: ImageVector,
    position: GroupPosition = GroupPosition.SINGLE,
    onClick: () -> Unit
) {
    val shape = getGroupedShape(position)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            },
            leadingContent = {
                CookieIconBox(
                    icon = icon,
                    contentDescription = title
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        )
    }
}

@Composable
private fun ActionOptionButton(
    label: String,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Text(text = label, fontWeight = FontWeight.SemiBold)
    }
}
