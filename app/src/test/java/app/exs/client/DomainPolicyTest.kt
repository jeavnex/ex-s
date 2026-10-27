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
        assertTrue(DomainPolicy.isBlockedResource("https://vast.strm-3705.trvid1.cc/api/vast"))
        assertTrue(DomainPolicy.isBlockedResource("https://v1.traffer.net/video/16/16652.mp4"))
        assertTrue(DomainPolicy.isBlockedResource("https://hg-delivery.b-cdn.net/57495/EVOKING_DE.MP4"))
        assertFalse(DomainPolicy.isBlockedResource("https://ex-fs.net/app.js"))
    }
}
