package com.akira.island.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import com.akira.island.service.ShizukuHelper
import com.akira.island.service.SystemBanners
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import rikka.shizuku.Shizuku
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile
import com.akira.island.ui.Ui
import com.akira.island.ui.posOf

/**
 * Системные плашки, которые дублируют остров. Убрать чужую всплывающую плашку приложение не может,
 * поэтому здесь — быстрый переход к нужной настройке уведомлений: уведомление остаётся в шторке,
 * а на экране событие показывает только остров. Мессенджеры — те, чьи сообщения уже были в острове.
 */
@Composable
fun BannersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val seen by SystemBanners.seen.collectAsState()
    val calls = remember(seen) { SystemBanners.callApps(context, seen) }
    val clocks = remember(seen) { SystemBanners.clockApps(context, seen) }
    val s by SettingsRepo.state.collectAsState()
    val popUp by SystemBanners.popUp.collectAsState()
    val messengers = remember(s.msgChannels) { s.msgChannels.keys.sortedBy { SystemBanners.label(context, it).lowercase() } }

    var isShizukuAvailable by remember { mutableStateOf(ShizukuHelper.isShizukuAvailable()) }
    var hasShizukuPermission by remember { mutableStateOf(ShizukuHelper.hasShizukuPermission()) }
    var isHeadsUpDisabled by remember { mutableStateOf(ShizukuHelper.isHeadsUpNotificationsDisabled(context)) }
    var isStatusBarHidden by remember { mutableStateOf(ShizukuHelper.isStatusBarIconsHidden(context)) }

    fun refreshShizukuStatus() {
        isShizukuAvailable = ShizukuHelper.isShizukuAvailable()
        hasShizukuPermission = ShizukuHelper.hasShizukuPermission()
        isHeadsUpDisabled = ShizukuHelper.isHeadsUpNotificationsDisabled(context)
        isStatusBarHidden = ShizukuHelper.isStatusBarIconsHidden(context)
    }

    LaunchedEffect(Unit) {
        refreshShizukuStatus()
    }

    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            hasShizukuPermission = grantResult == PackageManager.PERMISSION_GRANTED
            refreshShizukuStatus()
        }
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
        onDispose {
            runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
        }
    }
    Page(tr("Системные плашки", "System banners"), onBack) {
        SectionHeader(tr("Блокировка через Shizuku (Рекомендуется)", "Automatic via Shizuku (Recommended)"))

        Group {
            GroupItem(
                pos = Pos.First,
                icon = Icons.Rounded.Bolt,
                title = tr("Служба Shizuku", "Shizuku Service"),
                subtitle = when {
                    !isShizukuAvailable -> tr("Служба Shizuku не запущена — нажмите для открытия", "Shizuku not running — tap to open")
                    !hasShizukuPermission -> tr("Требуется разрешение — нажмите для запроса", "Permission needed — tap to grant")
                    else -> tr("Служба активна и готова к работе ✓", "Service is active and ready ✓")
                },
                color = when {
                    isShizukuAvailable && hasShizukuPermission -> Tile.Green
                    isShizukuAvailable -> Tile.Orange
                    else -> Tile.Gray
                },
                badge = if (isShizukuAvailable && hasShizukuPermission) tr("Активен ✓", "Active ✓") else tr("Требуется", "Needed"),
                onClick = {
                    if (!isShizukuAvailable) {
                        ShizukuHelper.openShizukuOrStore(context)
                    } else if (!hasShizukuPermission) {
                        ShizukuHelper.requestShizukuPermission()
                    }
                }
            )

            GroupToggle(
                pos = Pos.Middle,
                icon = Icons.Rounded.NotificationsOff,
                title = tr("Блокировать всплывающие плашки", "Block System Pop-Up Banners"),
                subtitle = tr(
                    "Отключает стандартные белые баннеры системы (heads_up_notifications_enabled 0). События показывает только остров.",
                    "Disables default system pop-up banners (heads_up_notifications_enabled 0). Only the island will show them."
                ),
                checked = isHeadsUpDisabled,
                color = Tile.Red,
                onChange = { disabled ->
                    ShizukuHelper.setHeadsUpNotificationsDisabled(context, disabled) {
                        refreshShizukuStatus()
                    }
                }
            )

            GroupToggle(
                pos = Pos.Middle,
                icon = Icons.Rounded.VisibilityOff,
                title = tr("Скрыть значки в строке состояния", "Hide Status Bar Icons"),
                subtitle = tr(
                    "Скрывает значки уведомлений и системные иконки в статус-баре для чистого экрана.",
                    "Hides notification and system icons in the status bar for a clean display."
                ),
                checked = isStatusBarHidden,
                color = Tile.Blue,
                onChange = { hidden ->
                    ShizukuHelper.setSystemStatusBarIconsHidden(context, hidden) {
                        refreshShizukuStatus()
                    }
                }
            )

            GroupItem(
                pos = Pos.Last,
                icon = Icons.Rounded.ContentCopy,
                title = tr("Команда ADB (если без Shizuku)", "Copy ADB Command"),
                subtitle = "settings put global heads_up_notifications_enabled 0",
                color = Tile.Gray,
                onClick = {
                    clipboardManager.setText(AnnotatedString("settings put global heads_up_notifications_enabled 0"))
                    Toast.makeText(context, "Perintah ADB berhasil disalin!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        Box(
            Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(24.dp)).background(Ui.Card)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                tr(
                    "Или настройте приложения вручную: нажмите на приложение ниже и выключите «Всплывающие уведомления». " +
                        "Уведомление останется в шторке, а на экране событие покажет только остров.",
                    "Or configure apps manually: tap an app below and turn off “Pop on screen”. " +
                        "The notification stays in the shade, and only the island shows the event on screen.",
                ),
                color = Ui.Sub, fontSize = 14.sp, lineHeight = 19.sp,
            )
        }

        // Сообщения приходят и плашкой острова, и своей всплывающей — две сразу
        SectionHeader(tr("Мессенджеры", "Messengers"))
        if (messengers.isEmpty()) {
            GroupItem(Pos.Single, Icons.Rounded.ChatBubble, tr("Пока ни одно сообщение не пришло в остров", "No message has reached the island yet"),
                color = Tile.Gray)
        } else {
            Group {
                messengers.forEachIndexed { i, pkg ->
                    GroupItem(
                        posOf(i, messengers.size), Icons.Rounded.ChatBubble, SystemBanners.label(context, pkg),
                        when (popUp[pkg]) {
                            true -> tr("Всплывает поверх острова — нажмите и выключите «Всплывающие»",
                                "Pops up over the island — tap and turn off “Pop on screen”")
                            false -> tr("Своя плашка не всплывает ✓", "Its own banner doesn't pop up ✓")
                            null -> tr("Нажмите и выключите «Всплывающие уведомления»", "Tap and turn off “Pop on screen”")
                        },
                        color = if (popUp[pkg] == false) Tile.Green else Tile.Blue,
                        onClick = { SystemBanners.openSettings(context, pkg) },
                    ) { Arrow() }
                }
            }
        }
        Text(
            tr(
                "Если выключить всплывающие у мессенджера, а остров потом выключить или скрыть, сообщения будут только в шторке.",
                "If you turn off a messenger's pop-ups and then turn the island off or hide it, messages will only be in the shade.",
            ),
            color = Ui.Sub, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        )

        SectionHeader(tr("Звонки", "Calls"))
        AppList(calls, Icons.Rounded.Call, Tile.Green,
            tr("Канал входящих вызовов. Экран при звонке включит сам остров", "The incoming calls channel. The island turns the screen on for calls itself"),
            tr("Звонилки не нашлось — позвоните себе, и она появится здесь",
                "No dialer found — call yourself and it will show up here")) { SystemBanners.openSettings(context, it) }

        SectionHeader(tr("Таймеры и будильники", "Timers and alarms"))
        AppList(clocks, Icons.Rounded.Alarm, Tile.Orange,
            tr("Сигнал таймера и будильника", "Timer and alarm alerts"),
            tr("Приложение «Часы» не нашлось", "No Clock app found")) { SystemBanners.openSettings(context, it) }
        Text(
            tr(
                "Совет: не выключайте уведомления «Часов» целиком — выберите «Без звука» и «Свернуть» (или выключите только " +
                    "«Всплывающие»). Тогда таймер и секундомер появятся в острове мгновенно, а его кнопки будут работать, " +
                    "не открывая «Часы». Если уведомления выключены совсем, остров узнаёт таймер с экрана «Часов», " +
                    "а кнопки нажимает сам, на мгновение открывая приложение.",
                "Tip: don't turn off Clock's notifications completely — choose “Silent” and “Minimize” (or turn off only " +
                    "“Pop on screen”). Then the timer and stopwatch show up in the island instantly, and its buttons work " +
                    "without opening Clock. If the notifications are fully off, the island reads the timer from the Clock " +
                    "screen and presses the buttons itself, opening the app for a moment.",
            ),
            color = Ui.Sub, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        )

        SectionHeader(tr("Батарея и зарядка", "Battery and charging"))
        Group {
            GroupItem(Pos.First, Icons.Rounded.BatteryAlert, SystemBanners.label(context, SystemBanners.SYSTEM_UI),
                tr("«Батарея разряжена» — канал «Батарея». У некоторых прошивок система менять его не даёт",
                    "“Battery low” — the Battery channel. Some firmwares don't let you change it"),
                color = Tile.Red, onClick = { SystemBanners.openSettings(context, SystemBanners.SYSTEM_UI) }) { Arrow() }
            GroupItem(Pos.Last, Icons.Rounded.Usb, SystemBanners.label(context, SystemBanners.ANDROID),
                tr("Зарядка через USB и другие сообщения системы", "USB charging and other system messages"),
                color = Tile.Gray, onClick = { SystemBanners.openSettings(context, SystemBanners.ANDROID) }) { Arrow() }
        }
        Text(
            tr(
                "Анимацию зарядки, которую рисует сама прошивка (Xiaomi, Samsung и др.), отключают в настройках экрана блокировки " +
                    "или батареи телефона, если производитель это позволяет.",
                "The charging animation drawn by the firmware itself (Xiaomi, Samsung and others) is turned off in the " +
                    "phone's lock screen or battery settings, if the manufacturer allows it.",
            ),
            color = Ui.Sub, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun AppList(
    packages: List<String>,
    icon: ImageVector,
    color: Color,
    hint: String,
    empty: String,
    onOpen: (String) -> Unit,
) {
    val context = LocalContext.current
    if (packages.isEmpty()) {
        GroupItem(Pos.Single, icon, empty, color = Tile.Gray)
        return
    }
    Group {
        packages.forEachIndexed { i, pkg ->
            GroupItem(posOf(i, packages.size), icon, SystemBanners.label(context, pkg), hint, color = color,
                onClick = { onOpen(pkg) }) { Arrow() }
        }
    }
}
