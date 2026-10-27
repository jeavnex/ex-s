package app.exs.client

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainPolicyTest {
    @Test
    fun allowsOnlyExFsForTopLevelNavigation() {
        assertTrue(DomainPolicy.isAllowedTopLevel("https://ex-fs.net/film/123"))
        assertTrue(DomainPolicy.isAllowedTopLevel("https://www.ex-fs.net/"))
        assertFalse(DomainPolicy.isAllowedTopLevel("https://ex-fs.net.example.com/"))
        assertFalse(DomainPolicy.isAllowedTopLevel("https://777ua.com.ua/register/"))
        assertFalse(DomainPolicy.isAllowedTopLevel("javascript:alert(1)"))
    }

    @Test
    fun blocksKnownAdvertisingHosts() {
        assertTrue(DomainPolicy.isBlockedResource("https://track.realanduse.com/a.js"))
        assertTrue(DomainPolicy.isBlockedResource("https://777ua.com.ua/register/"))
        assertFalse(DomainPolicy.isBlockedResource("https://distribrey.com/load-xml/?roll=pre"))
        assertFalse(DomainPolicy.isBlockedResource("https://ex-fs.net/app.js"))
    }
}
