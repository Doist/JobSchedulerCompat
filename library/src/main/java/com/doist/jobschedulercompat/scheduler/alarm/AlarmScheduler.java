package com.doist.jobschedulercompat.scheduler.alarm;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.scheduler.Scheduler;

import android.app.AlarmManager;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

/**
 * {@link Scheduler} based on {@link AlarmManager}.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class AlarmScheduler extends Scheduler {
    public static final String TAG = "AlarmScheduler";

    public AlarmScheduler(Context context, JobStore jobs) {
        super(context, jobs);
    }

    @Override
    public int schedule(JobInfo job) {
        int result = super.schedule(job);
        AlarmJobService.start(context);
        return result;
    }

    @Override
    public void cancel(int jobId) {
        super.cancel(jobId);
        AlarmJobService.start(context);
    }

    @Override
    public void cancelAll() {
        super.cancelAll();
        AlarmJobService.start(context);
    }

    @NonNull
    @Override
    public String getTag() {
        return TAG;
    }

    @Override
    public void onJobCompleted(int jobId, boolean needsReschedule, String scheduler) {
        super.onJobCompleted(jobId, needsReschedule, scheduler);
        AlarmJobService.start(context);
    }
}