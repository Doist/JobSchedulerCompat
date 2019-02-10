package com.doist.jobschedulercompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import com.doist.jobschedulercompat.job.JobStatus;
import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.scheduler.Scheduler;
import com.doist.jobschedulercompat.scheduler.alarm.AlarmScheduler;
import com.doist.jobschedulercompat.scheduler.gcm.GcmScheduler;
import com.doist.jobschedulercompat.scheduler.jobscheduler.JobSchedulerSchedulerV21;
import com.doist.jobschedulercompat.scheduler.jobscheduler.JobSchedulerSchedulerV24;
import com.doist.jobschedulercompat.scheduler.jobscheduler.JobSchedulerSchedulerV26;
import com.doist.jobschedulercompat.scheduler.jobscheduler.JobSchedulerSchedulerV28;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/** @see android.app.job.JobScheduler */
public class JobScheduler {
    /** @see android.app.job.JobScheduler#RESULT_SUCCESS */
    public static final int RESULT_FAILURE = 0;
    /** @see android.app.job.JobScheduler#RESULT_FAILURE */
    public static final int RESULT_SUCCESS = 1;

    static final int MAX_JOBS = 100;

    final Map<String, Scheduler> schedulers = new HashMap<>();

    @SuppressLint("StaticFieldLeak")
    private static JobScheduler instance;

    public static synchronized JobScheduler get(Context context) {
        if (instance == null) {
            instance = new JobScheduler(context);
        }
        return instance;
    }

    private final Context context;
    private final JobStore jobStore;

    private JobScheduler(Context context) {
        this.context = context.getApplicationContext();
        this.jobStore = JobStore.get(context);
    }

    /** @see android.app.job.JobScheduler#schedule(android.app.job.JobInfo) */
    public int schedule(JobInfo job) {
        synchronized (JobStore.LOCK) {
            if (jobStore.size() > MAX_JOBS) {
                throw new IllegalStateException("Apps may not schedule more than " + MAX_JOBS + " distinct jobs");
            }
            Scheduler scheduler = getSchedulerForJob(context, job);
            jobStore.add(JobStatus.createFromJobInfo(job, scheduler.getTag()));
            return scheduler.schedule(job);
        }
    }

    /** @see android.app.job.JobScheduler#cancel(int) */
    public void cancel(int jobId) {
        synchronized (JobStore.LOCK) {
            JobStatus jobStatus = jobStore.getJob(jobId);
            if (jobStatus != null) {
                jobStore.remove(jobId);
                getSchedulerForTag(context, jobStatus.getSchedulerTag()).cancel(jobId);
            }
        }
    }

    /** @see android.app.job.JobScheduler#cancelAll() */
    public void cancelAll() {
        synchronized (JobStore.LOCK) {
            Set<String> tags = new HashSet<>();
            for (JobStatus jobStatus : jobStore.getJobs()) {
                tags.add(jobStatus.getSchedulerTag());
            }
            jobStore.clear();
            for (String tag : tags) {
                getSchedulerForTag(context, tag).cancelAll();
            }
        }
    }

    /** @see android.app.job.JobScheduler#getAllPendingJobs() */
    @NonNull
    public List<JobInfo> getAllPendingJobs() {
        synchronized (JobStore.LOCK) {
            List<JobStatus> jobStatuses = jobStore.getJobs();
            List<JobInfo> result = new ArrayList<>(jobStatuses.size());
            for (JobStatus jobStatus : jobStatuses) {
                result.add(jobStatus.getJob());
            }
            return result;
        }
    }

    /** @see android.app.job.JobScheduler#getPendingJob(int) */
    @Nullable
    public JobInfo getPendingJob(int jobId) {
        synchronized (JobStore.LOCK) {
            JobStatus jobStatus = jobStore.getJob(jobId);
            return jobStatus != null ? jobStatus.getJob() : null;
        }
    }

    /**
     * Notify the scheduler that a job finished executing.
     *
     * Handle scheduler changes by cancelling it in the old scheduler and scheduling it in the new scheduler.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public void onJobCompleted(int jobId, boolean needsReschedule) {
        synchronized (JobStore.LOCK) {
            JobStatus jobStatus = jobStore.getJob(jobId);
            if (jobStatus != null) {
                jobStore.remove(jobId);
                if (needsReschedule) {
                    jobStore.add(getRescheduleJobForFailure(jobStatus));
                } else if (jobStatus.isPeriodic()) {
                    jobStore.add(getRescheduleJobForPeriodic(jobStatus));
                }
                getSchedulerForTag(context, jobStatus.getSchedulerTag()).onJobCompleted(jobId, needsReschedule);
            }
        }
    }

    /** Similar to com.android.server.job.JobSchedulerService#getRescheduleJobForFailureLocked(JobStatus). */
    private JobStatus getRescheduleJobForFailure(JobStatus failureToReschedule) {
        final long elapsedNowMillis = SystemClock.elapsedRealtime();
        final JobInfo job = failureToReschedule.getJob();
        final long initialBackoffMillis = job.getInitialBackoffMillis();
        final int backoffAttempts = failureToReschedule.getNumFailures() + 1;
        long delayMillis;
        switch (job.getBackoffPolicy()) {
            case JobInfo.BACKOFF_POLICY_LINEAR:
                delayMillis = initialBackoffMillis * backoffAttempts;
                break;

            case JobInfo.BACKOFF_POLICY_EXPONENTIAL:
            default:
                delayMillis = (long) Math.scalb(initialBackoffMillis, backoffAttempts - 1);
                break;
        }
        delayMillis = Math.min(delayMillis, JobInfo.MAX_BACKOFF_DELAY_MILLIS);

        JobStatus newJob = new JobStatus(
                failureToReschedule.getJob(), failureToReschedule.getSchedulerTag(), backoffAttempts,
                elapsedNowMillis + delayMillis, JobStatus.NO_LATEST_RUNTIME);

        getSchedulerForTag(context, newJob.getSchedulerTag()).onJobRescheduled(newJob, failureToReschedule);

        return newJob;
    }

    /** Similar to com.android.server.job.JobSchedulerService#getRescheduleJobForPeriodic(JobStatus). */
    private JobStatus getRescheduleJobForPeriodic(JobStatus periodicToReschedule) {
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

        return new JobStatus(
                periodicToReschedule.getJob(), periodicToReschedule.getSchedulerTag(), 0 /* backoffAttempt */,
                newEarliestRunTimeElapsed, newLatestRuntimeElapsed);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public List<JobStatus> getJobsByScheduler(String scheduler) {
        synchronized (JobStore.LOCK) {
            return jobStore.getJobsByScheduler(scheduler);
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public JobStatus getJob(int jobId) {
        synchronized (JobStore.LOCK) {
            return jobStore.getJob(jobId);
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public void addJob(JobStatus jobStatus) {
        synchronized (JobStore.LOCK) {
            jobStore.add(jobStatus);
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public void removeJob(int jobId) {
        synchronized (JobStore.LOCK) {
            jobStore.remove(jobId);
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    Scheduler getSchedulerForJob(Context context, JobInfo job) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return getSchedulerForTag(context, JobSchedulerSchedulerV28.TAG);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return getSchedulerForTag(context, JobSchedulerSchedulerV26.TAG);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && job.getNetworkType() != JobInfo.NETWORK_TYPE_CELLULAR
                && !job.isRequireBatteryNotLow()
                && !job.isRequireStorageNotLow()) {
            return getSchedulerForTag(context, JobSchedulerSchedulerV24.TAG);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && (!job.isPeriodic() || job.getFlexMillis() >= job.getIntervalMillis())
                && job.getNetworkType() != JobInfo.NETWORK_TYPE_NOT_ROAMING
                && job.getNetworkType() != JobInfo.NETWORK_TYPE_CELLULAR
                && job.getTriggerContentUris() == null
                && !job.isRequireBatteryNotLow()
                && !job.isRequireStorageNotLow()) {
            return getSchedulerForTag(context, JobSchedulerSchedulerV21.TAG);
        }

        boolean gcmAvailable;
        try {
            gcmAvailable = Class.forName("com.google.android.gms.gcm.GcmNetworkManager") != null
                    && GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
                    == ConnectionResult.SUCCESS;
        } catch (Throwable ignored) {
            gcmAvailable = false;
        }
        if (gcmAvailable
                && job.getNetworkType() != JobInfo.NETWORK_TYPE_NOT_ROAMING
                && job.getNetworkType() != JobInfo.NETWORK_TYPE_CELLULAR
                && !job.isRequireBatteryNotLow()
                && !job.isRequireStorageNotLow()) {
            return getSchedulerForTag(context, GcmScheduler.TAG);
        }

        return getSchedulerForTag(context, AlarmScheduler.TAG);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    Scheduler getSchedulerForTag(Context context, String tag) {
        Scheduler scheduler = schedulers.get(tag);
        if (scheduler == null) {
            switch (tag) {
                case JobSchedulerSchedulerV28.TAG:
                    scheduler = new JobSchedulerSchedulerV28(context);
                    break;
                case JobSchedulerSchedulerV26.TAG:
                    scheduler = new JobSchedulerSchedulerV26(context);
                    break;
                case JobSchedulerSchedulerV24.TAG:
                    scheduler = new JobSchedulerSchedulerV24(context);
                    break;
                case JobSchedulerSchedulerV21.TAG:
                    scheduler = new JobSchedulerSchedulerV21(context);
                    break;
                case GcmScheduler.TAG:
                    scheduler = new GcmScheduler(context);
                    break;
                case AlarmScheduler.TAG:
                    scheduler = new AlarmScheduler(context);
                    break;
                default:
                    throw new IllegalArgumentException("Missing scheduler for tag " + tag);
            }
            schedulers.put(tag, scheduler);
        }
        return scheduler;
    }
}
