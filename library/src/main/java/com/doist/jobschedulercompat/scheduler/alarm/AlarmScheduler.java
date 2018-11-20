package com.doist.jobschedulercompat.scheduler.alarm;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.job.JobStatus;
import com.doist.jobschedulercompat.scheduler.Scheduler;

import android.app.AlarmManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * {@link Scheduler} based on {@link AlarmManager}.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class AlarmScheduler extends Scheduler {
    public static final String TAG = "AlarmScheduler";

    public AlarmScheduler(Context context) {
        super(context);
    }

    @Override
    public int schedule(JobInfo job) {
        AlarmJobService.start(context);
        return RESULT_SUCCESS;
    }

    @Override
    public void cancel(int jobId) {
        AlarmJobService.start(context);
    }

    @Override
    public void cancelAll() {
        AlarmJobService.start(context);
    }

    @NonNull
    @Override
    public String getTag() {
        return TAG;
    }

    @Override
    public void onJobCompleted(int jobId, boolean needsReschedule) {
        AlarmJobService.start(context);
    }

    @Override
    public void onJobRescheduled(JobStatus newJob, JobStatus failedJob) {
        if (failedJob.hasContentTriggerConstraint() && newJob.hasContentTriggerConstraint()) {
            newJob.changedAuthorities = failedJob.changedAuthorities;
            newJob.changedUris = failedJob.changedUris;
        }
    }
}
