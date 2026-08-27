package com.pockethub.data.download

import android.content.Context
import android.os.Environment
import com.pockethub.data.local.DownloadDao
import com.pockethub.data.local.DownloadEntity
import com.pockethub.data.remote.AuthInterceptor
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
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val client: OkHttpClient,
    private val dao: DownloadDao,
    private val authInterceptor: AuthInterceptor,
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

    private suspend fun executeDownload(entity: DownloadEntity) {
        val targetFile = File(entity.localPath)
        targetFile.parentFile?.mkdirs()
        val url = entity.url
        val destFile = File(targetFile.parentFile, "${targetFile.name}.part")
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val request = Request.Builder().url(url).build()
                // Preserve AuthInterceptor so redirects to Azure Blob Storage
                // (GitHub artifact download URLs are 302→S3) still carry the
                // Bearer token — otherwise the blob server returns 401.
                val call = client.newBuilder()
                    .followRedirects(true)
                    .addInterceptor(authInterceptor)
                    .build()
                    .newCall(request)
                currentCall = call
                val response = call.execute()
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
                    val message = e.localizedMessage ?: e.javaClass.simpleName
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
