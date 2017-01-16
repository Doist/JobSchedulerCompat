package com.doist.jobschedulercompat.job;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.JobScheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.RestrictTo;

/**
 * Removes all jobs that are not persisted. Typically useful after the device boots.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class JobGcReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        JobScheduler jobScheduler = JobScheduler.get(context);
        for (JobInfo job : jobScheduler.getAllPendingJobs()) {
            if (!job.isPersisted()) {
                jobScheduler.removeJob(job.getId());
            }
        }
    }
}
