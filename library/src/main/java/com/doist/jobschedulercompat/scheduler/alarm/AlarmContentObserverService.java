package com.doist.jobschedulercompat.scheduler.alarm;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.JobScheduler;
import com.doist.jobschedulercompat.job.JobStatus;

import android.app.AlarmManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Content observing service for {@link AlarmScheduler}, the {@link AlarmManager}-based scheduler.
 *
 * This service runs whenever new jobs monitor content uris.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class AlarmContentObserverService extends Service {
    private static final int MAX_URIS_REPORTED = 50;

    private JobScheduler jobScheduler;

    private Handler handler;
    Map<JobInfo.TriggerContentUri, Observer> observers;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        jobScheduler = JobScheduler.get(this);

        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ContentResolver resolver = getContentResolver();

        // Unregister previously registered observers.
        if (observers != null) {
            for (Observer observer : observers.values()) {
                resolver.unregisterContentObserver(observer);
            }
        }

        // Register new observers.
        Map<JobInfo.TriggerContentUri, Observer> observers = new HashMap<>();
        List<JobStatus> jobStatuses = jobScheduler.getJobsByScheduler(AlarmScheduler.TAG);
        for (JobStatus jobStatus : jobStatuses) {
            JobInfo job = jobStatus.getJob();
            JobInfo.TriggerContentUri[] uris = job.getTriggerContentUris();
            if (uris != null) {
                for (JobInfo.TriggerContentUri uri : uris) {
                    Observer observer = observers.get(uri);
                    if (observer == null) {
                        observer = new Observer(handler);
                        observers.put(uri, observer);
                        resolver.registerContentObserver(
                                uri.getUri(),
                                (uri.getFlags() & JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS) != 0,
                                observer);
                    }
                    observer.jobIds.add(jobStatus.getJobId());
                }
            }
        }
        this.observers = observers;

        if (observers.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        } else {
            return START_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        for (Observer observer : observers.values()) {
            getContentResolver().unregisterContentObserver(observer);
        }
        observers.clear();
    }

    class Observer extends ContentObserver {
        Set<Integer> jobIds = new HashSet<>();

        Observer(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            for (Integer jobId : jobIds) {
                JobStatus existingJobStatus = jobScheduler.getJob(jobId);
                final long elapsedNowMillis = SystemClock.elapsedRealtime();
                long earliestRunTimeElapsedMillis = Math.max(
                        existingJobStatus.getEarliestRunTimeElapsed(),
                        elapsedNowMillis + existingJobStatus.getTriggerContentUpdateDelay());
                long latestRunTimeElapsedMillis = Math.max(
                        existingJobStatus.getLatestRunTimeElapsed(),
                        elapsedNowMillis + existingJobStatus.getTriggerContentMaxDelay());
                JobStatus jobStatus = new JobStatus(
                        existingJobStatus.getJob(), AlarmScheduler.TAG, existingJobStatus.getNumFailures(),
                        earliestRunTimeElapsedMillis, latestRunTimeElapsedMillis);
                if (jobStatus.changedUris == null) {
                    jobStatus.changedUris = new HashSet<>();
                }
                if (jobStatus.changedUris.size() < MAX_URIS_REPORTED) {
                    jobStatus.changedUris.add(uri);
                }
                if (jobStatus.changedAuthorities == null) {
                    jobStatus.changedAuthorities = new HashSet<>();
                }
                jobStatus.changedAuthorities.add(uri.getAuthority());
                jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_CONTENT_TRIGGER, true);
                jobScheduler.addJob(jobStatus);
            }

            AlarmJobService.start(AlarmContentObserverService.this);
        }
    }
}
