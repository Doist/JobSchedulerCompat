package com.doist.jobschedulercompat.scheduler.jobscheduler;

import com.doist.jobschedulercompat.JobInfo;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

@TargetApi(Build.VERSION_CODES.N)
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class JobSchedulerSchedulerV24 extends JobSchedulerSchedulerV21 {
    public static final String TAG = "PlatformSchedulerV24";

    public JobSchedulerSchedulerV24(Context context) {
        super(context);
    }

    @NonNull
    @Override
    public String getTag() {
        return TAG;
    }

    protected android.app.job.JobInfo.Builder toPlatformJob(JobInfo job) {
        android.app.job.JobInfo.Builder builder = super.toPlatformJob(job);

        JobInfo.TriggerContentUri[] triggerContentUris = job.getTriggerContentUris();
        if (triggerContentUris != null) {
            for (JobInfo.TriggerContentUri triggerContentUri : triggerContentUris) {
                builder.addTriggerContentUri(
                        new android.app.job.JobInfo.TriggerContentUri(
                                triggerContentUri.getUri(), triggerContentUri.getFlags()));
            }
            builder.setTriggerContentUpdateDelay(job.getTriggerContentUpdateDelay());
            builder.setTriggerContentMaxDelay(job.getTriggerContentMaxDelay());
        }
        if (job.isPeriodic()) {
            // Re-set periodic parameters as flex might have been set.
            builder.setPeriodic(job.getIntervalMillis(), job.getFlexMillis());
        }

        return builder;
    }
}
