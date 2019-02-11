package com.doist.jobschedulercompat;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.SparseArray;

import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/** @see android.app.job.JobService */
public abstract class JobService extends Service {
    private Binder binder;

    @NonNull
    @Override
    public final IBinder onBind(Intent intent) {
        if (binder == null) {
            binder = new Binder(this);
        }
        return binder;
    }

    /** @see android.app.job.JobService#onStartJob(android.app.job.JobParameters) */
    public abstract boolean onStartJob(JobParameters params);

    /** @see android.app.job.JobService#onStopJob(android.app.job.JobParameters) */
    public abstract boolean onStopJob(JobParameters params);

    /** @see android.app.job.JobService#jobFinished(android.app.job.JobParameters, boolean) */
    public final void jobFinished(JobParameters params, boolean needsReschedule) {
        if (binder != null) {
            binder.notifyJobFinished(params, needsReschedule);
        }
    }

    /**
     * Proxies callbacks between the scheduler job service that is handling the work and the user's {@link JobService}.
     *
     * All scheduler job services bind to this service to proxy their lifecycle. This allows maintaining parity with
     * JobScheduler's API, while hiding implementation details away from the user.
     *
     * Methods are synchronized to prevent race conditions when updating {@link #callbacks}.
     * There are no guarantees on which thread calls {@link #notifyJobFinished(JobParameters, boolean)}.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static class Binder extends android.os.Binder {
        private final WeakReference<JobService> serviceRef;
        private final SparseArray<Callback> callbacks;

        Binder(JobService service) {
            super();
            this.serviceRef = new WeakReference<>(service);
            this.callbacks = new SparseArray<>(1);
        }

        public synchronized boolean startJob(JobParameters params, Callback callback) {
            JobService service = serviceRef.get();
            if (service != null) {
                callbacks.put(params.getJobId(), callback);
                boolean willContinueRunning = service.onStartJob(params);
                if (!willContinueRunning) {
                    callbacks.remove(params.getJobId());
                }
                return willContinueRunning;
            } else {
                return false;
            }
        }

        public synchronized boolean stopJob(JobParameters params) {
            JobService service = serviceRef.get();
            if (service != null) {
                callbacks.remove(params.getJobId());
                return service.onStopJob(params);
            } else {
                return false;
            }
        }

        synchronized void notifyJobFinished(JobParameters params, boolean needsReschedule) {
            Callback callback = callbacks.get(params.getJobId());
            if (callback != null) {
                callbacks.remove(params.getJobId());
                callback.jobFinished(params, needsReschedule);
            }
        }

        public interface Callback {
            void jobFinished(JobParameters params, boolean needsReschedule);
        }
    }
}
