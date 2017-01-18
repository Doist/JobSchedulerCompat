package com.doist.jobschedulercompat.scheduler.gcm;

import com.google.android.gms.gcm.GcmNetworkManager;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.scheduler.Scheduler;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

import java.util.concurrent.TimeUnit;

/**
 * {@link Scheduler} based on {@link GcmNetworkManager}.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class GcmScheduler extends Scheduler {
    public static final String TAG = "GcmScheduler";

    static final String PACKAGE_GMS = "com.google.android.gms";
    static final String ACTION_SCHEDULE = "com.google.android.gms.gcm.ACTION_SCHEDULE";

    static final String BUNDLE_PARAM_SCHEDULER_ACTION = "scheduler_action";
    static final String BUNDLE_PARAM_TOKEN = "app";

    static final String SCHEDULER_ACTION_SCHEDULE_TASK = "SCHEDULE_TASK";
    static final String SCHEDULER_ACTION_CANCEL_TASK = "CANCEL_TASK";
    static final String SCHEDULER_ACTION_CANCEL_ALL = "CANCEL_ALL";

    static final String PARAM_TAG = "tag";
    static final String PARAM_SERVICE = "service";
    static final String PARAM_COMPONENT = "component";
    static final String PARAM_UPDATE_CURRENT = "update_current";
    static final String PARAM_EXTRAS = "extras";
    static final String PARAM_PERSISTED = "persisted";
    static final String PARAM_REQUIRES_CHARGING = "requiresCharging";
    static final String PARAM_REQUIRED_NETWORK = "requiredNetwork";
    static final String PARAM_TRIGGER_TYPE = "trigger_type";
    static final String PARAM_TRIGGER_WINDOW_END = "window_end";
    static final String PARAM_TRIGGER_WINDOW_FLEX = "period_flex";
    static final String PARAM_TRIGGER_WINDOW_PERIOD = "period";
    static final String PARAM_TRIGGER_WINDOW_START = "window_start";
    static final String PARAM_RETRY_STRATEGY = "retryStrategy";
    static final String PARAM_RETRY_STRATEGY_POLICY = "retry_policy";
    static final String PARAM_RETRY_STRATEGY_INITIAL_BACKOFF_SEC = "initial_backoff_seconds";
    static final String PARAM_RETRY_STRATEGY_MAXIMUM_BACKOFF_SEC = "maximum_backoff_seconds";

    static final int TRIGGER_TYPE_EXECUTION_WINDOW = 1;
    static final int TRIGGER_TYPE_IMMEDIATE = 2;

    static final int NETWORK_STATE_CONNECTED = 0;
    static final int NETWORK_STATE_UNMETERED = 1;
    static final int NETWORK_STATE_ANY = 2;

    static final int RETRY_POLICY_EXPONENTIAL = 0;
    static final int RETRY_POLICY_LINEAR = 1;

    private final PendingIntent token;

    public GcmScheduler(Context context, JobStore jobs) {
        super(context, jobs);
        this.token = PendingIntent.getBroadcast(context, 0, new Intent(), 0);
    }

    @Override
    public int schedule(JobInfo job) {
        super.schedule(job);
        context.sendBroadcast(getScheduleIntent(job));
        return RESULT_SUCCESS;
    }

    @Override
    public void cancel(int jobId) {
        super.cancel(jobId);
        context.sendBroadcast(getCancelIntent(jobId));
    }

    @Override
    public void cancelAll() {
        super.cancelAll();
        context.sendBroadcast(getCancelAllIntent());
    }

    @NonNull
    @Override
    public String getTag() {
        return TAG;
    }

    private Intent getScheduleIntent(JobInfo job) {
        Intent intent = getSchedulerIntent(SCHEDULER_ACTION_SCHEDULE_TASK);

        intent.putExtra(PARAM_TAG, String.valueOf(job.getId()));
        intent.putExtra(PARAM_SERVICE, GcmJobService.class.getName());
        intent.putExtra(PARAM_UPDATE_CURRENT, true);
        intent.putExtra(PARAM_PERSISTED, job.isPersisted());

        // Trigger.
        if (job.hasEarlyConstraint() || job.hasLateConstraint()) {
            intent.putExtra(PARAM_TRIGGER_TYPE, TRIGGER_TYPE_EXECUTION_WINDOW);
            if (job.isPeriodic()) {
                intent.putExtra(PARAM_TRIGGER_WINDOW_PERIOD, TimeUnit.MILLISECONDS.toSeconds(job.getIntervalMillis()));
                intent.putExtra(PARAM_TRIGGER_WINDOW_FLEX, TimeUnit.MILLISECONDS.toSeconds(job.getIntervalMillis()));
            } else {
                intent.putExtra(PARAM_TRIGGER_WINDOW_START, TimeUnit.MILLISECONDS.toSeconds(job.getMinLatencyMillis()));
                intent.putExtra(
                        PARAM_TRIGGER_WINDOW_END,
                        job.hasLateConstraint() ? TimeUnit.MILLISECONDS.toSeconds(job.getMaxExecutionDelayMillis())
                                                : TimeUnit.DAYS.toSeconds(7));
            }
        } else {
            intent.putExtra(PARAM_TRIGGER_TYPE, TRIGGER_TYPE_IMMEDIATE);
            intent.putExtra(PARAM_TRIGGER_WINDOW_START, 0L);
            intent.putExtra(PARAM_TRIGGER_WINDOW_END, 0L);
        }

        // Constraints.
        intent.putExtra(PARAM_REQUIRES_CHARGING, job.isRequireCharging());
        int requiredNetwork = NETWORK_STATE_ANY;
        switch (job.getNetworkType()) {
            case JobInfo.NETWORK_TYPE_ANY:
            case JobInfo.NETWORK_TYPE_NOT_ROAMING:
                // Roaming is checked in GcmJobService.
                requiredNetwork = NETWORK_STATE_CONNECTED;
                break;

            case JobInfo.NETWORK_TYPE_UNMETERED:
                requiredNetwork = NETWORK_STATE_UNMETERED;
                break;

        }
        intent.putExtra(PARAM_REQUIRED_NETWORK, requiredNetwork);

        // Backoff.
        Bundle retryStrategy = new Bundle();
        retryStrategy.putInt(
                PARAM_RETRY_STRATEGY_POLICY,
                job.getBackoffPolicy() == JobInfo.BACKOFF_POLICY_LINEAR
                ? RETRY_POLICY_LINEAR : RETRY_POLICY_EXPONENTIAL);
        retryStrategy.putInt(
                PARAM_RETRY_STRATEGY_INITIAL_BACKOFF_SEC,
                (int) TimeUnit.MILLISECONDS.toSeconds(job.getInitialBackoffMillis()));
        retryStrategy.putInt(
                PARAM_RETRY_STRATEGY_MAXIMUM_BACKOFF_SEC,
                (int) TimeUnit.HOURS.toSeconds(5) /* JobScheduler caps at 5 hours. */);
        intent.putExtra(PARAM_RETRY_STRATEGY, retryStrategy);

        intent.putExtra(PARAM_EXTRAS, job.getExtras().toBundle());

        return intent;
    }

    private Intent getCancelIntent(int jobId) {
        Intent intent = getSchedulerIntent(SCHEDULER_ACTION_CANCEL_TASK);
        intent.putExtra(PARAM_TAG, String.valueOf(jobId));
        intent.putExtra(PARAM_COMPONENT, new ComponentName(context, GcmJobService.class.getName()));
        return intent;
    }

    private Intent getCancelAllIntent() {
        Intent intent = getSchedulerIntent(SCHEDULER_ACTION_CANCEL_ALL);
        intent.putExtra(PARAM_COMPONENT, new ComponentName(context, GcmJobService.class.getName()));
        return intent;
    }

    private Intent getSchedulerIntent(String action) {
        Intent intent = new Intent(ACTION_SCHEDULE);
        intent.setPackage(PACKAGE_GMS);
        intent.putExtra(BUNDLE_PARAM_SCHEDULER_ACTION, action);
        intent.putExtra(BUNDLE_PARAM_TOKEN, token);
        return intent;
    }
}
