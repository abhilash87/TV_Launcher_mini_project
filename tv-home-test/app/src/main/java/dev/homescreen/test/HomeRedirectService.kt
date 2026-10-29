package dev.homescreen.test

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Brings this launcher to the front when:
 *  - Google TV's home activity opens (Home button), since this TV always resolves HOME to
 *    Google TV's privileged launcher even when this app holds the HOME role;
 *  - the TV is switched on (screen on), since the TV otherwise resumes its last input (HDMI 1);
 *  - the TV has just cold-booted and this service connects for the first time.
 *
 * It receives accessibility events from the Google TV launcher package only (see
 * home_redirect_service.xml) and cannot read window content.
 */
class HomeRedirectService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastRedirectAt = 0L

    private val showLauncher = Runnable { bringLauncherToFront("power on") }

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_ON) return
            // Let the TV finish restoring its last input first, then cover it with the launcher.
            handler.removeCallbacks(showLauncher)
            handler.postDelayed(showLauncher, POWER_ON_DELAY_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOnReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenOnReceiver, filter)
        }
        // Accessibility services are started during boot; if that is why we are connecting,
        // treat it like a power-on. A later reconnect (e.g. after an app update) does nothing.
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) {
            handler.postDelayed(showLauncher, POWER_ON_DELAY_MS)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.className?.toString() != GOOGLE_TV_HOME_CLASS) return

        // The user picked the Google TV tile. Google TV fires more than one window event while it
        // opens, so the pass covers the whole window rather than just the first event.
        if (SystemClock.uptimeMillis() < allowGoogleTvUntil) return
        bringLauncherToFront("Google TV home opened")
    }

    private fun bringLauncherToFront(reason: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastRedirectAt < DEBOUNCE_MS) return
        lastRedirectAt = now

        Log.d(TAG, "$reason; showing custom launcher")
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        )
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenOnReceiver) }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HomeRedirect"
        private const val DEBOUNCE_MS = 1_000L
        private const val POWER_ON_DELAY_MS = 1_500L
        private const val BOOT_WINDOW_MS = 3 * 60_000L
        private const val GOOGLE_TV_HOME_CLASS = "com.google.android.apps.tv.launcherx.home.HomeActivity"

        /** Set by the Google TV tile so Google TV home opening in the next few seconds is not redirected. */
        @Volatile
        var allowGoogleTvUntil = 0L

        fun allowGoogleTvOnce() {
            allowGoogleTvUntil = SystemClock.uptimeMillis() + 5_000L
        }
    }
}
