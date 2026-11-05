package com.lifeos.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.lifeos.app.assistant.AssistantControl
import com.lifeos.app.brief.BriefScheduler
import com.lifeos.app.container
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.openRoute(intent?.getStringExtra(EXTRA_ROUTE))
        setContent { AppNav(vm) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Android не даёт запускать микрофонный сервис из фона (в том числе после перезагрузки),
        // поэтому прослушивание возвращается, когда пользователь открывает приложение.
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val s = container.settings.current()
                if (s.wakeEnabled) AssistantControl.startListening(this@MainActivity)
                if (s.morningEnabled) BriefScheduler.schedule(this@MainActivity, s.morningHour, s.morningMinute)
            }
        }
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
