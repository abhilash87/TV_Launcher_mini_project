package dev.homescreen.test

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Minimal test launcher. Shows a full-screen background image with a fixed, ordered row of
 * glass tiles (see TILE_ORDER), plus a trailing "Set as Home…" tile while it is not the default.
 * Nothing here changes the default Home app on its own: the "Set as Home…" tile only opens
 * Android's own consent dialog, and the user decides there.
 */
class HomeActivity : Activity() {

    private data class AppEntry(
        val label: String,
        val art: Drawable?,
        val launch: Intent,
    )

    private lateinit var row: LinearLayout
    private lateinit var status: TextView
    private lateinit var wifiButton: View
    private lateinit var settingsButton: View
    private lateinit var wifiSignal: WifiSignalView
    private val networkHandler = Handler(Looper.getMainLooper())
    private val networkRefresh = object : Runnable {
        override fun run() {
            updateNetworkStatus()
            networkHandler.postDelayed(this, 5_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        row = findViewById(R.id.row)
        // Jump straight to the focused tile instead of smooth-scrolling the row.
        (row.parent as HorizontalScrollView).isSmoothScrollingEnabled = false
        status = findViewById(R.id.status)
        wifiButton = findViewById(R.id.wifi_button)
        settingsButton = findViewById(R.id.settings_button)
        wifiSignal = findViewById(R.id.wifi_signal)
        wifiButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            startSafely(if (intent.resolveActivity(packageManager) != null) intent else Intent(Settings.ACTION_SETTINGS), getString(R.string.wifi_settings))
        }
        settingsButton.setOnClickListener {
            startSafely(Intent(Settings.ACTION_SETTINGS), getString(R.string.tv_settings))
        }
    }

    override fun onResume() {
        super.onResume()
        // Refreshing on every resume keeps the row current after installs/uninstalls
        // without registering any broadcast receivers.
        updateStatus()
        buildTiles()
        networkHandler.removeCallbacks(networkRefresh)
        networkHandler.post(networkRefresh)
    }

    override fun onPause() {
        networkHandler.removeCallbacks(networkRefresh)
        super.onPause()
    }

    @Suppress("DEPRECATION")
    private fun updateNetworkStatus() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val active = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val ethernet = active?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        // A VPN may be the default network while Wi-Fi remains the underlying link.
        val wifi = if (ethernet) null else if (active?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) active
            else cm?.allNetworks?.mapNotNull { cm.getNetworkCapabilities(it) }
                ?.firstOrNull { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
        val connected = wifi != null
        val info = wifi?.transportInfo as? WifiInfo
            ?: if (connected) runCatching { applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo }.getOrNull() else null
        val rssi = info?.rssi
        val level = if (connected && rssi != null && rssi in -126..-1) WifiManager.calculateSignalLevel(rssi, 4) + 1 else 0
        wifiSignal.update(connected, ethernet, level)
        wifiButton.contentDescription = when {
            ethernet -> getString(R.string.ethernet_connected)
            connected && level > 0 -> getString(R.string.wifi_strength, level)
            connected -> getString(R.string.wifi_connected)
            else -> getString(R.string.wifi_disconnected)
        }
    }

    private fun roleManager(): RoleManager? = getSystemService(RoleManager::class.java)

    private fun isHome(): Boolean = roleManager()?.isRoleHeld(RoleManager.ROLE_HOME) == true

    private fun updateStatus() {
        // Preserve the header spacing while hiding setup text after activation.
        // Setup guidance is only needed until this launcher becomes the Home app.
        if (isHome()) {
            status.text = ""
            status.visibility = View.INVISIBLE
        } else {
            status.visibility = View.VISIBLE
            status.setText(R.string.status_not_home)
        }
    }
    /** Resolves one pinned package to a tile, or null if it is not installed. */
    private fun appEntry(pkg: String): AppEntry? {
        val pm = packageManager
        val launch = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
            ?: return null
        val ai = pm.resolveActivity(launch, 0)?.activityInfo ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return AppEntry(ai.loadLabel(pm).toString(), ai.loadIcon(pm), launch)
    }

    private fun buildTiles() {
        row.removeAllViews()

        for (pkg in TILE_ORDER) {
            appEntry(pkg)?.let { app ->
                addTile(app.label, app.art) { startSafely(app.launch, app.label) }
            }
        }
        // Only needed until this app is the default Home; disappears afterwards.
        if (!isHome()) {
            addTile(getString(R.string.tile_set_home), getDrawable(R.drawable.banner), fillDisc = false) { requestHomeRole() }
        }

        // Focus the first installed app, preferring YouTube.
        val firstTile = row.getChildAt(0)
        if (firstTile != null) {
            wifiButton.nextFocusDownId = firstTile.id
            settingsButton.nextFocusDownId = firstTile.id
            firstTile.requestFocus()
        } else {
            wifiButton.requestFocus()
        }
    }

    /**
     * A round glass disc with the art centred inside and the label underneath. App icons fill the
     * disc (clipped to a circle, leaving a thin glass rim); the setup glyph sits inset inside it.
     */
    private fun addTile(label: String, art: Drawable?, fillDisc: Boolean = true, onClick: () -> Unit): View {
        val disc = dp(96)
        val artSize = if (fillDisc) disc - dp(12) else disc - dp(36)
        val tile = LinearLayout(this).apply {
            id = View.generateViewId()
            nextFocusUpId = R.id.wifi_button
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = true
            isClickable = true
            setOnClickListener { onClick() }
            // Focus only brightens the glass disc; nothing scales, moves or fades.
            stateListAnimator = null
        }
        val discView = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.tile_bg)
            isDuplicateParentStateEnabled = true
        }
        discView.addView(ImageView(this).apply {
            setImageDrawable(art)
            scaleType = if (fillDisc) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            clipToOutline = true
        }, FrameLayout.LayoutParams(artSize, artSize, Gravity.CENTER))
        tile.addView(discView, LinearLayout.LayoutParams(disc, disc))
        tile.addView(TextView(this).apply {
            text = label
            setTextColor(getColor(R.color.text))
            setShadowLayer(4f, 0f, 1f, 0xCC000000.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(tile, LinearLayout.LayoutParams(dp(116), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(4), dp(4), dp(4), dp(4))
        })
        return tile
    }

    private fun startSafely(intent: Intent, label: String) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.launch_failed, label), Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, getString(R.string.launch_failed, label), Toast.LENGTH_SHORT).show()
        }
    }

    /** Opens Android's own Home-app consent dialog. The user accepts or declines on the TV. */
    private fun requestHomeRole() {
        val rm = roleManager()
        if (rm == null || !rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
            Toast.makeText(this, R.string.role_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        @Suppress("DEPRECATION")
        startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQUEST_HOME_ROLE)
    }

    @Deprecated("Framework Activity API; fine for this dependency-free test app.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_HOME_ROLE) updateStatus()
    }

    @Deprecated("Framework Activity API; fine for this dependency-free test app.")
    override fun onBackPressed() {
        // A Home screen must not close itself on Back; as a normal app, Back exits as usual.
        if (!isHome()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQUEST_HOME_ROLE = 1

        /** Row order, left to right. Package names are pinned apps; missing apps are skipped. */
        val TILE_ORDER = listOf(
            "com.google.android.youtube.tv", // YouTube
            "com.netflix.ninja", // Netflix
            "ar.tvplayer.tv", // TiviMate
        )
    }
}
