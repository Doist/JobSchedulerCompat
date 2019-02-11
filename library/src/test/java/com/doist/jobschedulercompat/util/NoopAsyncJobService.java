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

    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(0);
    private static final SparseArray<ScheduledFuture> futures = new SparseArray<>();

    @Override
    public boolean onStartJob(final JobParameters params) {
        final int jobId = params.getJobId();
        final ScheduledFuture future = executor.schedule(new Runnable() {
            @Override
            public void run() {
                jobFinished(params, false);
                synchronized (futures) {
                    futures.remove(jobId);
                }
            }
        }, Math.max(params.getExtras().getLong(EXTRA_DELAY), 1), TimeUnit.MILLISECONDS);
        synchronized (futures) {
            futures.append(jobId, future);
        }
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        int jobId = params.getJobId();
        ScheduledFuture future;
        synchronized (futures) {
            future = futures.get(jobId);
            futures.remove(jobId);
        }
        if (future != null) {
            future.cancel(true);
        }
        return false;
    }

    static void waitForJob(int jobId) {
        ScheduledFuture future;
        synchronized (futures) {
            future = futures.get(jobId);
        }
        try {
            if (future != null) {
                future.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            // Ignore.
        }
    }

    static void interruptJobs() {
        SparseArray<ScheduledFuture> currentFutures;
        synchronized (futures) {
            currentFutures = futures.clone();
            futures.clear();
        }
        for (int i = 0; i < currentFutures.size(); i++) {
            currentFutures.valueAt(i).cancel(true);
        }
    }
}
