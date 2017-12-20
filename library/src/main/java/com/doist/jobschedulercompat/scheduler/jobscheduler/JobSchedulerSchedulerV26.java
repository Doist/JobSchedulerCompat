package com.doist.jobschedulercompat.scheduler.jobscheduler;

import com.doist.jobschedulercompat.JobInfo;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

@TargetApi(Build.VERSION_CODES.O)
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class JobSchedulerSchedulerV26
        extends com.doist.jobschedulercompat.scheduler.jobscheduler.JobSchedulerSchedulerV24 {
    public static final String TAG = "PlatformSchedulerV26";

    public JobSchedulerSchedulerV26(Context context) {
        super(context);
    }

    @NonNull
    @Override
    public String getTag() {
        return TAG;
    }

    protected android.app.job.JobInfo.Builder toPlatformJob(JobInfo job) {
        android.app.job.JobInfo.Builder builder = super.toPlatformJob(job);

        builder.setTransientExtras(job.getTransientExtras());
        builder.setRequiresBatteryNotLow(job.isRequireBatteryNotLow());
        builder.setRequiresStorageNotLow(job.isRequireStorageNotLow());

        return builder;
    }
}
