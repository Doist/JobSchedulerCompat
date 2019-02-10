package com.doist.jobschedulercompat.scheduler.jobscheduler;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.scheduler.Scheduler;

import android.annotation.TargetApi;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class JobSchedulerSchedulerV21 extends Scheduler {
    public static final String TAG = "PlatformSchedulerV21";

    private final JobScheduler jobScheduler;

    public JobSchedulerSchedulerV21(Context context) {
        super(context);
        jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }

    @Override
    public int schedule(JobInfo job) {
        int result = jobScheduler.schedule(toPlatformJob(job).build());
        return result == JobScheduler.RESULT_SUCCESS ? RESULT_SUCCESS : RESULT_FAILURE;
    }

    @Override
    public void cancel(int jobId) {
        jobScheduler.cancel(jobId);
    }

    @Override
    public void cancelAll() {
        jobScheduler.cancelAll();
    }

    @NonNull
    @Override
    public String getTag() {
        return TAG;
    }

    protected android.app.job.JobInfo.Builder toPlatformJob(JobInfo job) {
        if (job == null) {
            return null;
        }

        // Create builder with all parameters available across all sdks.
        ComponentName jobService = new ComponentName(context, JobSchedulerJobService.class);
        android.app.job.JobInfo.Builder builder =
                new android.app.job.JobInfo.Builder(job.getId(), jobService)
                        .setExtras(job.getExtras().toPersistableBundle())
                        .setRequiredNetworkType(job.getNetworkType())
                        .setRequiresCharging(job.isRequireCharging())
                        .setRequiresDeviceIdle(job.isRequireDeviceIdle());

        // Persisting jobs requires the RECEIVE_BOOT_COMPLETED. Only proxy the call if set.
        if (job.isPersisted()) {
            builder.setPersisted(true);
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

        if (job.getBackoffPolicy() != JobInfo.DEFAULT_BACKOFF_POLICY
                || job.getInitialBackoffMillis() != JobInfo.DEFAULT_INITIAL_BACKOFF_MILLIS) {
            builder.setBackoffCriteria(job.getInitialBackoffMillis(), job.getBackoffPolicy());
        }

        return builder;
    }
}
