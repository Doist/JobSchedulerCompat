package com.doist.jobschedulercompat.scheduler;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.job.JobStatus;
import com.doist.jobschedulercompat.job.JobStore;

import android.app.job.JobScheduler;
import android.content.Context;
import android.os.SystemClock;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

/**
 * Abstraction to schedule and cancel jobs as defined by {@link JobInfo}.
 */
public abstract class Scheduler {
    /** @see JobScheduler#RESULT_FAILURE */
    public static final int RESULT_FAILURE = 0;
    /** @see JobScheduler#RESULT_SUCCESS */
    public static final int RESULT_SUCCESS = 1;

    static final int MAX_JOBS = 100;

    protected Context context;
    private JobStore jobs;

    public Scheduler(Context context, JobStore jobs) {
        this.context = context;
        this.jobs = jobs;
    }

    /* @see JobScheduler#schedule(android.app.job.JobInfo) */
    @CallSuper
    public int schedule(JobInfo job) {
        synchronized (JobStore.LOCK) {
            if (jobs.size() > MAX_JOBS) {
                throw new IllegalStateException("Apps may not schedule more than " + MAX_JOBS + " distinct jobs");
            }
            jobs.add(JobStatus.createFromJobInfo(job, getTag()));
        }
        return RESULT_SUCCESS;
    }

    /* @see JobScheduler#cancel(int) */
    @CallSuper
    public void cancel(int jobId) {
        synchronized (JobStore.LOCK) {
            jobs.remove(jobId);
        }
    }

    /* @see JobScheduler#cancelAll() */
    @CallSuper
    public void cancelAll() {
        synchronized (JobStore.LOCK) {
            jobs.clear();
        }
    }

    /**
     * Returns this scheduler's unique tag.
     */
    @NonNull
    public abstract String getTag();

    /**
     * A job finished executing. It's fetched from the store and depending on the state (ie. {@code needsReschedule} or
     * being a periodic job) it's rescheduled.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public void onJobCompleted(int jobId, boolean needsReschedule, String scheduler) {
        synchronized (JobStore.LOCK) {
            JobStatus jobStatus = jobs.getJob(jobId);
            if (jobStatus != null) {
                jobs.remove(jobId);
                if (needsReschedule) {
                    jobs.add(getRescheduleJobForFailure(jobStatus, scheduler));
                } else if (jobStatus.isPeriodic()) {
                    jobs.add(getRescheduleJobForPeriodic(jobStatus, scheduler));
                }
            }
        }
    }

    /** Similar to com.android.server.job.JobSchedulerService#getRescheduleJobForFailure(JobStatus). */
    private JobStatus getRescheduleJobForFailure(JobStatus failureToReschedule, String scheduler) {
        final long elapsedNowMillis = SystemClock.elapsedRealtime();
        final JobInfo job = failureToReschedule.getJob();
        final long initialBackoffMillis = job.getInitialBackoffMillis();
        final int backoffAttempts = failureToReschedule.getNumFailures() + 1;
        long delayMillis;
        switch (job.getBackoffPolicy()) {
            case android.app.job.JobInfo.BACKOFF_POLICY_LINEAR:
                delayMillis = initialBackoffMillis * backoffAttempts;
                break;

            case android.app.job.JobInfo.BACKOFF_POLICY_EXPONENTIAL:
            default:
                delayMillis = (long) Math.scalb(initialBackoffMillis, backoffAttempts - 1);
                break;
        }
        delayMillis = Math.min(delayMillis, JobInfo.MAX_BACKOFF_DELAY_MILLIS);

        return new JobStatus(failureToReschedule.getJob(), scheduler, backoffAttempts,
                             elapsedNowMillis + delayMillis, JobStatus.NO_LATEST_RUNTIME);
    }

    /** Similar to com.android.server.job.JobSchedulerService#getRescheduleJobForPeriodic(JobStatus). */
    private JobStatus getRescheduleJobForPeriodic(JobStatus periodicToReschedule, String scheduler) {
        final long elapsedNowMillis = SystemClock.elapsedRealtime();
        // Compute how much of the period is remaining.
        long runEarly = 0L;
        // If this periodic was rescheduled it won't have a deadline.
        if (periodicToReschedule.hasDeadlineConstraint()) {
            runEarly = Math.max(periodicToReschedule.getLatestRunTimeElapsed() - elapsedNowMillis, 0L);
        }
        final long newEarliestRunTimeElapsed = elapsedNowMillis + runEarly;
        final long period = periodicToReschedule.getJob().getIntervalMillis();
        final long newLatestRuntimeElapsed = newEarliestRunTimeElapsed + period;

        return new JobStatus(periodicToReschedule.getJob(), scheduler, 0 /* backoffAttempt */,
                             newEarliestRunTimeElapsed, newLatestRuntimeElapsed);
    }
}
