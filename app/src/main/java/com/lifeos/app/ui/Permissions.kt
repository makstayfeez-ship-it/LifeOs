package com.lifeos.app.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lifeos.app.assistant.AssistantControl
import com.lifeos.app.assistant.ScreenWatcherService

object Perms {
    fun canOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun canExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Включён ли сервис «робот видит экран» в специальных возможностях. */
    fun watcherEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val me = "${context.packageName}/${ScreenWatcherService::class.java.name}"
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    fun has(context: Context, permission: String): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(context, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Доступ к контактам и звонкам: нужен для «позвони маме» и «напиши Ивану». */
    fun hasContacts(context: Context): Boolean = has(context, android.Manifest.permission.READ_CONTACTS)

    fun hasCall(context: Context): Boolean = has(context, android.Manifest.permission.CALL_PHONE)

    fun overlaySettings(context: Context) = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Сведения о приложении: там в меню ⋮ включается «Разрешить ограниченные настройки». */
    fun appDetailsSettings(context: Context) = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun notificationListenerSettings() =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Разрешено ли показывать окно на весь экран поверх блокировки (Android 14+ спрашивает это отдельно). */
    fun canFullScreenIntent(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            context.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()

    fun fullScreenIntentSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 34) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
        } else {
            appDetailsSettings(context)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun batterySettings() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun accessibilitySettings() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun exactAlarmSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * Возвращает функцию-запрос: вызовите её, и если доступ к микрофону уже есть, сразу выполнится [onGranted],
 * иначе покажется системный запрос, и [onGranted] выполнится после согласия.
 */
@Composable
fun rememberMicRequest(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) onGranted()
    }
    return {
        if (AssistantControl.hasMic(context)) onGranted() else launcher.launch(android.Manifest.permission.RECORD_AUDIO)
    }
}

/** Число, которое меняется при каждом возвращении на экран: нужно, чтобы заново проверить разрешения после настроек Android. */
@Composable
fun rememberResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

/** Запрашивает сразу несколько разрешений Android; [onResult] вызывается, когда пользователь ответил. */
@Composable
fun rememberPermissionsRequest(permissions: Array<String>, onResult: () -> Unit = {}): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onResult() }
    return { launcher.launch(permissions) }
}
