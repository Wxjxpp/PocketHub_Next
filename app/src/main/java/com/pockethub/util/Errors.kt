package com.pockethub.util

import retrofit2.HttpException

/**
 * 用户可读的错误消息，统一优先级：
 * GitHub API 响应体的 `message` 字段（如 "Not Found"、校验失败原因）
 * > "HTTP <code>" > 本地 localizedMessage > [fallback]。
 *
 * 背景：Retrofit 的 HttpException.localizedMessage 只有 "HTTP 404 "，
 * 会丢掉 GitHub 返回的具体原因，这里统一解析恢复。
 */
fun Throwable.userMessage(fallback: String = "Something went wrong"): String {
    if (this is HttpException) {
        val apiMsg = runCatching { response()?.errorBody()?.string() }.getOrNull()
            ?.let { body -> Regex("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(body)?.groupValues?.1 }
        if (!apiMsg.isNullOrBlank()) return apiMsg
        return "HTTP ${code()}"
    }
    return localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
}
