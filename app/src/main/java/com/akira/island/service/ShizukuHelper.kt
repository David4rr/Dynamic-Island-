package com.akira.island.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors

object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    const val SHIZUKU_PERMISSION_REQUEST_CODE = 4001
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private const val ICON_BLACKLIST_FULL = "wifi,cell,mobile,phone_signal,phone_evdo_signal,data_connection,no_calling,volte,ims_volte,wifi_calling,vowifi,battery,battery_saver,power_saver,clock,time,volume,mute,vibrate,bluetooth,bt,bluetooth_connected,bluetooth_battery,bt_battery,bt_connected,hearing_aid,bluetooth_hearing_aid,headset,headphone,earphone,earphones,alarm_clock,alarm,zen,dnd,do_not_disturb,managed_profile,nfc,nfc_on,cast,hotspot,vpn,airplane,location,gps,camera,microphone,screen_record,connected_display,sync_active,sync_failing,rotate,eye_protect,notification,notifications,notification_icons,notification_badge,notification_badges,badges,more_notifications,app_badge,app_icon,new_message,message,mail,sms"

    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    fun hasShizukuPermission(): Boolean {
        if (!isShizukuAvailable()) return false
        return try {
            if (Shizuku.isPreV11()) false
            else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun hasWriteSecureSettings(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    fun requestShizukuPermission(): Boolean {
        return try {
            if (isShizukuAvailable() && !hasShizukuPermission()) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                true
            } else {
                false
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
            false
        }
    }

    fun grantWriteSecureSettings(packageName: String, onResult: (Boolean) -> Unit) {
        if (!isShizukuAvailable() || !hasShizukuPermission()) {
            onResult(false)
            return
        }
        executor.execute {
            val success = runShellCommand("pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
            mainHandler.post { onResult(success) }
        }
    }

    fun isHeadsUpNotificationsDisabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, "heads_up_notifications_enabled", 1) == 0
        } catch (_: Throwable) {
            false
        }
    }

    fun isStatusBarIconsHidden(context: Context): Boolean {
        return try {
            val blacklist = Settings.Secure.getString(context.contentResolver, "icon_blacklist")
            !blacklist.isNullOrBlank()
        } catch (_: Throwable) {
            false
        }
    }

    fun setHeadsUpNotificationsDisabled(context: Context, disabled: Boolean, onResult: ((Boolean) -> Unit)? = null) {
        executor.execute {
            var success = false
            val value = if (disabled) 0 else 1

            if (hasWriteSecureSettings(context)) {
                try {
                    Settings.Global.putInt(context.contentResolver, "heads_up_notifications_enabled", value)
                    success = true
                } catch (e: Throwable) {
                    Log.w(TAG, "Direct Settings write for heads_up failed", e)
                }
            }

            if (isShizukuAvailable() && hasShizukuPermission()) {
                val cmd = "settings put global heads_up_notifications_enabled $value"
                val ok = runShellCommand(cmd)
                if (ok) success = true
            }

            if (!success) {
                try {
                    val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put global heads_up_notifications_enabled $value"))
                    p.waitFor()
                    success = p.exitValue() == 0
                } catch (_: Throwable) {}
            }

            mainHandler.post { onResult?.invoke(success) }
        }
    }

    fun setSystemStatusBarIconsHidden(context: Context, hidden: Boolean, onResult: ((Boolean) -> Unit)? = null) {
        executor.execute {
            var success = false

            if (hasWriteSecureSettings(context)) {
                try {
                    if (hidden) {
                        Settings.Secure.putString(context.contentResolver, "icon_blacklist", ICON_BLACKLIST_FULL)
                        Settings.Global.putString(context.contentResolver, "policy_control", "immersive.status=*")
                        Settings.Secure.putString(context.contentResolver, "status_bar_show_notification_icon", "0")
                        Settings.Secure.putString(context.contentResolver, "show_notification_icon", "0")
                        Settings.Secure.putString(context.contentResolver, "notification_badging", "0")
                        Settings.Secure.putString(context.contentResolver, "status_bar_show_badges", "0")
                        Settings.System.putString(context.contentResolver, "status_bar_show_notification_icon", "0")
                        Settings.System.putString(context.contentResolver, "show_notification_icon", "0")
                    } else {
                        Settings.Secure.putString(context.contentResolver, "icon_blacklist", "")
                        Settings.Global.putString(context.contentResolver, "policy_control", "")
                        Settings.Secure.putString(context.contentResolver, "status_bar_show_notification_icon", "1")
                        Settings.Secure.putString(context.contentResolver, "show_notification_icon", "1")
                        Settings.Secure.putString(context.contentResolver, "notification_badging", "1")
                        Settings.Secure.putString(context.contentResolver, "status_bar_show_badges", "1")
                        Settings.System.putString(context.contentResolver, "status_bar_show_notification_icon", "1")
                        Settings.System.putString(context.contentResolver, "show_notification_icon", "1")
                    }
                    success = true
                } catch (e: Throwable) {
                    Log.w(TAG, "Direct Settings write failed, falling back to shell", e)
                }
            }

            val commands = if (hidden) {
                listOf(
                    "settings put secure icon_blacklist \"$ICON_BLACKLIST_FULL\"",
                    "settings put global policy_control \"immersive.status=*\"",
                    "settings put secure status_bar_show_notification_icon 0",
                    "settings put secure show_notification_icon 0",
                    "settings put secure notification_badging 0",
                    "settings put secure status_bar_show_badges 0",
                    "settings put system status_bar_show_notification_icon 0",
                    "settings put system show_notification_icon 0"
                )
            } else {
                listOf(
                    "settings delete secure icon_blacklist",
                    "settings put secure icon_blacklist \"\"",
                    "settings delete global policy_control",
                    "settings put global policy_control \"\"",
                    "settings delete secure status_bar_show_notification_icon",
                    "settings put secure status_bar_show_notification_icon 1",
                    "settings delete secure show_notification_icon",
                    "settings put secure show_notification_icon 1",
                    "settings delete secure notification_badging",
                    "settings put secure notification_badging 1",
                    "settings delete secure status_bar_show_badges",
                    "settings put secure status_bar_show_badges 1",
                    "settings delete system status_bar_show_notification_icon",
                    "settings put system status_bar_show_notification_icon 1",
                    "settings delete system show_notification_icon",
                    "settings put system show_notification_icon 1"
                )
            }

            if (isShizukuAvailable() && hasShizukuPermission()) {
                for (cmd in commands) {
                    runShellCommand(cmd)
                }
                success = true
            }

            try {
                for (cmd in commands) {
                    val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                    p.waitFor()
                }
            } catch (_: Throwable) {}

            mainHandler.post { onResult?.invoke(success) }
        }
    }

    private fun runShellCommand(command: String): Boolean {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as java.lang.Process
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val reader = BufferedReader(InputStreamReader(process.errorStream))
                val err = reader.readText()
                Log.e(TAG, "Command failed ($exitCode): $err")
            }
            exitCode == 0
        } catch (e: Throwable) {
            Log.e(TAG, "Error executing shell command via Shizuku: $command", e)
            false
        }
    }

    fun getShizukuLaunchIntent(context: Context): Intent? {
        val pm = context.packageManager
        return pm.getLaunchIntentForPackage("moe.shizuku.privileged.api")
    }

    fun openShizukuOrStore(context: Context) {
        val launchIntent = getShizukuLaunchIntent(context)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        } else {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
            } catch (_: Exception) {
            }
        }
    }
}
