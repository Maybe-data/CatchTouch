package com.catchtouch.app

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.net.toUri
import com.google.android.accessibility.selecttospeak.SelectToSpeakService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SettingsManager.isHideFromRecents(this)) {
            try {
                val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
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

data class AppInfo(val packageName: String, val label: String)

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var maskMode by remember { mutableStateOf(SettingsManager.getMode(context)) }
    var topPercent by remember { mutableStateOf(SettingsManager.getTop(context)) }
    var bottomPercent by remember { mutableStateOf(SettingsManager.getBottom(context)) }
    var leftPercent by remember { mutableStateOf(SettingsManager.getLeft(context)) }
    var rightPercent by remember { mutableStateOf(SettingsManager.getRight(context)) }
    var thumbLeft by remember { mutableStateOf(SettingsManager.getThumbLeft(context)) }
    var thumbRight by remember { mutableStateOf(SettingsManager.getThumbRight(context)) }
    var leftHoleHeight by remember { mutableStateOf(SettingsManager.getLeftHoleHeight(context)) }
    var leftHolePos by remember { mutableStateOf(SettingsManager.getLeftHolePos(context)) }
    var rightHoleHeight by remember { mutableStateOf(SettingsManager.getRightHoleHeight(context)) }
    var rightHolePos by remember { mutableStateOf(SettingsManager.getRightHolePos(context)) }
    var enabled by remember { mutableStateOf(SettingsManager.isEnabled(context)) }
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            delay(300)
            val serviceRunning = SelectToSpeakService.instance != null
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
                    this.thumbLeftPercent = thumbLeft
                    this.thumbRightPercent = thumbRight
                    this.leftHoleHeightPercent = leftHoleHeight
                    this.leftHolePosPercent = leftHolePos
                    this.rightHoleHeightPercent = rightHoleHeight
                    this.rightHolePosPercent = rightHolePos
                    this.showPreview = true
                }
            },
            update = { view ->
                view.maskMode = maskMode
                view.topPercent = topPercent
                view.bottomPercent = bottomPercent
                view.leftPercent = leftPercent
                view.rightPercent = rightPercent
                view.thumbLeftPercent = thumbLeft
                view.thumbRightPercent = thumbRight
                view.leftHoleHeightPercent = leftHoleHeight
                view.leftHolePosPercent = leftHolePos
                view.rightHoleHeightPercent = rightHoleHeight
                view.rightHolePosPercent = rightHolePos
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
                        CollapsibleSection(title = "边框遮罩") {
                            SliderItem("顶部", topPercent, 0.15f) { v -> topPercent = v; SettingsManager.setTop(context, v) }
                            SliderItem("底部", bottomPercent, 0.15f) { v -> bottomPercent = v; SettingsManager.setBottom(context, v) }
                            SliderItem("左侧", leftPercent, 0.15f) { v -> leftPercent = v; SettingsManager.setLeft(context, v) }
                            SliderItem("右侧", rightPercent, 0.15f) { v -> rightPercent = v; SettingsManager.setRight(context, v) }
                        }
                        if (leftPercent > 0f || rightPercent > 0f) {
                            CollapsibleSection(title = "挖孔") {
                                if (leftPercent > 0f) {
                                    SliderItem("左挖孔高", leftHoleHeight, 0.30f) { v -> leftHoleHeight = v; SettingsManager.setLeftHoleHeight(context, v) }
                                    SliderItem("左挖孔位", leftHolePos, 1f) { v -> leftHolePos = v; SettingsManager.setLeftHolePos(context, v) }
                                }
                                if (rightPercent > 0f) {
                                    SliderItem("右挖孔高", rightHoleHeight, 0.30f) { v -> rightHoleHeight = v; SettingsManager.setRightHoleHeight(context, v) }
                                    SliderItem("右挖孔位", rightHolePos, 1f) { v -> rightHolePos = v; SettingsManager.setRightHolePos(context, v) }
                                }
                            }
                        }
                    }

                    if (maskMode == MaskMode.MODE_TWO || maskMode == MaskMode.MIXED) {
                        CollapsibleSection(title = "拇指扇形") {
                            SliderItem("左拇指", thumbLeft, 0.30f) { v -> thumbLeft = v; SettingsManager.setThumbLeft(context, v) }
                            SliderItem("右拇指", thumbRight, 0.30f) { v -> thumbRight = v; SettingsManager.setThumbRight(context, v) }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("启动开关", color = Purple300, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                val service = SelectToSpeakService.instance
                                if (service == null) {
                                    val cn = ComponentName(context, SelectToSpeakService::class.java)
                                    val enabledServices = Settings.Secure.getString(
                                        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                                    ) ?: ""
                                    val authorized = enabledServices.contains(cn.flattenToString()) ||
                                            enabledServices.contains(cn.packageName)
                                    if (!authorized) {
                                        enabled = false
                                        SettingsManager.setEnabled(context, false)
                                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                                        return@Switch
                                    }
                                    // 已授权但系统尚未绑定（部分 ROM 重启后延迟绑定）：等待重绑，免跳转
                                    Toast.makeText(context, "正在恢复无障碍服务…", Toast.LENGTH_SHORT).show()
                                    scope.launch {
                                        var waited = 0L
                                        while (SelectToSpeakService.instance == null && waited < 2000L) {
                                            delay(100); waited += 100
                                        }
                                        val svc = SelectToSpeakService.instance
                                        if (svc != null) {
                                            svc.enableMask()
                                            enabled = true
                                        } else {
                                            SettingsManager.setEnabled(context, false)
                                            enabled = false
                                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                                        }
                                    }
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
                                try {
                                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                                    am.appTasks?.forEach { it.setExcludeFromRecents(checked) }
                                } catch (_: Exception) {}
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Purple200, checkedTrackColor = Purple300)
                        )
                    }

                    Text("长按音量+键切换遮罩开关", color = Color(0xFFBBBBBB), fontSize = 11.sp)
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
        @Suppress("DEPRECATION")
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
            onValueChange = { _ -> },
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
                val isSelected = app.packageName in selectedApps
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
                        val newSet = if (isSelected) selectedApps - app.packageName else selectedApps + app.packageName
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
    var batteryOptimized by remember { mutableStateOf(true) }
    var notificationDisabled by remember { mutableStateOf(false) }
    var secureWriteGranted by remember { mutableStateOf(false) }

    fun checkPermissions() {
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val cn = ComponentName(context, SelectToSpeakService::class.java)
        accessibilityEnabled = enabledServices.contains(cn.flattenToString()) || enabledServices.contains(cn.packageName)
        overlayEnabled = Settings.canDrawOverlays(context)
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        batteryOptimized = !pm.isIgnoringBatteryOptimizations(context.packageName)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationDisabled = !nm.areNotificationsEnabled()
        secureWriteGranted = SelectToSpeakService.hasSecureWritePermission(context)
    }

    fun doGrant() {
        ShizukuHelper.grantWriteSecureSettings(context) { ok, msg ->
            Toast.makeText(context, if (ok) "已授权静默重开" else msg, Toast.LENGTH_SHORT).show()
            checkPermissions()
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode == ShizukuHelper.REQUEST_CODE && grantResult == PackageManager.PERMISSION_GRANTED) {
                    doGrant()
                }
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        onDispose { Shizuku.removeRequestPermissionResultListener(listener) }
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
        if (!secureWriteGranted && ShizukuHelper.isRunning()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = {
                        if (!ShizukuHelper.isRunning()) {
                            Toast.makeText(context, "Shizuku 未运行，请先启动", Toast.LENGTH_SHORT).show()
                        } else if (!ShizukuHelper.hasShizukuPermission()) {
                            ShizukuHelper.requestPermission()
                        } else {
                            doGrant()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000)),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("通过 Shizuku 授权（静默重开）", color = Color.White, fontSize = 13.sp) }
                Text("需保持 Shizuku 运行，授权一次后不再需要", color = Color(0xFF999999), fontSize = 10.sp)
            }
        }
        if (!overlayEnabled) {
            Button(
                onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000)),
                modifier = Modifier.fillMaxWidth()
            ) { Text("允许悬浮窗权限", color = Color.White, fontSize = 13.sp) }
        }
        if (notificationDisabled) {
            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                modifier = Modifier.fillMaxWidth()
            ) { Text("允许通知权限", color = Color.White, fontSize = 13.sp) }
        }
        if (batteryOptimized && accessibilityEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri()))
                        } catch (_: Exception) {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
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
            value = selected.label, onValueChange = { _ -> }, readOnly = true,
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
fun CollapsibleSection(title: String, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(if (expanded) 0f else 90f, label = "arrow")
    Column {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Purple300, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Purple300,
                modifier = Modifier.size(20.dp).rotate(arrowRotation)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) { content() }
        }
    }
}

@Composable
fun SliderItem(label: String, value: Float, maxValue: Float, modifier: Modifier = Modifier, onValueChange: (Float) -> Unit) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Purple300, fontSize = 12.sp, modifier = Modifier.width(64.dp))
        Slider(
            value = value, onValueChange = onValueChange, valueRange = 0f..maxValue,
            colors = SliderDefaults.colors(thumbColor = Purple200, activeTrackColor = Purple100, inactiveTrackColor = Purple50),
            modifier = Modifier.weight(1f).height(24.dp)
        )
        Text(
            "${(value * 100).toInt()}%",
            color = Purple300, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End, modifier = Modifier.width(38.dp)
        )
    }
}
