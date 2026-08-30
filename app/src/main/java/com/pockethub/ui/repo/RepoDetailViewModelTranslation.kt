package com.pockethub.ui.repo


import android.util.Base64
import androidx.lifecycle.viewModelScope
import com.pockethub.data.remote.GoogleTranslate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
// ── Translation ──────────────────────────────────────────

/** Toggle between original and translated README. Triggers translation if needed. */
internal fun RepoDetailViewModel.toggleTranslation() {
    val target = translateTarget.value ?: return
    if (_showTranslated.value) {
        // Switch back to original
        _showTranslated.update { false }
        return
    }
    // If already translated, just switch
    if (_translatedReadme.value != null) {
        _showTranslated.update { true }
        return
    }
    if (_readme.value == null) return
    viewModelScope.launch { translateTo(target) }
}

/**
 * Auto-translate the README once it loads, when a target language is enabled
 * in Settings. Guarded by a content fingerprint so it runs at most once per
 * README — a user who manually switches back to the original view is never
 * forced back until a different README loads.
 */
internal fun RepoDetailViewModel.maybeAutoTranslate() {
    val readme = _readme.value ?: return
    if (readme.isBlank()) return
    if (_autoTranslateFingerprint.value == readme) return
    viewModelScope.launch {
        val target = translateTarget.first() ?: return@launch
        _autoTranslateFingerprint.update { readme }
        translateTo(target)
    }
}

/** Translate the current README to [target] and switch the view to it. */
internal suspend fun RepoDetailViewModel.translateTo(target: String) {
    val original = _readme.value ?: return
    _isTranslating.update { true }
    try {
        val lang = if (target == "zh") "zh-CN" else "en"
        // One automatic retry: 429s from the free endpoints are usually
        // transient, and the multi-provider fallback inside
        // GoogleTranslate already spreads load across services.
        val translated = try {
            GoogleTranslate.translate(original, lang)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (first: Exception) {
            kotlinx.coroutines.delay(1500)
            GoogleTranslate.translate(original, lang)
        }
        _translatedReadme.update { translated }
        _showTranslated.update { true }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e // don't swallow real coroutine cancellation
    } catch (e: Exception) {
        issueReporter.reportError("RepoDetail", "translateReadme", e, mapOf("target" to target))
        _translateMessage.update { e.message ?: "Translation failed — check your network or try again later" }
    } finally {
        _isTranslating.update { false }
    }
}

internal fun RepoDetailViewModel.clearTranslateMessage() {
    _translateMessage.update { null }
}

internal fun RepoDetailViewModel.decodeBase64(b64: String): String {
    return try {
        val cleaned = b64.replace("\n", "")
        String(Base64.decode(cleaned, Base64.DEFAULT), Charsets.UTF_8)
    } catch (_: Exception) {
        b64
    }
}
