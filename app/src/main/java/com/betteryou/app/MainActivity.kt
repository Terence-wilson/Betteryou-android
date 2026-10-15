package com.betteryou.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betteryou.app.notifications.NotificationRoute
import com.betteryou.app.notifications.Reminders
import com.betteryou.app.ui.BetterYouApp
import com.betteryou.app.ui.theme.BetterYouTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Screen requested by a tapped notification, consumed once shown. */
    private val pendingRoute = MutableStateFlow<NotificationRoute?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)

        setContent {
            BetterYouTheme {
                val route by pendingRoute.collectAsStateWithLifecycle()
                BetterYouApp(
                    pendingRoute = route,
                    onRouteHandled = { pendingRoute.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val route = intent?.getStringExtra(Reminders.EXTRA_ROUTE) ?: return
        pendingRoute.value = NotificationRoute.entries.firstOrNull { it.name == route }
    }
}
