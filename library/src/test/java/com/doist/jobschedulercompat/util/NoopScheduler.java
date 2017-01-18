package com.doist.jobschedulercompat.util;

import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.scheduler.Scheduler;

import android.content.Context;
import android.support.annotation.NonNull;

public class NoopScheduler extends Scheduler {
    public NoopScheduler(Context context, JobStore jobs) {
        super(context, jobs);
    }

    @NonNull
    @Override
    public String getTag() {
        return "noop";
    }
}
