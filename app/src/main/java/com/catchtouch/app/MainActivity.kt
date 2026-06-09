package com.catchtouch.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SettingsManager.isHideFromRecents(this)) {
            try {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                am.appTasks?.forEach { it.setExcludeFromRecents(true) }
            } catch (_: Exception) {}
        }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFFCE93D8),
                secondary = Color(0xFFE1BEE7),
                surface = Color.White
            )) {
                MainScreen()
            }
        }
    }
}

private val Purple50 = Color(0xFFF3E5F5)
private val Purple100 = Color(0xFFE1BEE7)
private val Purple200 = Color(0xFFCE93D8)
private val Purple300 = Color(0xFFBA68C8)
private val Purple500 = Color(0xFF9C27B0)

data class AppInfo(val packageName: String, val label: String)

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var maskMode by remember { mutableStateOf(SettingsManager.getMode(context)) }
    var topPercent by remember { mutableStateOf(SettingsManager.getTop(context)) }
    var bottomPercent by remember { mutableStateOf(SettingsManager.getBottom(context)) }
    var leftPercent by remember { mutableStateOf(SettingsManager.getLeft(context)) }
    var rightPercent by remember { mutableStateOf(SettingsManager.getRight(context)) }
    var thumbPercent by remember { mutableStateOf(SettingsManager.getThumb(context)) }
    var enabled by remember { mutableStateOf(SettingsManager.isEnabled(context)) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(300)
            val serviceRunning = AntiTouchService.instance != null
            val savedEnabled = SettingsManager.isEnabled(context)
            if (!serviceRunning && savedEnabled) {
                SettingsManager.setEnabled(context, false)
                enabled = false
            } else {
                enabled = savedEnabled
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MaskView(ctx).apply {
                    this.maskMode = maskMode
                    this.topPercent = topPercent
                    this.bottomPercent = bottomPercent
                    this.leftPercent = leftPercent
                    this.rightPercent = rightPercent
                    this.thumbPercent = thumbPercent
                    this.showPreview = true
                }
            },
            update = { view ->
                view.maskMode = maskMode
                view.topPercent = topPercent
                view.bottomPercent = bottomPercent
                view.leftPercent = leftPercent
                view.rightPercent = rightPercent
                view.thumbPercent = thumbPercent
                view.showPreview = true
                view.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(visible = !showDialog, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.size(72.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(Purple200).clickable { showDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置", tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
        }

        AnimatedVisibility(visible = showDialog, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0x33000000))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(0.85f).clip(RoundedCornerShape(20.dp)).background(Color(0xF0FFFFFF))
                        .verticalScroll(rememberScrollState()).padding(20.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("CatchTouch 防误触", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Purple200)

                    PermissionCheck()

                    ModeDropdown(selected = maskMode, onSelect = { maskMode = it; SettingsManager.setMode(context, it) })

                    AppSelector()

                    if (maskMode == MaskMode.MODE_ONE || maskMode == MaskMode.MIXED) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SliderItem("顶部", topPercent, 0.15f, Modifier.weight(1f)) { v -> topPercent = v; SettingsManager.setTop(context, v) }
                            SliderItem("底部", bottomPercent, 0.15f, Modifier.weight(1f)) { v -> bottomPercent = v; SettingsManager.setBottom(context, v) }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SliderItem("左侧", leftPercent, 0.15f, Modifier.weight(1f)) { v -> leftPercent = v; SettingsManager.setLeft(context, v) }
                            SliderItem("右侧", rightPercent, 0.15f, Modifier.weight(1f)) { v -> rightPercent = v; SettingsManager.setRight(context, v) }
                        }
                    }

                    if (maskMode == MaskMode.MODE_TWO || maskMode == MaskMode.MIXED) {
                        SliderItem("拇指范围", thumbPercent, 0.30f, Modifier.fillMaxWidth()) { v -> thumbPercent = v; SettingsManager.setThumb(context, v) }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("启动开关", color = Purple300, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                val service = AntiTouchService.instance
                                if (service == null) {
                                    enabled = false
                                    SettingsManager.setEnabled(context, false)
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                                    return@Switch
                                }
                                if (checked) {
                                    service.enableMask()
                                    enabled = true
                                } else {
                                    service.disableMask()
                                    enabled = false
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Purple200, checkedTrackColor = Purple300)
                        )
                    }

                    var hideRecents by remember { mutableStateOf(SettingsManager.isHideFromRecents(context)) }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("隐藏后台", color = Purple300, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = hideRecents,
                            onCheckedChange = { checked ->
                                hideRecents = checked
                                SettingsManager.setHideFromRecents(context, checked)
                                if (checked) {
                                    try {
                                        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                                        am.appTasks?.forEach { it.setExcludeFromRecents(true) }
                                    } catch (_: Exception) {}
                                } else {
                                    try {
                                        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                                        am.appTasks?.forEach { it.setExcludeFromRecents(false) }
                                    } catch (_: Exception) {}
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Purple200, checkedTrackColor = Purple300)
                        )
                    }

                    Text("长按音量-键切换遮罩开关", color = Color(0xFFBBBBBB), fontSize = 11.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelector() {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var selectedApps by remember { mutableStateOf(SettingsManager.getSelectedApps(context)) }

    val installedApps = remember {
        val pm = context.packageManager
        pm.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .mapNotNull { appInfo ->
                val label = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { null }
                if (label != null) AppInfo(appInfo.packageName, label) else null
            }
            .sortedBy { it.label.lowercase() }
    }

    val sortedApps = remember(selectedApps, installedApps) {
        val selected = installedApps.filter { it.packageName in selectedApps }.sortedBy { it.label.lowercase() }
        val unselected = installedApps.filter { it.packageName !in selectedApps }.sortedBy { it.label.lowercase() }
        selected + unselected
    }

    val displayText = if (selectedApps.isEmpty()) "全部应用" else "已选${selectedApps.size}个应用"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text("针对应用", color = Purple300, fontSize = 12.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Purple300),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple200, unfocusedBorderColor = Purple100, focusedLabelColor = Purple300)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.height(250.dp)
        ) {
            DropdownMenuItem(
                text = { Text("全部应用（不选即全部生效）", color = if (selectedApps.isEmpty()) Purple300 else Purple200) },
                onClick = {
                    selectedApps = emptySet()
                    SettingsManager.setSelectedApps(context, emptySet())
                }
            )
            sortedApps.forEach { app ->
                val isSelected = selectedApps.contains(app.packageName)
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (isSelected) "✓ " else "  ",
                                color = Purple300,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                app.label,
                                color = if (isSelected) Purple300 else Purple200,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    onClick = {
                        val newSet = if (isSelected) {
                            selectedApps - app.packageName
                        } else {
                            selectedApps + app.packageName
                        }
                        selectedApps = newSet
                        SettingsManager.setSelectedApps(context, newSet)
                    }
                )
            }
        }
    }
}

@Composable
fun PermissionCheck() {
    val context = LocalContext.current
    var accessibilityEnabled by remember { mutableStateOf(false) }
    var overlayEnabled by remember { mutableStateOf(false) }
    var usageStatsEnabled by remember { mutableStateOf(false) }
    var batteryOptimized by remember { mutableStateOf(true) }

    fun checkPermissions() {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val cn = ComponentName(context, AntiTouchService::class.java)
        val flat = cn.flattenToString()
        accessibilityEnabled = enabledServices.contains(flat) || enabledServices.contains(cn.packageName)
        overlayEnabled = Settings.canDrawOverlays(context)
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(0, now - 60000, now)
            usageStatsEnabled = stats != null && stats.isNotEmpty()
        } catch (_: Exception) {
            usageStatsEnabled = false
        }
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        batteryOptimized = !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    androidx.lifecycle.compose.LifecycleEventEffect(event = androidx.lifecycle.Lifecycle.Event.ON_RESUME) { checkPermissions() }
    LaunchedEffect(Unit) { checkPermissions() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!accessibilityEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("开启无障碍服务", color = Color.White, fontSize = 13.sp) }
                Text("设置 → 无障碍 → CatchTouch → 开启", color = Color(0xFF999999), fontSize = 11.sp)
            }
        }
        if (!overlayEnabled) {
            Button(
                onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000)),
                modifier = Modifier.fillMaxWidth()
            ) { Text("允许悬浮窗权限", color = Color.White, fontSize = 13.sp) }
        }
        if (!usageStatsEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("允许查看使用情况", color = Color.White, fontSize = 13.sp) }
                Text("设置 → 应用管理 → 特殊权限 → 查看使用情况 → CatchTouch", color = Color(0xFF999999), fontSize = 11.sp)
            }
        }
        if (batteryOptimized && accessibilityEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = {
                        try {
                            context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                        } catch (_: Exception) {
                            context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("关闭电池优化（防杀后台）", color = Color.White, fontSize = 13.sp) }
                Text("OPPO/一加: 还需开启 自启动 + 后台运行允许", color = Color(0xFFE53935), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("设置 → 应用管理 → CatchTouch → 电池 → 不限制", color = Color(0xFF999999), fontSize = 11.sp)
                Text("设置 → 应用管理 → CatchTouch → 自启动 → 开启", color = Color(0xFF999999), fontSize = 11.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeDropdown(selected: MaskMode, onSelect: (MaskMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label, onValueChange = {}, readOnly = true,
            label = { Text("模式", color = Purple300, fontSize = 12.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Purple300),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple200, unfocusedBorderColor = Purple100, focusedLabelColor = Purple300)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MaskMode.entries.forEach { mode ->
                DropdownMenuItem(text = { Text(mode.label, color = Purple300) }, onClick = { onSelect(mode); expanded = false })
            }
        }
    }
}

@Composable
fun SliderItem(label: String, value: Float, maxValue: Float, modifier: Modifier = Modifier, onValueChange: (Float) -> Unit) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Purple300, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("${(value * 100).toInt()}%", color = Purple300, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value, onValueChange = onValueChange, valueRange = 0f..maxValue,
            colors = SliderDefaults.colors(thumbColor = Purple200, activeTrackColor = Purple100, inactiveTrackColor = Purple50),
            modifier = Modifier.fillMaxWidth().height(24.dp)
        )
    }
}
