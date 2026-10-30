package dev.homescreen.test

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/** Optional, user-enabled Home redirect. No screen content is retrieved. */
@Suppress("DEPRECATION")
class HomeRedirectService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val policy = HomeRedirectPolicy()
    private var receiverRegistered = false
    private var bootCount = -1
    private val preferences by lazy { getSharedPreferences("launcher_startup", Context.MODE_PRIVATE) }

    private val redirectHome = Runnable { bringLauncherToFront("Google TV home opened") }
    private val showAfterWake = Runnable { bringLauncherToFront("startup or screen on") }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> scheduleWakeLaunch()
                Intent.ACTION_SCREEN_OFF -> cancelPendingLaunches()
                Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> {
                    if (intent.getStringExtra("reason") == "homekey") {
                        handler.removeCallbacks(redirectHome)
                        val delay = policy.homeKey(SystemClock.uptimeMillis())
                        handler.postDelayed(redirectHome, delay)
                        Log.d(TAG, "Home key; redirect scheduled after burst")
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                // System-sent Home-key broadcasts also occur when no window state changes.
                addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerLegacySystemReceiver(filter)
            }
            receiverRegistered = true
        }
        bootCount = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        val handledBoot = preferences.getInt("handled_boot_count", -1)
        // Works even if Android starts this service more than three minutes after boot.
        // Ordinary same-boot reconnects do not interrupt the app being watched.
        if (HomeRedirectPolicy.needsBootLaunch(bootCount, handledBoot)) scheduleWakeLaunch()
    }

    // API 31-32 have no RECEIVER_NOT_EXPORTED flag. Restrict senders to holders
    // of this signature permission instead; Android itself holds it.
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerLegacySystemReceiver(filter: IntentFilter) {
        registerReceiver(screenReceiver, filter, "android.permission.BROADCAST_CLOSE_SYSTEM_DIALOGS", handler)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() != GOOGLE_TV_PACKAGE) return
        if (event.className?.toString() != GOOGLE_TV_HOME_CLASS) return
        val delay = policy.request(SystemClock.uptimeMillis()) ?: return
        // Keep one trailing request rather than dropping Home presses during a cooldown.
        handler.postDelayed(redirectHome, delay)
    }

    private fun scheduleWakeLaunch() {
        handler.removeCallbacks(showAfterWake)
        handler.postDelayed(showAfterWake, POWER_ON_DELAY_MS)
    }

    private fun cancelPendingLaunches() {
        handler.removeCallbacks(redirectHome)
        handler.removeCallbacks(showAfterWake)
        policy.cancel()
    }

    private fun bringLauncherToFront(reason: String) {
        cancelPendingLaunches()
        if (getSystemService(PowerManager::class.java)?.isInteractive != true) return
        try {
            startActivity(Intent(this, HomeActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            ))
            policy.launched(SystemClock.uptimeMillis())
            if (bootCount >= 0) preferences.edit().putInt("handled_boot_count", bootCount).apply()
            Log.d(TAG, "$reason; requested custom launcher")
        } catch (error: SecurityException) {
            Log.w(TAG, "System denied launcher redirect", error)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        cancelPendingLaunches()
        if (receiverRegistered) runCatching { unregisterReceiver(screenReceiver) }
        receiverRegistered = false
        super.onDestroy()
    }

    private companion object {
        const val TAG = "HomeRedirect"
        const val POWER_ON_DELAY_MS = 1_500L
        const val GOOGLE_TV_PACKAGE = "com.google.android.apps.tv.launcherx"
        const val GOOGLE_TV_HOME_CLASS = "com.google.android.apps.tv.launcherx.home.HomeActivity"
    }
}
