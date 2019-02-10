package com.doist.jobschedulercompat.scheduler;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.JobScheduler;
import com.doist.jobschedulercompat.job.JobStatus;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Abstraction to schedule and cancel jobs, as well as handle key parts of a job's lifecycle.
 *
 * Implementations should delegate as much as possible to whichever API they use (eg. JobScheduler).
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public abstract class Scheduler {
    protected static final int RESULT_SUCCESS = JobScheduler.RESULT_SUCCESS;
    protected static final int RESULT_FAILURE = JobScheduler.RESULT_FAILURE;

    protected final Context context;

    protected Scheduler(Context context) {
        this.context = context;
    }

    /**
     * Schedules this job. It has already been stored.
     */
    public abstract int schedule(JobInfo job);

    /**
     * Cancels this job. It has already been removed.
     */
    public abstract void cancel(int jobId);

    /**
     * Cancels all jobs. They've all been removed.
     */
    public abstract void cancelAll();

    /**
     * Signals that a job has completed and been rescheduled, if needed.
     */
    public void onJobCompleted(int jobId, boolean needsReschedule) {
        // Implementations can override.
    }

    /**
     * Signals that a job failed and is being rescheduled.
     */
    public void onJobRescheduled(JobStatus newJob, JobStatus failedJob) {
        // Implementations can override.
    }

    /**
     * Returns this scheduler's unique tag.
     */
    @NonNull
    public abstract String getTag();
}
