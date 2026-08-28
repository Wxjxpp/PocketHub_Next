package com.pockethub.data.download

import android.content.Context
import com.pockethub.util.userMessage
import android.os.Environment
import com.pockethub.data.local.DownloadDao
import com.pockethub.data.local.DownloadEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val client: OkHttpClient,
    private val dao: DownloadDao,
) {

    data class EnqueueRequest(
        val url: String,
        val fileName: String,
        val contentType: String,
        val sizeBytes: Long,
        val repoKey: String,
        val releaseTag: String = "",
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueSignals = Channel<Unit>(Channel.CONFLATED)
    private val cancelledUrls = ConcurrentHashMap.newKeySet<String>()

    /**
     * Bare client used for redirect hops. Built from scratch because OkHttp
     * cannot REMOVE inherited interceptors via newBuilder() — the shared
     * [client] carries [com.pockethub.data.remote.AuthInterceptor], which would
     * attach the GitHub Bearer token to the redirect target (Azure Blob / CDN),
     * and those hosts reject foreign tokens with 401.
     */
    private val redirectClient = OkHttpClient.Builder()
        .followRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile private var currentJob: Job? = null
    @Volatile private var currentUrl: String? = null
    @Volatile private var currentCall: Call? = null

    init {
        scope.launch {
            for (ignored in queueSignals) drainQueue()
        }
        queueSignals.trySend(Unit)
    }

    fun allFlow(): Flow<List<DownloadEntity>> = dao.allFlow()
    fun activeFlow(): Flow<List<DownloadEntity>> =
        dao.flowByStates(listOf("QUEUED", "IN_PROGRESS", "FAILED"))
    fun doneFlow(): Flow<List<DownloadEntity>> = dao.flowByState("DONE")

    suspend fun get(url: String): DownloadEntity? = dao.byUrl(url)

    suspend fun enqueue(req: EnqueueRequest) {
        cancelledUrls.remove(req.url)
        val dir = File(workRoot(), req.repoKey.ifBlank { "common" })
        val destFile = File(dir, req.fileName)
        val existing = dao.byUrl(req.url)
        if (existing?.status == "IN_PROGRESS" || existing?.status == "QUEUED") return
        if (existing?.status == "DONE" && destFile.exists()) return

        val now = System.currentTimeMillis()
        dao.upsert(
            DownloadEntity(
                url = req.url,
                fileName = req.fileName,
                contentType = req.contentType,
                repoKey = req.repoKey,
                releaseTag = req.releaseTag,
                sizeBytes = req.sizeBytes,
                localPath = destFile.absolutePath,
                status = "QUEUED",
                createdAt = now,
                updatedAt = now,
            )
        )
        runNextIfIdle()
    }

    suspend fun retry(url: String) {
        cancelledUrls.remove(url)
        val existing = dao.byUrl(url) ?: return
        destFileOrNull(existing)?.delete()
        dao.upsert(
            existing.copy(
                status = "QUEUED",
                downloadedBytes = 0,
                progressPct = 0,
                errorMsg = "",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
        )
        runNextIfIdle()
    }

    suspend fun cancel(url: String) {
        val existing = dao.byUrl(url) ?: return
        if (currentUrl == url) {
            cancelledUrls += url
            currentCall?.cancel()
            currentJob?.cancel()
        }
        destFileOrNull(existing)?.delete()
        dao.deleteByUrl(url)
        runNextIfIdle()
    }

    suspend fun removeCompleted(url: String) {
        val existing = dao.byUrl(url) ?: return
        destFileOrNull(existing)?.delete()
        dao.deleteByUrl(url)
    }

    private fun destFileOrNull(entity: DownloadEntity): File? =
        entity.localPath.takeIf { it.isNotBlank() }?.let { File(it) }

    /** Directory where downloads for [repoKey] are stored. */
    fun dirFor(repoKey: String): File = File(workRoot(), repoKey.ifBlank { "common" })

    private fun workRoot(): File {
        val root = File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir,
            "PocketHub",
        )
        if (!root.exists()) root.mkdirs()
        return root
    }

    private fun runNextIfIdle() {
        queueSignals.trySend(Unit)
    }

    private suspend fun drainQueue() {
        while (true) {
            val queued = dao.flowByStates(listOf("QUEUED", "IN_PROGRESS", "FAILED")).first()
                .firstOrNull { it.status == "QUEUED" }
                ?: return
            executeDownload(queued)
        }
    }

    /**
     * Opens [url] following redirects manually:
     *
     * - The initial request goes through the shared [client] (in a
     *   no-redirect derived copy), so [com.pockethub.data.remote.AuthInterceptor]
     *   attaches the Bearer token — required because GitHub's artifact download
     *   URLs (`archive_download_url`) only authenticate on the API host
     *   (mandatory for private repos; public ones tolerate it).
     * - GitHub answers with 302 → signed Azure Blob / CDN URL whose query string
     *   already carries the authorization (SAS token). Those redirect targets
     *   REJECT a foreign `Authorization` header with 401, and OkHttp would
     *   otherwise copy the header across hosts — so each hop is issued via the
     *   bare [redirectClient] with no auth header attached.
     *
     * Returns the final call (usable for cancellation) and its response.
     */
    private fun openDownload(url: String): Pair<Call, Response> {
        var call = client.newBuilder().followRedirects(false).build()
            .newCall(Request.Builder().url(url).build())
        currentCall = call
        var response = call.execute()
        var hops = 0
        while (response.isRedirect && hops < 5) {
            val nextUrl = response.header("Location")?.let { response.request.url.resolve(it) }
            response.close()
            if (nextUrl == null) throw IOException("Redirect missing Location header")
            call = redirectClient.newCall(Request.Builder().url(nextUrl).build())
            currentCall = call
            response = call.execute()
            hops++
        }
        return call to response
    }

    private suspend fun executeDownload(entity: DownloadEntity) {
        val targetFile = File(entity.localPath)
        targetFile.parentFile?.mkdirs()
        val url = entity.url
        val destFile = File(targetFile.parentFile, "${targetFile.name}.part")
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val (call, response) = openDownload(url)
                currentCall = call
                response.use {
                    if (!it.isSuccessful) {
                        dao.upsert(entity.copy(status = "FAILED", errorMsg = "HTTP ${it.code}", updatedAt = System.currentTimeMillis()))
                        return@launch
                    }

                    val totalBytes = it.body?.contentLength()?.takeIf { size -> size > 0 } ?: entity.sizeBytes
                    val body = it.body ?: throw IOException("No body in response")
                    dao.upsert(entity.copy(status = "IN_PROGRESS", sizeBytes = totalBytes, updatedAt = System.currentTimeMillis()))

                    body.byteStream().use { input ->
                        destFile.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var totalRead = 0L
                            var lastReported = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                totalRead += read
                                if (totalRead - lastReported >= 100 * 1024) {
                                    val progress = if (totalBytes > 0) ((totalRead * 100) / totalBytes).toInt() else 0
                                    dao.upsert(entity.copy(status = "IN_PROGRESS", downloadedBytes = totalRead, progressPct = progress.coerceIn(0, 100), updatedAt = System.currentTimeMillis()))
                                    lastReported = totalRead
                                }
                            }
                        }
                    }

                    if (targetFile.exists()) targetFile.delete()
                    if (!destFile.renameTo(targetFile)) {
                        destFile.copyTo(targetFile, overwrite = true)
                        destFile.delete()
                    }
                    dao.upsert(entity.copy(status = "DONE", downloadedBytes = totalBytes, progressPct = 100, updatedAt = System.currentTimeMillis()))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                destFile.delete()
                if (!cancelledUrls.remove(url)) {
                    dao.upsert(entity.copy(status = "FAILED", errorMsg = "Cancelled", updatedAt = System.currentTimeMillis()))
                }
                throw e
            } catch (e: Throwable) {
                destFile.delete()
                if (!cancelledUrls.remove(url)) {
                    val message = e.userMessage(e.javaClass.simpleName)
                    dao.upsert(entity.copy(status = "FAILED", errorMsg = message, updatedAt = System.currentTimeMillis()))
                }
            }
        }
        currentUrl = url
        currentJob = job
        job.start()
        job.join()
        currentCall = null
        currentJob = null
        currentUrl = null
    }
}
