package com.doist.jobschedulercompat.scheduler.gcm;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.INetworkTaskCallback;
import com.google.android.gms.gcm.PendingCallback;
import com.google.android.gms.gcm.Task;
import com.google.android.gms.gcm.TaskParams;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.JobParameters;
import com.doist.jobschedulercompat.JobScheduler;
import com.doist.jobschedulercompat.JobService;
import com.doist.jobschedulercompat.PersistableBundle;
import com.doist.jobschedulercompat.job.JobStatus;
import com.doist.jobschedulercompat.util.DeviceUtils;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.util.Log;
import android.util.SparseArray;

/**
 * Job service for {@link GcmScheduler}, the {@link GcmNetworkManager}-based scheduler.
 *
 * This service runs whenever {@link GcmNetworkManager} starts it based on the current jobs and constraints.
 * It is responsible for running jobs ({@link #ACTION_EXECUTE}) and reinitializing them ({@link #ACTION_INITIALIZE}).
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class GcmJobService extends Service implements JobService.Binder.Callback {
    private static final String LOG_TAG = "GcmJobService";

    /** @see GcmTaskService#onRunTask(TaskParams) */
    static final String ACTION_EXECUTE = "com.google.android.gms.gcm.ACTION_TASK_READY";
    /** @see GcmTaskService#onInitializeTasks() */
    static final String ACTION_INITIALIZE = "com.google.android.gms.gcm.SERVICE_ACTION_INITIALIZE";

    static final String EXTRA_TAG = "tag";
    static final String EXTRA_EXTRAS = "extras";
    static final String EXTRA_CALLBACK = "callback";

    private static final int RESULT_SUCCESS = 0;
    private static final int RESULT_RESCHEDULE = 1;
    private static final int RESULT_FAILURE = 2;

    private JobScheduler jobScheduler;
    private final SparseArray<Connection> connections = new SparseArray<>();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        jobScheduler = JobScheduler.get(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            try {
                String action = intent.getAction();
                switch (action) {
                    // Schedule all existing jobs per GcmNetworkManager's request.
                    case ACTION_INITIALIZE:
                        for (JobInfo job : jobScheduler.getAllPendingJobs()) {
                            jobScheduler.schedule(job);
                        }
                        break;

                    // Start the job specified in the Intent.
                    case ACTION_EXECUTE:
                        startJob(intent, startId);
                        break;
                }
            } finally {
                if (connections.size() == 0) {
                    stopSelf(startId);
                }
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void jobFinished(JobParameters params, boolean needsReschedule) {
        Connection connection = connections.get(params.getJobId());
        if (connection != null) {
            stopJob(connection, !needsReschedule, needsReschedule);
        }
    }

    /**
     * Starts the user's {@link JobService} by binding to it.
     *
     * Given {@link GcmNetworkManager}'s lack of support for roaming constraints and {@link Task}'s lack of information
     * on whether the deadline expired or not, both of these scenarios are handled manually.
     *
     * @param intent {@link GcmNetworkManager}'s intent, whose extras contain the parameters and callback.
     */
    private void startJob(Intent intent, int startId) {
        String tag = intent.getStringExtra(EXTRA_TAG);
        Bundle extras = intent.getBundleExtra(EXTRA_EXTRAS);
        intent.setExtrasClassLoader(PendingCallback.class.getClassLoader());
        Parcelable parcelledCallback = intent.getParcelableExtra(EXTRA_CALLBACK);
        if (tag == null || extras == null || !(parcelledCallback instanceof PendingCallback)) {
            // Invalid extras. Bail out.
            return;
        }
        int jobId = Integer.valueOf(tag);
        JobStatus jobStatus = jobScheduler.getJob(jobId);
        if (jobStatus != null) {
            boolean isOverrideDeadlineExpired = isOverrideDeadlineExpired(jobStatus);
            JobParameters params = new JobParameters(jobId, new PersistableBundle(extras), isOverrideDeadlineExpired);
            INetworkTaskCallback callback =
                    INetworkTaskCallback.Stub.asInterface(((PendingCallback) parcelledCallback).getIBinder());
            Connection connection = new Connection(jobId, startId, params, callback);
            // Handle not roaming and idle constraints manually, while respecting the deadline.
            if (isOverrideDeadlineExpired
                    || ((!jobStatus.hasNotRoamingConstraint() || DeviceUtils.isNotRoaming(this))
                    && (!jobStatus.hasIdleConstraint() || DeviceUtils.isIdle(this)))) {
                Intent jobIntent = new Intent();
                ComponentName service = jobStatus.getService();
                jobIntent.setComponent(service);
                if (bindService(jobIntent, connection, BIND_AUTO_CREATE)) {
                    connections.put(jobId, connection);
                } else {
                    Log.w(LOG_TAG, "Unable to bind to service: " + service + ". Have you declared it in the manifest?");
                    stopJob(connection, false, true);
                }
            } else {
                stopJob(connection, false, true);
            }
        }
    }

    /**
     * Stops the user's {@link android.app.job.JobService} by unbinding from it and passing the result to the callback.
     */
    private void stopJob(Connection connection, boolean success, boolean needsReschedule) {
        connections.remove(connection.jobId);
        unbindService(connection);
        try {
            connection.callback.taskFinished(
                    success ? RESULT_SUCCESS : (needsReschedule ? RESULT_RESCHEDULE : RESULT_FAILURE));
        } catch (RemoteException | NullPointerException e) {
            Log.w(LOG_TAG, "Encountered error while running the callback", e);
        }
        jobScheduler.onJobCompleted(connection.jobId, needsReschedule);
        stopSelf(connection.startId);
    }

    private boolean isOverrideDeadlineExpired(JobStatus jobStatus) {
        if (jobStatus.hasDeadlineConstraint()) {
            long jobDeadline = jobStatus.getLatestRunTimeElapsed();
            if (jobDeadline <= SystemClock.elapsedRealtime()) {
                jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_DEADLINE, true);
                return jobStatus.isDeadlineSatisfied();
            }
        }
        return false;
    }

    /**
     * {@link ServiceConnection} to the user's {@link JobService} that starts jobs when connected.
     */
    private class Connection implements ServiceConnection {
        private final int jobId;
        private final int startId;
        private final JobParameters params;
        private final INetworkTaskCallback callback;

        private JobService.Binder binder;

        private Connection(int jobId, int startId, JobParameters params, INetworkTaskCallback callback) {
            this.jobId = jobId;
            this.startId = startId;
            this.params = params;
            this.callback = callback;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (!(service instanceof JobService.Binder)) {
                Log.w(LOG_TAG, "Unknown service connected: " + service);
                stopJob(this, false, false);
                return;
            }
            binder = (JobService.Binder) service;
            if (!binder.startJob(params, GcmJobService.this)) {
                stopJob(this, true, false);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // Should never happen as it's the same process.
            binder = null;
            if (connections.get(jobId) == this) {
                stopJob(this, false, false);
            }
        }
    }
}
