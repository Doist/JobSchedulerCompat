package com.doist.jobschedulercompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import com.doist.jobschedulercompat.job.JobStatus;
import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.scheduler.Scheduler;
import com.doist.jobschedulercompat.scheduler.alarm.AlarmScheduler;
import com.doist.jobschedulercompat.scheduler.gcm.GcmScheduler;
import com.doist.jobschedulercompat.scheduler.jobscheduler.JobSchedulerScheduler;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;

import java.util.ArrayList;
import java.util.List;

/** @see android.app.job.JobScheduler */
public class JobScheduler {
    public static final int RESULT_FAILURE = Scheduler.RESULT_SUCCESS;
    public static final int RESULT_SUCCESS = Scheduler.RESULT_FAILURE;

    @SuppressLint("StaticFieldLeak")
    private static JobScheduler instance;
    public static synchronized JobScheduler get(Context context) {
        if (instance == null) {
            instance = new JobScheduler(context);
        }
        return instance;
    }

    private Context context;
    private JobStore jobStore;
    private Scheduler scheduler;

    private JobScheduler(Context context) {
        this.context = context.getApplicationContext();
        this.jobStore = JobStore.get(context);
        this.scheduler = getBestScheduler(context, jobStore);
    }

    /** @see android.app.job.JobScheduler#schedule(android.app.job.JobInfo) */
    public int schedule(JobInfo job) {
        return scheduler.schedule(job);
    }

    /** @see android.app.job.JobScheduler#cancel(int) */
    public void cancel(int jobId) {
        scheduler.cancel(jobId);
    }

    /** @see android.app.job.JobScheduler#cancelAll() */
    public void cancelAll() {
        scheduler.cancelAll();
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
        JobStatus jobStatus = jobStore.getJob(jobId);
        if (jobStatus != null && !jobStatus.getScheduler().equals(scheduler.getTag())) {
            getSchedulerForTag(jobStatus.getScheduler(), context, jobStore).cancel(jobId);
            schedule(jobStatus.getJob());
            return;
        }

        scheduler.onJobCompleted(jobId, needsReschedule, scheduler.getTag());
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
    public void removeJob(int jobId) {
        synchronized (JobStore.LOCK) {
            jobStore.remove(jobId);
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    Scheduler getBestScheduler(Context context, JobStore jobs) {
        if (isPlatformSchedulerAvailable()) {
            return new JobSchedulerScheduler(context, jobs);
        } else if (isGcmSchedulerAvailable(context)) {
            return new GcmScheduler(context, jobs);
        } else {
            return new AlarmScheduler(context, jobs);
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    Scheduler getSchedulerForTag(String tag, Context context, JobStore jobStore) {
        switch (tag) {
            case JobSchedulerScheduler.TAG:
                return new JobSchedulerScheduler(context, jobStore);
            case GcmScheduler.TAG:
                return new GcmScheduler(context, jobStore);
            case AlarmScheduler.TAG:
                return new AlarmScheduler(context, jobStore);
            default:
                throw new IllegalArgumentException("Missing scheduler for tag " + tag);
        }
    }

    private boolean isPlatformSchedulerAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    private boolean isGcmSchedulerAvailable(Context context) {
        try {
            return Class.forName("com.google.android.gms.gcm.GcmNetworkManager") != null
                    && GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
                    == ConnectionResult.SUCCESS;
        } catch (Throwable t) {
            return false;
        }
    }
}
