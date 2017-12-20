package com.doist.jobschedulercompat.util;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.scheduler.Scheduler;

import android.content.Context;
import android.support.annotation.NonNull;

public class NoopScheduler extends Scheduler {
    public NoopScheduler(Context context) {
        super(context);
    }

    @Override
    public int schedule(JobInfo job) {
        return RESULT_SUCCESS;
    }

    @Override
    public void cancel(int jobId) {

    }

    @Override
    public void cancelAll() {

    }

    @NonNull
    @Override
    public String getTag() {
        return "noop";
    }
}
