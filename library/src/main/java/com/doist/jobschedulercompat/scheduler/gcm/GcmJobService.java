package com.doist.jobschedulercompat.scheduler.gcm;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.Task;
import com.google.android.gms.gcm.TaskParams;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.JobParameters;
import com.doist.jobschedulercompat.JobScheduler;
import com.doist.jobschedulercompat.JobService;
import com.doist.jobschedulercompat.PersistableBundle;
import com.doist.jobschedulercompat.job.JobStatus;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

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

    private static final int RESULT_SUCCESS = 0;
    private static final int RESULT_RESCHEDULE = 1;
    private static final int RESULT_FAILURE = 2;

    private static final String DESCRIPTOR = "com.google.android.gms.gcm.INetworkTaskCallback";
    private static final int TRANSACTION_TASK_FINISHED = IBinder.FIRST_CALL_TRANSACTION + 1;

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
                if (ACTION_INITIALIZE.equals(action)) {
                    // Schedule all existing jobs per GcmNetworkManager's request.
                    for (JobInfo job : jobScheduler.getAllPendingJobs()) {
                        jobScheduler.schedule(job);
                    }
                } else if (ACTION_EXECUTE.equals(action)) {
                    startJob(intent, startId);
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
        GcmIntentParser parser;
        try {
            parser = new GcmIntentParser(intent.getExtras());
        } catch (RuntimeException e) {
            // Invalid extras. Bail out.
            return;
        }

        int jobId = parser.getJobId();
        Bundle extras = parser.getExtras();
        Uri[] triggeredUris = null;
        if (parser.getTriggeredContentUris() != null) {
            triggeredUris = parser.getTriggeredContentUris().toArray(new Uri[0]);
        }
        String[] triggeredAuthorities = null;
        if (parser.getTriggeredContentAuthorities() != null) {
            triggeredAuthorities = parser.getTriggeredContentAuthorities().toArray(new String[0]);
        }
        IBinder callback = parser.getCallback();

        JobStatus jobStatus = jobScheduler.getJob(jobId);
        if (jobStatus != null) {
            JobInfo job = jobStatus.getJob();
            JobParameters params = new JobParameters(
                    jobId, new PersistableBundle(extras), job.getTransientExtras(),
                    isOverrideDeadlineExpired(jobStatus), triggeredUris, triggeredAuthorities);
            Connection connection = new Connection(jobId, startId, params, callback);
            Intent jobIntent = new Intent();
            ComponentName service = jobStatus.getServiceComponent();
            jobIntent.setComponent(service);
            if (bindService(jobIntent, connection, BIND_AUTO_CREATE)) {
                connections.put(jobId, connection);
            } else {
                Log.w(LOG_TAG, "Unable to bind to service: " + service + ". Have you declared it in the manifest?");
                stopJob(connection, false, true);
            }
        }
    }

    /**
     * Stops the user's {@link android.app.job.JobService} by unbinding from it and passing the result to the callback.
     */
    private void stopJob(Connection connection, boolean success, boolean needsReschedule) {
        connections.remove(connection.jobId);
        try {
            unbindService(connection);
        } catch (IllegalArgumentException e) {
            // Service not registered at this point. Drop it.
        }
        Parcel request = Parcel.obtain();
        Parcel response = Parcel.obtain();
        try {
            request.writeInterfaceToken(DESCRIPTOR);
            response.writeInt(success ? RESULT_SUCCESS : (needsReschedule ? RESULT_RESCHEDULE : RESULT_FAILURE));
            connection.remote.transact(TRANSACTION_TASK_FINISHED, request, response, 0);
            response.readException();
        } catch (RemoteException | RuntimeException e) {
            Log.w(LOG_TAG, "Encountered error while running the callback", e);
        } finally {
            request.recycle();
            response.recycle();
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
        private final IBinder remote;

        private JobService.Binder binder;

        private Connection(int jobId, int startId, JobParameters params, IBinder remote) {
            this.jobId = jobId;
            this.startId = startId;
            this.params = params;
            this.remote = remote;
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
