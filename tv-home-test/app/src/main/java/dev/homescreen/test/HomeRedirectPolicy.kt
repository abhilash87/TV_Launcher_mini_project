package dev.homescreen.test

/** Coalesce bursts without discarding the final Home request. Uses monotonic time. */
internal class HomeRedirectPolicy(private val minimumIntervalMs: Long = 250L, private val homeQuietPeriodMs: Long = 350L) {
    private var lastLaunchAt: Long? = null
    private var pending = false
    private var lastHomeKeyAt: Long? = null

    fun request(now: Long): Long? {
        if (pending) return null
        pending = true
        val cooldown = lastLaunchAt?.let { (minimumIntervalMs - (now - it)).coerceAtLeast(0L) } ?: 0L
        val quietPeriod = lastHomeKeyAt?.let { (homeQuietPeriodMs - (now - it)).coerceAtLeast(0L) } ?: 0L
        return maxOf(cooldown, quietPeriod)
    }

    /** Every new key moves the trailing redirect to after the final press. */
    fun homeKey(now: Long): Long {
        lastHomeKeyAt = now
        pending = false
        return request(now)!!
    }

    fun launched(now: Long) {
        lastLaunchAt = now
        pending = false
    }

    fun cancel() { pending = false }

    companion object {
        fun needsBootLaunch(bootCount: Int, handledBootCount: Int): Boolean =
            bootCount < 0 || bootCount != handledBootCount
    }
}
