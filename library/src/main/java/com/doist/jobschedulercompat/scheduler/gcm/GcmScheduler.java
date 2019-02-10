package com.doist.jobschedulercompat.scheduler.gcm;

import com.google.android.gms.gcm.GcmNetworkManager;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.scheduler.Scheduler;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * {@link Scheduler} based on {@link GcmNetworkManager}.
 *
 * @see <a href="https://developers.google.com/android/reference/com/google/android/gms/gcm/Task">Task</a>
 * @see <a href="https://developers.google.com/android/reference/com/google/android/gms/gcm/PeriodicTask">PeriodicTask</a>
 * @see <a href="https://developers.google.com/android/reference/com/google/android/gms/gcm/OneoffTask">OneoffTask</a>
 * @see <a href="https://github.com/firebase/firebase-jobdispatcher-android/blob/master/jobdispatcher/src/main/java/com/firebase/jobdispatcher/GooglePlayJobWriter.java">FirebaseJobDispatcher</a>
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

    static final String PARAM_SOURCE = "source";
    static final String PARAM_SOURCE_VERSION = "source_version";

    static final String PARAM_TAG = "tag";
    static final String PARAM_SERVICE = "service";
    static final String PARAM_UPDATE_CURRENT = "update_current";
    static final String PARAM_EXTRAS = "extras";
    static final String PARAM_PERSISTED = "persisted";
    static final String PARAM_REQUIRED_NETWORK = "requiredNetwork";
    static final String PARAM_REQUIRES_CHARGING = "requiresCharging";
    static final String PARAM_REQUIRES_IDLE = "requiresIdle";

    static final String PARAM_TRIGGER_TYPE = "trigger_type";
    static final String PARAM_TRIGGER_WINDOW_END = "window_end";
    static final String PARAM_TRIGGER_WINDOW_FLEX = "period_flex";
    static final String PARAM_TRIGGER_WINDOW_PERIOD = "period";
    static final String PARAM_TRIGGER_WINDOW_START = "window_start";

    static final String PARAM_CONTENT_URI_ARRAY = "content_uri_array";
    static final String PARAM_CONTENT_URI_FLAGS_ARRAY = "content_uri_flags_array";

    static final String PARAM_RETRY_STRATEGY = "retryStrategy";
    static final String PARAM_RETRY_STRATEGY_POLICY = "retry_policy";
    static final String PARAM_RETRY_STRATEGY_INITIAL_BACKOFF_SEC = "initial_backoff_seconds";
    static final String PARAM_RETRY_STRATEGY_MAXIMUM_BACKOFF_SEC = "maximum_backoff_seconds";

    static final String PARAM_COMPONENT = "component";

    /*
     * This is found in Firebase JobDispatcher's code, presumably ensuring that Google Play Services maintains
     * compatibility with the way the data is fed into it (ie. the variables and workflow below).
     */
    private static final int JOB_DISPATCHER_SOURCE_CODE = 1 << 3;
    private static final int JOB_DISPATCHER_SOURCE_VERSION_CODE = 1;

    static final int TRIGGER_TYPE_EXECUTION_WINDOW = 1;
    static final int TRIGGER_TYPE_IMMEDIATE = 2;
    static final int TRIGGER_TYPE_CONTENT_URI = 3;

    static final int NETWORK_STATE_CONNECTED = 0;
    static final int NETWORK_STATE_UNMETERED = 1;
    static final int NETWORK_STATE_ANY = 2;

    static final int RETRY_POLICY_EXPONENTIAL = 0;
    static final int RETRY_POLICY_LINEAR = 1;

    private final PendingIntent token;

    public GcmScheduler(Context context) {
        super(context);
        this.token = PendingIntent.getBroadcast(context, 0, new Intent(), 0);
    }

    @Override
    public int schedule(JobInfo job) {
        context.sendBroadcast(getScheduleIntent(job));
        return RESULT_SUCCESS;
    }

    @Override
    public void cancel(int jobId) {
        context.sendBroadcast(getCancelIntent(jobId));
    }

    @Override
    public void cancelAll() {
        context.sendBroadcast(getCancelAllIntent());
    }

    @NonNull
    @Override
    public String getTag() {
        return TAG;
    }

    private Intent getScheduleIntent(JobInfo job) {
        Intent intent = getSchedulerIntent(SCHEDULER_ACTION_SCHEDULE_TASK);

        intent.putExtra(PARAM_TAG, String.valueOf(job.getId()))
              .putExtra(PARAM_SERVICE, GcmJobService.class.getName())
              .putExtra(PARAM_UPDATE_CURRENT, true)
              .putExtra(PARAM_PERSISTED, job.isPersisted())
              .putExtra(PARAM_EXTRAS, job.getExtras().toBundle());

        // Trigger.
        if (job.getTriggerContentUris() != null) {
            intent.putExtra(PARAM_TRIGGER_TYPE, TRIGGER_TYPE_CONTENT_URI);
            JobInfo.TriggerContentUri[] triggerContentUris = job.getTriggerContentUris();
            int size = triggerContentUris.length;
            Uri[] uriArray = new Uri[size];
            int[] flagsArray = new int[size];
            for (int i = 0; i < size; i++) {
                JobInfo.TriggerContentUri triggerContentUri = triggerContentUris[i];
                uriArray[i] = triggerContentUri.getUri();
                flagsArray[i] = triggerContentUri.getFlags();
            }
            intent.putExtra(PARAM_CONTENT_URI_FLAGS_ARRAY, flagsArray)
                  .putExtra(PARAM_CONTENT_URI_ARRAY, uriArray);
        } else if (job.hasEarlyConstraint() || job.hasLateConstraint()) {
            intent.putExtra(PARAM_TRIGGER_TYPE, TRIGGER_TYPE_EXECUTION_WINDOW);
            if (job.isPeriodic()) {
                intent.putExtra(PARAM_TRIGGER_WINDOW_PERIOD, TimeUnit.MILLISECONDS.toSeconds(job.getIntervalMillis()))
                      .putExtra(PARAM_TRIGGER_WINDOW_FLEX, TimeUnit.MILLISECONDS.toSeconds(job.getFlexMillis()));
            } else {
                intent.putExtra(PARAM_TRIGGER_WINDOW_START, TimeUnit.MILLISECONDS.toSeconds(job.getMinLatencyMillis()))
                      .putExtra(PARAM_TRIGGER_WINDOW_END,
                                job.hasLateConstraint()
                                ? TimeUnit.MILLISECONDS.toSeconds(job.getMaxExecutionDelayMillis())
                                : TimeUnit.DAYS.toSeconds(7));
            }
        } else {
            intent.putExtra(PARAM_TRIGGER_TYPE, TRIGGER_TYPE_IMMEDIATE)
                  .putExtra(PARAM_TRIGGER_WINDOW_START, 0L)
                  .putExtra(PARAM_TRIGGER_WINDOW_END, 1L);
        }

        // Constraints.
        intent.putExtra(PARAM_REQUIRES_CHARGING, job.isRequireCharging())
              .putExtra(PARAM_REQUIRES_IDLE, job.isRequireDeviceIdle());
        int requiredNetwork;
        switch (job.getNetworkType()) {
            case JobInfo.NETWORK_TYPE_ANY:
                requiredNetwork = NETWORK_STATE_CONNECTED;
                break;

            case JobInfo.NETWORK_TYPE_UNMETERED:
                requiredNetwork = NETWORK_STATE_UNMETERED;
                break;

            case JobInfo.NETWORK_TYPE_NONE:
            default:
                requiredNetwork = NETWORK_STATE_ANY;
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

        return intent;
    }

    private Intent getCancelIntent(int jobId) {
        return getSchedulerIntent(SCHEDULER_ACTION_CANCEL_TASK)
                .putExtra(PARAM_TAG, String.valueOf(jobId))
                .putExtra(PARAM_COMPONENT, new ComponentName(context, GcmJobService.class));
    }

    private Intent getCancelAllIntent() {
        return getSchedulerIntent(SCHEDULER_ACTION_CANCEL_ALL)
                .putExtra(PARAM_COMPONENT, new ComponentName(context, GcmJobService.class));
    }

    private Intent getSchedulerIntent(String action) {
        return new Intent(ACTION_SCHEDULE)
                .setPackage(PACKAGE_GMS)
                .putExtra(BUNDLE_PARAM_SCHEDULER_ACTION, action)
                .putExtra(BUNDLE_PARAM_TOKEN, token)
                .putExtra(PARAM_SOURCE, JOB_DISPATCHER_SOURCE_CODE)
                .putExtra(PARAM_SOURCE_VERSION, JOB_DISPATCHER_SOURCE_VERSION_CODE);
    }
}
