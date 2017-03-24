package com.doist.jobschedulercompat.scheduler.alarm;

import com.doist.jobschedulercompat.JobParameters;
import com.doist.jobschedulercompat.JobScheduler;
import com.doist.jobschedulercompat.JobService;
import com.doist.jobschedulercompat.job.JobStatus;
import com.doist.jobschedulercompat.util.DeviceUtils;
import com.doist.jobschedulercompat.util.WakeLockUtils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.util.Log;
import android.util.SparseArray;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Job service for {@link AlarmScheduler}, the {@link AlarmManager}-based scheduler.
 *
 * This service runs whenever new jobs are scheduled or deleted, whenever constraints (eg. connectivity, charging)
 * might have changed, and whenever a previous job finished, as these can schedule new jobs.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class AlarmJobService extends Service implements JobService.Binder.Callback {
    private static final String LOG_TAG = "AlarmJobService";

    private static final String KEY_WAKE_LOCK_PROCESS = "process";
    private static final String KEY_WAKE_LOCK_JOB = "job";

    private static final long TIMEOUT_WAKE_LOCK_PROCESS = TimeUnit.MINUTES.toMillis(1);
    private static final long TIMEOUT_WAKE_LOCK_JOB = TimeUnit.MINUTES.toMillis(3); // Same as JobScheduler's.

    static void start(Context context) {
        WakeLockUtils.acquireWakeLock(context, KEY_WAKE_LOCK_PROCESS, TIMEOUT_WAKE_LOCK_PROCESS);
        context.startService(new Intent(context, AlarmJobService.class));
    }

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
        try {
            // Stop jobs that have been cancelled.
            for (int i = connections.size() - 1; i >= 0; i--) {
                Connection connection = connections.valueAt(i);
                if (jobScheduler.getJob(connection.params.getJobId()) == null) {
                    stopJob(connection, false);
                }
            }

            // Start jobs that are ready, schedule jobs that are not.
            List<JobStatus> jobStatuses = jobScheduler.getJobsByScheduler(AlarmScheduler.TAG);
            updateConstraints(jobStatuses);
            for (JobStatus jobStatus : jobStatuses) {
                Connection connection = connections.get(jobStatus.getJobId());
                if (jobStatus.isReady()) {
                    if (connection == null) {
                        // Job is ready and not already running, bind to the service and start the job.
                        startJob(jobStatus, startId);
                    }
                } else if (connection != null) {
                    // Job is running but not ready, unbind from the service and stop the job.
                    boolean needsReschedule = connection.binder != null && connection.binder.stopJob(connection.params);
                    stopJob(connection, needsReschedule);
                }
            }

            // Enable alarm receiver if there any alarm-based jobs left.
            setComponentEnabled(this, AlarmReceiver.class, !jobStatuses.isEmpty());
        } finally {
            if (connections.size() == 0) {
                stopSelf(startId);
            }

            // Each job holds its own wake lock while processing, release ours now.
            WakeLockUtils.releaseWakeLock(KEY_WAKE_LOCK_PROCESS);
        }
        return START_NOT_STICKY;
    }

    /**
     * Updates the state of each constraint in each {@link JobStatus}.
     *
     * When constraints are not met, receivers and/or alarms are scheduled for when it's appropriate to run again.
     */
    private void updateConstraints(List<JobStatus> jobStatuses) {
        // Update charging constraint.
        boolean unsatisfiedChargingConstraint = false;
        boolean charging = DeviceUtils.isCharging(this);
        for (JobStatus jobStatus : jobStatuses) {
            jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_CHARGING, charging);
            unsatisfiedChargingConstraint |= jobStatus.hasChargingConstraint() && !charging;
        }

        // Enable charging receiver if there are unmet constraints, or disable it if there aren't.
        setComponentEnabled(this, AlarmReceiver.BatteryReceiver.class, unsatisfiedChargingConstraint);

        // Update idle constraint.
        boolean unsatisfiedIdleConstraint = false;
        boolean idle = DeviceUtils.isIdle(this);
        for (JobStatus jobStatus : jobStatuses) {
            jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_IDLE, idle);
            unsatisfiedIdleConstraint |= jobStatus.hasIdleConstraint() && !idle;
        }

        // ACTION_SCREEN_OFF cannot be received through a receiver declared in AndroidManifest.
        // AlarmReceiver will be scheduled to run by AlarmManager at most 30 minutes from now.

        // Get connectivity constraints.
        boolean unsatisfiedConnectivityConstraint = false;
        boolean connected = DeviceUtils.isConnected(this);
        boolean unmetered = connected && DeviceUtils.isUnmetered(this);
        boolean notRoaming = connected && DeviceUtils.isNotRoaming(this);
        for (JobStatus jobStatus : jobStatuses) {
            jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_CONNECTIVITY, connected);
            unsatisfiedConnectivityConstraint |= jobStatus.hasConnectivityConstraint() && !connected;
            jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_UNMETERED, unmetered);
            unsatisfiedConnectivityConstraint |= jobStatus.hasUnmeteredConstraint() && !unmetered;
            jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_NOT_ROAMING, notRoaming);
            unsatisfiedConnectivityConstraint |= jobStatus.hasNotRoamingConstraint() && !notRoaming;
        }

        // Enable connectivity receiver if there are unmet constraints, or disable it if there aren't.
        setComponentEnabled(this, AlarmReceiver.ConnectivityReceiver.class, unsatisfiedConnectivityConstraint);

        // Get timing constraints.
        long nextExpiryTime = Long.MAX_VALUE;
        long nextDelayTime = Long.MAX_VALUE;
        long nowElapsed = SystemClock.elapsedRealtime();
        for (JobStatus jobStatus : jobStatuses) {
            if (jobStatus.hasDeadlineConstraint()) {
                long jobDeadline = jobStatus.getLatestRunTimeElapsed();
                if (jobDeadline <= nowElapsed) {
                    jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_DEADLINE, true);
                    continue; // Skip timing delay, job will run now.
                } else if (nextExpiryTime > jobDeadline) {
                    nextExpiryTime = jobDeadline;
                }
            }
            if (jobStatus.hasTimingDelayConstraint()) {
                long jobDelayTime = jobStatus.getEarliestRunTimeElapsed();
                if (jobDelayTime <= nowElapsed) {
                    jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_TIMING_DELAY, true);
                } else if (nextDelayTime > jobDelayTime) {
                    nextDelayTime = jobDelayTime;
                }
            }
        }

        // Schedule alarm to run at the earliest deadline, if any.
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(this, AlarmReceiver.class), 0);
        long triggerAtMillis = Math.min(nextExpiryTime, nextDelayTime);
        if (unsatisfiedIdleConstraint) {
            triggerAtMillis = Math.min(triggerAtMillis, TimeUnit.MINUTES.toMillis(30));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent);
            }
        } else if (triggerAtMillis != Long.MAX_VALUE) {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent);
        } else {
            alarmManager.cancel(pendingIntent);
        }
    }

    @Override
    public void jobFinished(JobParameters params, boolean needsReschedule) {
        Connection connection = connections.get(params.getJobId());
        if (connection != null) {
            stopJob(connection, needsReschedule);
        }
    }

    /**
     * Starts the user's {@link JobService} by binding to it.
     */
    private void startJob(JobStatus jobStatus, int startId) {
        WakeLockUtils.acquireWakeLock(this, KEY_WAKE_LOCK_JOB, TIMEOUT_WAKE_LOCK_JOB);
        int jobId = jobStatus.getJobId();
        JobParameters params = new JobParameters(jobId, jobStatus.getExtras(), jobStatus.isDeadlineSatisfied());
        Connection connection = new Connection(jobId, startId, params);
        Intent jobIntent = new Intent();
        ComponentName service = jobStatus.getService();
        jobIntent.setComponent(service);
        if (bindService(jobIntent, connection, BIND_AUTO_CREATE)) {
            connections.put(jobId, connection);
        } else {
            Log.w(LOG_TAG, "Unable to bind to service: " + service + ". Have you declared it in the manifest?");
            stopJob(connection, true);
        }
    }

    /**
     * Stops the user's {@link JobService} by unbinding from it.
     */
    private void stopJob(Connection connection, boolean needsReschedule) {
        connections.remove(connection.jobId);
        unbindService(connection);
        jobScheduler.onJobCompleted(connection.jobId, needsReschedule);
        stopSelf(connection.startId);
        WakeLockUtils.releaseWakeLock(KEY_WAKE_LOCK_JOB);
    }

    private static void setComponentEnabled(Context context, Class cls, boolean enabled) {
        PackageManager pm = context.getPackageManager();
        if (pm != null) {
            pm.setComponentEnabledSetting(
                    new ComponentName(context, cls),
                    enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }

    /**
     * {@link ServiceConnection} to the user's {@link JobService} that starts jobs when connected.
     */
    private class Connection implements ServiceConnection {
        private final int jobId;
        private final int startId;
        private final JobParameters params;

        private JobService.Binder binder;

        private Connection(int jobId, int startId, JobParameters params) {
            this.jobId = jobId;
            this.startId = startId;
            this.params = params;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (!(service instanceof JobService.Binder)) {
                Log.w(LOG_TAG, "Unknown service connected: " + service);
                stopJob(this, false);
                return;
            }
            binder = (JobService.Binder) service;
            if (!binder.startJob(params, AlarmJobService.this)) {
                stopJob(this, false);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // Should never happen as it's the same process.
            binder = null;
            if (connections.get(jobId) == this) {
                stopJob(this, false);
            }
        }
    }
}
