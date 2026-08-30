package com.pockethub.ui.markdown

// GitHub in-app link router (net branch, v2).
//
// Routing is driven by the URL's own path parameters — every github.com URL
// maps to the deepest destination the app actually supports, degrading
// gracefully one level at a time:
//
//   github.com/{login}                              → user profile
//   github.com/orgs/{org}/...                       → user profile (org)
//   github.com/{owner}/{repo}                       → repo (overview)
//   github.com/{owner}/{repo}?tab=                  → repo at the given tab
//   github.com/{owner}/{repo}/issues                → repo, Issues tab
//   github.com/{owner}/{repo}/issues/new            → Create Issue screen
//   github.com/{owner}/{repo}/issues/{n}            → issue detail
//   github.com/{owner}/{repo}/pulls                 → repo, PR tab
//   github.com/{owner}/{repo}/pull/{n}              → PR detail
//   github.com/{owner}/{repo}/commits[/x]           → repo, Commits tab / commit detail
//   github.com/{owner}/{repo}/actions[/runs/{id}]   → repo, Workflows tab / run detail
//   github.com/{owner}/{repo}/releases[/tag/{t}]    → repo, Releases tab
//   github.com/{owner}/{repo}/blob/{ref}/{path}     → in-app file viewer
//   github.com/{owner}/{repo}/{some/doc/path.md}    → in-app file viewer
//   github.com/{owner}/{repo}/tree|wiki|discussions… → repo (nearest supported)
//   marketing pages, gists, other hosts             → system browser
//
// Fragments (#readme, #L12) and query strings are stripped before parsing.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler

/** Repo destinations the app can show directly. */
enum class RepoTabTarget(val wire: String) {
    CODE("code"), ISSUES("issues"), PULLS("pulls"), RELEASES("releases"),
    COMMITS("commits"), ACTIONS("actions");

    companion object {
        fun fromWire(value: String?): RepoTabTarget? =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }
    }
}

sealed class GitHubLinkTarget {
    /** Repo home, or a specific tab when the URL's section asks for one. */
    data class Repo(
        val owner: String,
        val repo: String,
        val tab: RepoTabTarget? = null,
    ) : GitHubLinkTarget()
    data class Issue(val owner: String, val repo: String, val number: Int) : GitHubLinkTarget()
    data class Pull(val owner: String, val repo: String, val number: Int) : GitHubLinkTarget()
    data class Commit(val owner: String, val repo: String, val sha: String) : GitHubLinkTarget()
    data class WorkflowRun(val owner: String, val repo: String, val runId: Long) : GitHubLinkTarget()
    data class User(val login: String) : GitHubLinkTarget()
    /** github.com/{o}/{r}/issues/new — the app's Create Issue screen. */
    data class CreateIssue(val owner: String, val repo: String) : GitHubLinkTarget()
    /** A file inside a repo — blob links and relative README doc links. */
    data class File(
        val owner: String,
        val repo: String,
        val path: String,
        val ref: String? = null,
    ) : GitHubLinkTarget()
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

/** Sections that are repo pages but have no dedicated app screen. */
private val REPO_FALLBACK_SECTIONS = setOf(
    "wiki", "discussions", "compare", "network", "stargazers", "forks",
    "watchers", "projects", "pulse", "graphs", "milestone", "milestones",
    "deploy", "purchase", "packages",
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
        section == "issues" && segments.size >= 4 && segments[3].equals("new", ignoreCase = true) ->
            GitHubLinkTarget.CreateIssue(owner, repo)
        section == "issues" ->
            GitHubLinkTarget.Repo(owner, repo, RepoTabTarget.ISSUES)
        section == "pull" && arg.toIntOrNull() != null ->
            GitHubLinkTarget.Pull(owner, repo, arg.toInt())
        section == "pulls" ->
            GitHubLinkTarget.Repo(owner, repo, RepoTabTarget.PULLS)
        (section == "commits" || section == "commit") && arg.isNotBlank() ->
            GitHubLinkTarget.Commit(owner, repo, arg)
        section == "commits" ->
            GitHubLinkTarget.Repo(owner, repo, RepoTabTarget.COMMITS)
        section == "actions" ->
            if (segments.size >= 5 && segments[3].equals("runs", ignoreCase = true)) {
                segments[4].toLongOrNull()
                    ?.let { GitHubLinkTarget.WorkflowRun(owner, repo, it) }
                    ?: GitHubLinkTarget.Repo(owner, repo, RepoTabTarget.ACTIONS)
            } else {
                GitHubLinkTarget.Repo(owner, repo, RepoTabTarget.ACTIONS)
            }
        section == "releases" || section == "tag" ->
            GitHubLinkTarget.Repo(owner, repo, RepoTabTarget.RELEASES)
        // blob/{ref}/{path} opens the exact file; a bare blob/{ref} lands on Code.
        section == "blob" ->
            if (segments.size >= 5) {
                GitHubLinkTarget.File(owner, repo, path = segments.drop(4).joinToString("/"), ref = arg.ifBlank { null })
            } else {
                GitHubLinkTarget.Repo(owner, repo, RepoTabTarget.CODE)
            }
        // Directories: the code browser can't deep-link a path yet → Code tab.
        section == "tree" ->
            GitHubLinkTarget.Repo(owner, repo, RepoTabTarget.CODE)
        section in REPO_FALLBACK_SECTIONS ->
            GitHubLinkTarget.Repo(owner, repo)
        else -> {
            // /{o}/{r}/{unrecognised/path} — if the last segment looks like a
            // file (has an extension), treat it as a repo file link. This is
            // what README-relative doc links resolve to (docs/faq.md etc.).
            val relPath = segments.drop(2).joinToString("/")
            val last = relPath.substringAfterLast('/')
            if (last.contains('.') && !last.endsWith(".")) {
                GitHubLinkTarget.File(owner, repo, path = relPath, ref = null)
            } else {
                GitHubLinkTarget.Repo(owner, repo)
            }
        }
    }
}

/**
 * Navigation callbacks the host screen can provide. Any null callback makes
 * that target class degrade one level (repo home → browser). [owner]/[repo]
 * is the screen's own context (used for same-repo workflow-run routing).
 */
data class GitHubLinkNav(
    val owner: String,
    val repo: String,
    /** [tab] is a [RepoTabTarget.wire] value or null for the repo home. */
    val onRepo: ((owner: String, repo: String, tab: String?) -> Unit)? = null,
    val onIssue: ((owner: String, repo: String, number: Int) -> Unit)? = null,
    val onPull: ((owner: String, repo: String, number: Int) -> Unit)? = null,
    val onCommit: ((owner: String, repo: String, sha: String) -> Unit)? = null,
    val onUser: ((login: String) -> Unit)? = null,
    /** Same-repo workflow runs only — cross-repo runs degrade to the repo home. */
    val onWorkflowRun: ((runId: Long) -> Unit)? = null,
    /** github.com/{o}/{r}/issues/new — null degrades to the system browser. */
    val onCreateIssue: ((owner: String, repo: String) -> Unit)? = null,
    /** Open a repo file in the in-app viewer — null degrades to the repo home. */
    val onFile: ((owner: String, repo: String, path: String, ref: String?) -> Unit)? = null,
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
                    if (go != null) go(target.owner, target.repo, target.tab?.wire) else browser()
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
                is GitHubLinkTarget.File -> {
                    val go = nav.onFile
                    if (go != null) go(target.owner, target.repo, target.path, target.ref)
                    else nav.onRepo?.invoke(target.owner, target.repo, RepoTabTarget.CODE.wire)
                        ?: browser()
                }
                is GitHubLinkTarget.WorkflowRun -> {
                    val sameRepo = target.owner.equals(nav.owner, true) && target.repo.equals(nav.repo, true)
                    when {
                        sameRepo && nav.onWorkflowRun != null -> nav.onWorkflowRun!!.invoke(target.runId)
                        nav.onRepo != null -> nav.onRepo!!.invoke(target.owner, target.repo, RepoTabTarget.ACTIONS.wire)
                        else -> browser()
                    }
                }
                GitHubLinkTarget.Unknown -> browser()
            }
        }
    }
}
