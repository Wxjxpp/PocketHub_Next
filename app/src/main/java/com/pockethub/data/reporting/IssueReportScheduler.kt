package com.pockethub.data.reporting

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pockethub.data.remote.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules or cancels the [IssueReportWorker] periodic dump-and-email.
 *
 * Paired with [SettingsRepository.issueReportEnabled] / [issueReportIntervalDays] /
 * [issueReportEmail]; called from [com.pockethub.ui.settings.SettingsViewModel]
 * and on app launch.
 */
@Singleton
class IssueReportScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /**
     * Reschedule according to current persisted settings.
     *
     * @param enabled whether periodic runs should be active.
     * @param days periodic interval in days (min 1 — WorkManager lowers it to its minimum of 15 min anyway).
     */
    fun schedule(enabled: Boolean, days: Int) {
        workManager.cancelUniqueWork(IssueReportWorker.WORK_NAME)
        if (!enabled || days < 1) return

        val request = PeriodicWorkRequestBuilder<IssueReportWorker>(
            days.toLong(), TimeUnit.DAYS,
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        workManager.enqueueUniquePeriodicWork(
            IssueReportWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** Convenience: read persisted state and reschedule on app startup. */
    suspend fun rescheduleFromSettings() {
        val enabled = settings.issueReportEnabled.first()
        val days = settings.issueReportIntervalDays.first()
        schedule(enabled, days)
    }
}
