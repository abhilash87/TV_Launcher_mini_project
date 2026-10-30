package dev.homescreen.test

import org.junit.Assert.*
import org.junit.Test

class HomeRedirectPolicyTest {
    @Test fun firstHomeIsImmediate() {
        assertEquals(0L, HomeRedirectPolicy().request(0L))
    }

    @Test fun quickSecondPressIsQueuedInsteadOfDropped() {
        val policy = HomeRedirectPolicy()
        policy.launched(1_000L)
        assertEquals(150L, policy.request(1_100L))
    }

    @Test fun repeatedEventsDoNotPostponeTheQueuedRedirect() {
        val policy = HomeRedirectPolicy()
        policy.launched(1_000L)
        assertEquals(150L, policy.request(1_100L))
        assertNull(policy.request(1_150L))
        assertNull(policy.request(1_240L))
        policy.launched(1_250L)
        assertEquals(240L, policy.request(1_260L))
    }

    @Test fun pressAfterCooldownIsImmediate() {
        val policy = HomeRedirectPolicy()
        policy.launched(1_000L)
        assertEquals(0L, policy.request(2_000L))
    }

    @Test fun screenOffCancellationAllowsNextHomeRequest() {
        val policy = HomeRedirectPolicy()
        policy.request(1_000L)
        policy.cancel()
        assertEquals(0L, policy.request(2_000L))
    }

    @Test fun aNewBootLaunchesRegardlessOfServiceStartupDelay() {
        assertTrue(HomeRedirectPolicy.needsBootLaunch(42, 41))
        assertTrue(HomeRedirectPolicy.needsBootLaunch(42, -1))
    }

    @Test fun sameBootReconnectDoesNotInterruptPlayback() {
        assertFalse(HomeRedirectPolicy.needsBootLaunch(42, 42))
    }

    @Test fun unavailableBootCounterStillAllowsStartup() {
        assertTrue(HomeRedirectPolicy.needsBootLaunch(-1, -1))
    }
    @Test fun homeKeyWaitsForTheLastPressNotTheFirstWindowEvent() {
        val policy = HomeRedirectPolicy()
        assertEquals(350L, policy.homeKey(1_000L))
        assertEquals(350L, policy.homeKey(1_150L))
        assertEquals(350L, policy.homeKey(1_300L))
        assertNull(policy.request(1_350L))
    }

    @Test fun manyHomeBurstsAlwaysLeaveATrailingRedirect() {
        val policy = HomeRedirectPolicy()
        var time = 1_000L
        repeat(20) {
            repeat(6) {
                assertEquals(350L, policy.homeKey(time))
                time += 100L
            }
            // Redirect becomes due 350 ms after the sixth key.
            time += 250L
            policy.launched(time)
            time += 100L
        }
    }

    @Test fun windowEventsCannotShortenTheHomeKeyQuietPeriod() {
        val policy = HomeRedirectPolicy()
        policy.homeKey(1_000L)
        policy.cancel()
        assertEquals(250L, policy.request(1_100L))
    }
}
