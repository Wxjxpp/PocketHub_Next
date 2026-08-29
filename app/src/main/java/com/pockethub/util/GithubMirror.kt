package com.pockethub.util

// GitHub download accelerator (net branch experiment).
//
// Users on censored networks point the app at a "gh-proxy"-style accelerator
// (e.g. https://gh-proxy.com/). These proxies serve the ORIGINAL url appended
// after their base, e.g.:
//   https://gh-proxy.com/https://github.com/owner/repo/releases/download/v1/x.apk
// We only ever rewrite hosts that the proxy family actually mirrors — GitHub
// file hosts — never api.github.com (which usually works and must keep the
// auth header out of third-party hands).

/** Hosts whose FILE downloads may be routed through the user's accelerator. */
private val MIRRORABLE_HOSTS = setOf(
    "github.com",
    "www.github.com",
    "raw.githubusercontent.com",
    "codeload.github.com",
    "objects.githubusercontent.com",
    "gist.githubusercontent.com",
    "github-cloud.s3.amazonaws.com",
)

/** Whether [url] points at a GitHub file host eligible for acceleration. */
fun isMirrorableGithubUrl(url: String): Boolean {
    val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: return false
    return host in MIRRORABLE_HOSTS
}

/**
 * Apply the user's accelerator prefix to a GitHub file URL. Returns [url]
 * unchanged when the prefix is blank or the host isn't a mirrorable GitHub
 * file host.
 */
fun applyMirrorPrefix(url: String, prefix: String): String {
    val p = prefix.trim()
    if (p.isEmpty() || !p.startsWith("http")) return url
    if (!isMirrorableGithubUrl(url)) return url
    val base = if (p.endsWith("/")) p else "$p/"
    return base + url
}
