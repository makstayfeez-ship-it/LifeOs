package com.lifeos.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.lifeos.app.assistant.AssistantControl
import com.lifeos.app.brief.BriefScheduler
import com.lifeos.app.container
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** FragmentActivity нужен для окна отпечатка пальца (BiometricPrompt). */
class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()

    /** Микрофон и уведомления просим одним окном при первом запуске; после согласия помощник сразу начинает слушать. */
    private val startupPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (AssistantControl.hasMic(this)) {
                lifecycleScope.launch {
                    if (container.settings.current().wakeEnabled) AssistantControl.startListening(this@MainActivity)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.openRoute(intent?.getStringExtra(EXTRA_ROUTE))
        setContent { AppNav(vm) }

        val missing = buildList {
            if (!AssistantControl.hasMic(this@MainActivity)) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missing.isNotEmpty() && savedInstanceState == null) startupPermissions.launch(missing.toTypedArray())

        // «Запретить скриншоты»: окно помечается защищённым и пропадает из превью последних приложений
        lifecycleScope.launch {
            container.settings.flow.map { it.secureScreen }.distinctUntilChanged().collect { secure ->
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        // Android не даёт запускать микрофонный сервис из фона (в том числе после перезагрузки),
        // поэтому прослушивание возвращается, когда пользователь открывает приложение.
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val s = container.settings.current()
                if (s.wakeEnabled) AssistantControl.startListening(this@MainActivity)
                if (s.morningEnabled) BriefScheduler.schedule(this@MainActivity, s.morningHour, s.morningMinute)
                com.lifeos.app.reminder.ReminderScheduler.rescheduleAll(this@MainActivity)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onAppStarted()
    }

    override fun onStop() {
        super.onStop()
        vm.onAppStopped()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.openRoute(intent.getStringExtra(EXTRA_ROUTE))
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
