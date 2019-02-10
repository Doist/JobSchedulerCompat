package com.doist.jobschedulercompat.util;

import com.doist.jobschedulercompat.JobParameters;
import com.doist.jobschedulercompat.JobService;

import android.util.SparseArray;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class NoopAsyncJobService extends JobService {
    public static final String EXTRA_DELAY = "delay";

    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    private static final SparseArray<ScheduledFuture> futures = new SparseArray<>();

    @Override
    public boolean onStartJob(final JobParameters params) {
        final int jobId = params.getJobId();
        final ScheduledFuture future = executor.schedule(new Runnable() {
            @Override
            public void run() {
                jobFinished(params, false);
                synchronized (NoopAsyncJobService.class) {
                    futures.remove(jobId);
                }
            }
        }, params.getExtras().getLong(EXTRA_DELAY), TimeUnit.MILLISECONDS);
        synchronized (NoopAsyncJobService.class) {
            futures.append(jobId, future);
        }
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        int jobId = params.getJobId();
        futures.get(jobId).cancel(true);
        synchronized (NoopAsyncJobService.class) {
            futures.remove(jobId);
        }
        return false;
    }

    static void waitForJob(int jobId) {
        try {
            futures.get(jobId).get();
        } catch (InterruptedException | ExecutionException e) {
            // Ignore.
        }
    }

    static void interruptJobs() {
        synchronized (NoopAsyncJobService.class) {
            for (int i = 0; i < futures.size(); i++) {
                futures.valueAt(i).cancel(true);
            }
            futures.clear();
        }
    }
}
