package com.doist.jobschedulercompat.util;

import com.doist.jobschedulercompat.JobParameters;
import com.doist.jobschedulercompat.JobService;

import android.util.SparseArray;

public class NoopAsyncJobService extends JobService {
    public static final String EXTRA_DELAY = "delay";

    private static SparseArray<Thread> threads = new SparseArray<>();

    @Override
    public boolean onStartJob(final JobParameters params) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                boolean stopped = false;
                long delay = params.getExtras().getLong(EXTRA_DELAY);
                if (delay > 0) {
                    try {
                        if (!isInterrupted()) {
                            Thread.sleep(delay);
                        }
                    } catch (InterruptedException e) {
                        stopped = true;
                    }
                }
                threads.remove(params.getJobId());
                if (!stopped) {
                    jobFinished(params, false);
                }
            }
        };
        threads.put(params.getJobId(), thread);
        thread.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        int jobId = params.getJobId();
        threads.get(jobId).interrupt();
        threads.remove(jobId);
        return false;
    }

    static void waitForJob(int id) {
        Thread thread = threads.get(id);
        if (thread != null) {
            while (thread.isAlive()) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    // Ignore.
                }
            }
        }
    }

    static void interruptJobs() {
        SparseArray<Thread> currentThreads = threads.clone();
        for (int i = 0; i < currentThreads.size(); i++) {
            currentThreads.valueAt(i).interrupt();
        }
        for (int i = 0; i < currentThreads.size(); i++) {
            waitForJob(currentThreads.keyAt(i));
        }
    }
}
