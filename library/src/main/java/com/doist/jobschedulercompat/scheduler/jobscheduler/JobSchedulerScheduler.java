package com.doist.jobschedulercompat.scheduler.jobscheduler;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.scheduler.Scheduler;

import android.annotation.TargetApi;
import android.app.job.JobScheduler;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

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

        android.app.job.JobInfo.Builder builder =
                new android.app.job.JobInfo.Builder(job.getId(), job.getService())
                        .setExtras(job.getExtras().toPersistableBundle())
                        .setRequiresCharging(job.isRequireCharging())
                        .setPersisted(job.isPersisted());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setRequiredNetworkType(job.getNetworkType());
            if (job.isRequireDeviceIdle()) {
                builder.setRequiresDeviceIdle(true);
            } else {
                builder.setBackoffCriteria(job.getInitialBackoffMillis(), job.getBackoffPolicy());
            }
        } else {
            int networkType = job.getNetworkType();
            networkType = networkType != JobInfo.NETWORK_TYPE_NOT_ROAMING ? networkType : JobInfo.NETWORK_TYPE_ANY;
            builder.setRequiredNetworkType(networkType)
                   .setBackoffCriteria(job.getInitialBackoffMillis(), job.getBackoffPolicy());
        }

        if (job.isPeriodic()) {
            builder.setPeriodic(job.getIntervalMillis());
        } else {
            builder.setMinimumLatency(job.getMinLatencyMillis())
                   .setOverrideDeadline(job.getMaxExecutionDelayMillis());
        }

        return builder.build();
    }
}
