package com.pockethub.data.remote

// GitHub Actions: workflow runs, check runs, jobs, artifacts endpoints.
// Split out of GitHubApi.kt; inherited by GitHubApi so Retrofit and
// call sites keep resolving everything through GitHubApi.X.

import com.pockethub.data.model.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ActionEndpoints {

    /** GitHub Actions workflow runs for a repo. */
    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun getWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
        @Query("branch") branch: String? = null,
    ): WorkflowRunsResponse

    /**
     * List check runs for a given commit ref — the canonical source for "PR checks"
     * (the PR header on GitHub web shows exactly this aggregate). Includes GitHub
     * Actions plus all third-party CI apps.
     */
    @GET("repos/{owner}/{repo}/commits/{ref}/check-runs")
    suspend fun listCheckRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("filter") filter: String = "latest",
    ): CheckRunsResponse

    /**
     * List workflows (definitions) for a repo.
     * [ref] is optional — omit it to use the repo's default branch, or pass a
     * specific branch name to list workflows as defined on that branch.
     */
    @GET("repos/{owner}/{repo}/actions/workflows")
    suspend fun getWorkflows(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("ref") ref: String? = null,
    ): WorkflowsResponse

    /** Trigger a `workflow_dispatch` event for a single workflow. */
    @POST("repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches")
    suspend fun dispatchWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: Long,
        @Body body: WorkflowDispatchRequest,
    ): retrofit2.Response<Unit>

    /**
     * List build artifacts produced by a workflow run. Covers everything a
     * workflow uploaded via `actions/upload-artifact` regardless of format —
     * GitHub stores each artifact as a single zip (download endpoint returns
     * the zip). Expired artifacts (default 90-day retention) still appear in
     * the list but their download URL 404s.
     */
    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/artifacts")
    suspend fun getWorkflowRunArtifacts(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
    ): ArtifactsResponse

    /** List jobs for a specific workflow run. */
    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/jobs")
    suspend fun getWorkflowRunJobs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("filter") filter: String = "latest",
    ): WorkflowJobsResponse

    /**
     * Per-job logs endpoint. GitHub responds with HTTP 302 to a signed
     * objects.githubusercontent.com URL (zip). The retrofit call therefore must
     * use [retrofit2.Response] to surface the Location header for callers that
     * want to follow it themselves, or for callers that just want a 302 sentinel.
     */
    @GET("repos/{owner}/{repo}/actions/jobs/{job_id}/logs")
    suspend fun getWorkflowJobLogsUrl(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("job_id") jobId: Long,
    ): retrofit2.Response<Unit>

    @PUT("repos/{owner}/{repo}/actions/runs/{run_id}/cancel")
    suspend fun cancelWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long,
    ): retrofit2.Response<Unit>

    @POST("repos/{owner}/{repo}/actions/runs/{run_id}/rerun")
    suspend fun rerunWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long,
    ): retrofit2.Response<Unit>

    @kotlinx.serialization.Serializable
    data class CheckRunsResponse(
        @kotlinx.serialization.SerialName("total_count") val totalCount: Int = 0,
        val runs: List<CheckRun> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class CheckRun(
        val id: Long = 0,
        val name: String = "",
        val status: String? = null,              // queued | in_progress | completed
        val conclusion: String? = null,          // success | failure | neutral | cancelled | skipped | timed_out | action_required | stale
        @kotlinx.serialization.SerialName("started_at") val startedAt: String? = null,
        @kotlinx.serialization.SerialName("completed_at") val completedAt: String? = null,
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        @kotlinx.serialization.SerialName("details_url") val detailsUrl: String? = null,
        val app: CheckApp? = null,
    )

    @kotlinx.serialization.Serializable
    data class CheckApp(
        val name: String = "",
        @kotlinx.serialization.SerialName("slug") val slug: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class WorkflowDispatchRequest(
        /** GitHubApi.Branch or tag name the workflow should run on. */
        val ref: String,
    )

    @kotlinx.serialization.Serializable
    data class WorkflowsResponse(
        @kotlinx.serialization.SerialName("total_count") val totalCount: Int = 0,
        @kotlinx.serialization.SerialName("workflows") val workflows: List<Workflow> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class Workflow(
        val id: Long = 0,
        @kotlinx.serialization.SerialName("node_id") val nodeId: String? = null,
        val name: String = "",
        val path: String = "",
        val state: String = "",
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
        @kotlinx.serialization.SerialName("updated_at") val updatedAt: String? = null,
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        @kotlinx.serialization.SerialName("badge_url") val badgeUrl: String? = null,
        @kotlinx.serialization.SerialName("deleted_at") val deletedAt: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class WorkflowRunsResponse(
        @kotlinx.serialization.SerialName("total_count") val totalCount: Int = 0,
        @kotlinx.serialization.SerialName("workflow_runs") val runs: List<WorkflowRun> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class WorkflowRun(
        val id: Long = 0,
        @kotlinx.serialization.SerialName("node_id") val nodeId: String? = null,
        val name: String = "",
        @kotlinx.serialization.SerialName("head_branch") val headBranch: String? = null,
        @kotlinx.serialization.SerialName("head_sha") val headSha: String? = null,
        val path: String? = null,
        @kotlinx.serialization.SerialName("run_number") val runNumber: Int = 0,
        val event: String? = null,
        val status: String? = null,
        val conclusion: String? = null,
        @kotlinx.serialization.SerialName("workflow_id") val workflowId: Long? = null,
        val url: String? = null,
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
        @kotlinx.serialization.SerialName("updated_at") val updatedAt: String? = null,
        @kotlinx.serialization.SerialName("run_started_at") val runStartedAt: String? = null,
        val actor: User? = null,
        @kotlinx.serialization.SerialName("head_commit")
        val headCommit: HeadCommit? = null,
    )

    @kotlinx.serialization.Serializable
    data class HeadCommit(
        val message: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class ArtifactsResponse(
        @kotlinx.serialization.SerialName("total_count") val totalCount: Int = 0,
        val artifacts: List<Artifact> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class Artifact(
        val id: Long = 0,
        val name: String = "",
        @kotlinx.serialization.SerialName("size_in_bytes") val sizeInBytes: Long = 0,
        @kotlinx.serialization.SerialName("archive_download_url") val archiveDownloadUrl: String = "",
        val expired: Boolean = false,
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
        @kotlinx.serialization.SerialName("expires_at") val expiresAt: String? = null,
        @kotlinx.serialization.SerialName("workflow_run") val workflowRun: ArtifactWorkflowRun? = null,
    )

    @kotlinx.serialization.Serializable
    data class ArtifactWorkflowRun(
        val id: Long? = null,
        @kotlinx.serialization.SerialName("head_branch") val headBranch: String? = null,
        @kotlinx.serialization.SerialName("head_sha") val headSha: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class WorkflowJobsResponse(
        @kotlinx.serialization.SerialName("total_count") val totalCount: Int = 0,
        val jobs: List<WorkflowJob> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class WorkflowJob(
        val id: Long = 0,
        @kotlinx.serialization.SerialName("run_id") val runId: Long = 0,
        @kotlinx.serialization.SerialName("node_id") val nodeId: String? = null,
        @kotlinx.serialization.SerialName("head_sha") val headSha: String? = null,
        val status: String? = null,              // queued | in_progress | completed
        val conclusion: String? = null,          // success | failure | cancelled | skipped | neutral
        val name: String = "",
        val steps: List<WorkflowStep> = emptyList(),
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        @kotlinx.serialization.SerialName("started_at") val startedAt: String? = null,
        @kotlinx.serialization.SerialName("completed_at") val completedAt: String? = null,
        val runnerName: String? = null,
        @kotlinx.serialization.SerialName("runner_group_id") val runnerGroupId: Long? = null,
    )

    @kotlinx.serialization.Serializable
    data class WorkflowStep(
        val name: String = "",
        val status: String = "",                // queued | in_progress | completed
        val conclusion: String? = null,         // success | failure | cancelled | skipped
        val number: Int = 0,
        @kotlinx.serialization.SerialName("started_at") val startedAt: String? = null,
        @kotlinx.serialization.SerialName("completed_at") val completedAt: String? = null,
    )
}
