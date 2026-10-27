package app.exs.client

import java.net.URI

object DomainPolicy {
    private const val SITE_HOST = "ex-fs.net"

    private val blockedSuffixes = setOf(
        "777ua.com.ua",
        "realanduse.com",
        "deltarockme.com",
        "beesratyou.com",
        "tallertorn.com",
        "agl007.site",
        "onelink.me",
        "miceonme.com",
        "myangular.life",
        "counter.yadro.ru",
        "distribrey.com",
    )

    fun isAllowedTopLevel(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme !in setOf("https", "http")) return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        return host == SITE_HOST || host.endsWith(".$SITE_HOST")
    }

    fun isBlockedResource(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase()?.trimEnd('.') }.getOrNull()
            ?: return false
        return blockedSuffixes.any { host == it || host.endsWith(".$it") }
    }
}
