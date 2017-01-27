package com.doist.jobschedulercompat.scheduler.jobscheduler;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.scheduler.Scheduler;

import android.annotation.TargetApi;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

import java.util.concurrent.TimeUnit;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class JobSchedulerScheduler extends Scheduler {
    public static final String TAG = "PlatformScheduler";

    private JobScheduler jobScheduler;

    public JobSchedulerScheduler(Context context, JobStore jobs) {
        super(context, jobs);
        jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }

    @Override
    public int schedule(JobInfo job) {
        super.schedule(job);
        int result = jobScheduler.schedule(toPlatformJob(job));
        return result == JobScheduler.RESULT_SUCCESS ? RESULT_SUCCESS : RESULT_FAILURE;
    }

    @Override
    public void cancel(int jobId) {
        super.cancel(jobId);
        jobScheduler.cancel(jobId);
    }

    @Override
    public void cancelAll() {
        super.cancelAll();
        jobScheduler.cancelAll();
    }

    @NonNull
    @Override
    public String getTag() {
        return TAG;
    }

    private android.app.job.JobInfo toPlatformJob(JobInfo job) {
        if (job == null) {
            return null;
        }

        ComponentName jobService = new ComponentName(context, JobSchedulerJobService.class);
        android.app.job.JobInfo.Builder builder =
                new android.app.job.JobInfo.Builder(job.getId(), jobService)
                        .setExtras(job.getExtras().toPersistableBundle())
                        .setRequiresCharging(job.isRequireCharging())
                        .setPersisted(job.isPersisted());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setRequiredNetworkType(job.getNetworkType());
            builder.setRequiresDeviceIdle(job.isRequireDeviceIdle());
        } else {
            int networkType = job.getNetworkType();
            if (networkType == JobInfo.NETWORK_TYPE_NOT_ROAMING) {
                networkType = JobInfo.NETWORK_TYPE_ANY;
            }
            builder.setRequiredNetworkType(networkType);

            // Idle constraint is unavailable before N, which will crash if there are no other constraints.
            // Set a small latency to prevent this on non-periodic jobs (periodic jobs are inherently constrained).
            if (job.isRequireDeviceIdle() && !job.isPeriodic()) {
                builder.setMinimumLatency(TimeUnit.MINUTES.toMillis(1));
            }
        }

        if (job.getBackoffPolicy() != JobInfo.DEFAULT_BACKOFF_POLICY
                || job.getInitialBackoffMillis() != JobInfo.DEFAULT_INITIAL_BACKOFF_MILLIS) {
            builder.setBackoffCriteria(job.getInitialBackoffMillis(), job.getBackoffPolicy());
        }

        if (job.isPeriodic()) {
            builder.setPeriodic(job.getIntervalMillis());
        } else {
            if (job.hasEarlyConstraint()) {
                builder.setMinimumLatency(job.getMinLatencyMillis());
            }
            if (job.hasLateConstraint()) {
                builder.setOverrideDeadline(job.getMaxExecutionDelayMillis());
            }
        }

        return builder.build();
    }
}
