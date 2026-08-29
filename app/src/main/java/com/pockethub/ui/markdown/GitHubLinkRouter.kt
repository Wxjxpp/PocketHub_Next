package com.pockethub.ui.markdown

// GitHub in-app link router (net branch).
//
// README / issue / PR / release bodies are full of github.com links. This
// parses any such URL into a structured target and maps it onto the app's
// navigation graph, falling back to the system browser only for things the
// app genuinely can't show.
//
// Routing table (verified against GitHub URL shapes in 20 real READMEs —
// see the test corpus in the commit message):
//   github.com/{login}                              → user profile
//   github.com/{login}?tab=repositories             → user profile
//   github.com/orgs/{org}/...                       → org profile
//   github.com/{owner}/{repo}                       → repo home
//   github.com/{owner}/{repo}/issues/{n}            → issue detail
//   github.com/{owner}/{repo}/pull/{n}              → PR detail
//   github.com/{owner}/{repo}/commit(s)/{sha}       → commit detail
//   github.com/{owner}/{repo}/actions/runs/{id}     → workflow run detail (same repo)
//   github.com/{owner}/{repo}/tree|blob/...         → repo home (code browser can't deep-link a path yet)
//   github.com/{owner}/{repo}/<anything else>       → repo home (releases, wiki, discussions, projects, …)
//   github.com/features|topics|about|...            → system browser (marketing pages)
//   non-github hosts, gists                         → system browser
// Fragments (#readme, #L12) and query strings are stripped before parsing.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler

sealed class GitHubLinkTarget {
    data class Repo(val owner: String, val repo: String) : GitHubLinkTarget()
    data class Issue(val owner: String, val repo: String, val number: Int) : GitHubLinkTarget()
    data class Pull(val owner: String, val repo: String, val number: Int) : GitHubLinkTarget()
    data class Commit(val owner: String, val repo: String, val sha: String) : GitHubLinkTarget()
    data class WorkflowRun(val owner: String, val repo: String, val runId: Long) : GitHubLinkTarget()
    data class User(val login: String) : GitHubLinkTarget()
    /** github.com/{o}/{r}/issues/new — the app's Create Issue screen. */
    data class CreateIssue(val owner: String, val repo: String) : GitHubLinkTarget()
    /** Nothing the app can render — open in the system browser. */
    object Unknown : GitHubLinkTarget()
}

/** First path segments that are GitHub product pages, never user logins. */
private val GITHUB_RESERVED_SEGMENTS = setOf(
    "about", "features", "pricing", "topics", "marketplace", "collections",
    "trending", "sponsors", "sponsorship", "settings", "notifications",
    "explore", "enterprise", "security", "login", "signup", "join", "site",
    "orgs", "apps", "customer-stories", "readme", "issues", "pulls", "search",
    "developer", "new", "import", "install", "updates", "git-guides",
    "account", "dashboard", "finance", "trust", "premium-support",
)

private fun urlDecode(s: String): String =
    runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

/** Parse a github.com URL into a structured in-app navigation target. */
fun parseGitHubLink(url: String): GitHubLinkTarget {
    val uri = runCatching { java.net.URI(url.trim()) }.getOrNull()
        ?: return GitHubLinkTarget.Unknown
    val host = uri.host?.lowercase() ?: return GitHubLinkTarget.Unknown
    if (host != "github.com" && host != "www.github.com") return GitHubLinkTarget.Unknown

    val segments = (uri.path ?: "").trim('/').split('/').filter { it.isNotBlank() }
    if (segments.isEmpty()) return GitHubLinkTarget.Unknown

    val s0 = urlDecode(segments[0])
    if (segments.size == 1) {
        return if (s0.lowercase() in GITHUB_RESERVED_SEGMENTS) GitHubLinkTarget.Unknown
        else GitHubLinkTarget.User(s0)
    }
    // orgs/{org}/(repositories|teams|people|…) — treat the org as a user profile.
    if (s0.equals("orgs", ignoreCase = true)) {
        return GitHubLinkTarget.User(urlDecode(segments[1]))
    }
    if (s0.lowercase() in GITHUB_RESERVED_SEGMENTS) return GitHubLinkTarget.Unknown

    val owner = s0
    var repo = urlDecode(segments[1])
    // Clone URLs in the wild: https://github.com/owner/repo.git
    if (repo.endsWith(".git")) repo = repo.removeSuffix(".git")
    if (repo.isBlank()) return GitHubLinkTarget.Unknown
    if (segments.size == 2) return GitHubLinkTarget.Repo(owner, repo)

    val section = urlDecode(segments[2]).lowercase()
    val arg = urlDecode(segments.getOrNull(3) ?: "")
    return when {
        section == "issues" && arg.toIntOrNull() != null ->
            GitHubLinkTarget.Issue(owner, repo, arg.toInt())
        // github.com/{o}/{r}/issues/new — the web's new-issue form maps to the
        // app's Create Issue screen.
        section == "issues" && segments.size >= 4 && segments[3].equals("new", ignoreCase = true) ->
            GitHubLinkTarget.CreateIssue(owner, repo)
        section == "pull" && arg.toIntOrNull() != null ->
            GitHubLinkTarget.Pull(owner, repo, arg.toInt())
        (section == "commits" || section == "commit") && arg.isNotBlank() ->
            GitHubLinkTarget.Commit(owner, repo, arg)
        section == "actions" ->
            if (segments.size >= 5 && segments[3].equals("runs", ignoreCase = true)) {
                segments[4].toLongOrNull()
                    ?.let { GitHubLinkTarget.WorkflowRun(owner, repo, it) }
                    ?: GitHubLinkTarget.Repo(owner, repo)
            } else {
                GitHubLinkTarget.Repo(owner, repo)
            }
        // tree/blob/releases/wiki/discussions/compare/network/stargazers/forks/
        // projects/pulse/graphs/security/pulls(anything else) — all live inside
        // the repo in GitHub's web UI; the app surfaces the repo home.
        else -> GitHubLinkTarget.Repo(owner, repo)
    }
}

/**
 * Navigation callbacks the host screen can provide. Any null callback makes
 * that target class degrade to the system browser instead of doing nothing.
 * [owner]/[repo] is the screen's own context (used to route same-repo
 * workflow runs to the run detail screen).
 */
data class GitHubLinkNav(
    val owner: String,
    val repo: String,
    val onRepo: ((owner: String, repo: String) -> Unit)? = null,
    val onIssue: ((owner: String, repo: String, number: Int) -> Unit)? = null,
    val onPull: ((owner: String, repo: String, number: Int) -> Unit)? = null,
    val onCommit: ((owner: String, repo: String, sha: String) -> Unit)? = null,
    val onUser: ((login: String) -> Unit)? = null,
    /** Same-repo workflow runs only — cross-repo runs degrade to the repo home. */
    val onWorkflowRun: ((runId: Long) -> Unit)? = null,
    /** github.com/{o}/{r}/issues/new — null degrades to the system browser. */
    val onCreateIssue: ((owner: String, repo: String) -> Unit)? = null,
    /** Release assets / raw files — enqueue into the in-app download manager. */
    val onDownload: ((url: String, fileName: String) -> Unit)? = null,
)

/**
 * The single link handler every markdown host should use. Returns an
 * (url, kind) -> Unit callback for [com.pockethub.ui.markdown.MarkdownText]'s
 * `onLinkClick`.
 */
@Composable
fun rememberGitHubLinkHandler(nav: GitHubLinkNav): (String, LinkKind) -> Unit {
    val uriHandler = LocalUriHandler.current
    return remember(nav) {
        link@{ url: String, kind: LinkKind ->
            when (kind) {
                LinkKind.DOWNLOADABLE -> {
                    val download = nav.onDownload
                    if (download != null) {
                        download(url, url.substringAfterLast('/').ifBlank { "download.bin" })
                    } else {
                        runCatching { uriHandler.openUri(url) }
                    }
                    return@link
                }
                // Full-res images have no in-app viewer outside the previewer
                // (which MarkdownText already hijacks before this handler runs).
                LinkKind.IMAGE_URL, LinkKind.IMAGE -> {
                    runCatching { uriHandler.openUri(url) }
                    return@link
                }
                else -> {}
            }

            fun browser() { runCatching { uriHandler.openUri(url) } }
            // NOTE: callbacks return Unit, so `?.invoke() ?: fallback` can't be
            // used for null-checking — compare the callback reference itself.
            when (val target = parseGitHubLink(url)) {
                is GitHubLinkTarget.Repo -> {
                    val go = nav.onRepo
                    if (go != null) go(target.owner, target.repo) else browser()
                }
                is GitHubLinkTarget.Issue -> {
                    val go = nav.onIssue
                    if (go != null) go(target.owner, target.repo, target.number) else browser()
                }
                is GitHubLinkTarget.Pull -> {
                    val go = nav.onPull
                    if (go != null) go(target.owner, target.repo, target.number) else browser()
                }
                is GitHubLinkTarget.Commit -> {
                    val go = nav.onCommit
                    if (go != null) go(target.owner, target.repo, target.sha) else browser()
                }
                is GitHubLinkTarget.User -> {
                    val go = nav.onUser
                    if (go != null) go(target.login) else browser()
                }
                is GitHubLinkTarget.CreateIssue -> {
                    val go = nav.onCreateIssue
                    if (go != null) go(target.owner, target.repo) else browser()
                }
                is GitHubLinkTarget.WorkflowRun -> {
                    val sameRepo = target.owner.equals(nav.owner, true) && target.repo.equals(nav.repo, true)
                    when {
                        sameRepo && nav.onWorkflowRun != null -> nav.onWorkflowRun!!.invoke(target.runId)
                        nav.onRepo != null -> nav.onRepo!!.invoke(target.owner, target.repo)
                        else -> browser()
                    }
                }
                GitHubLinkTarget.Unknown -> browser()
            }
        }
    }
}
