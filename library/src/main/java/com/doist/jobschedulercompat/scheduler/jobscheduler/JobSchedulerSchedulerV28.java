package com.doist.jobschedulercompat.scheduler.jobscheduler;

import com.doist.jobschedulercompat.JobInfo;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

@TargetApi(Build.VERSION_CODES.P)
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class JobSchedulerSchedulerV28 extends JobSchedulerSchedulerV26 {
    public static final String TAG = "PlatformSchedulerV28";

    public JobSchedulerSchedulerV28(Context context) {
        super(context);
    }

    @NonNull
    @Override
    public String getTag() {
        return TAG;
    }

    protected android.app.job.JobInfo.Builder toPlatformJob(JobInfo job) {
        android.app.job.JobInfo.Builder builder = super.toPlatformJob(job);

        if (job.getRequiredNetwork() != null) {
            builder.setRequiredNetwork(job.getRequiredNetwork());
        }
        builder.setEstimatedNetworkBytes(job.getEstimatedNetworkDownloadBytes(), job.getEstimatedNetworkUploadBytes());
        builder.setImportantWhileForeground(job.isImportantWhileForeground());
        builder.setPrefetch(job.isPrefetch());

        return builder;
    }
}
