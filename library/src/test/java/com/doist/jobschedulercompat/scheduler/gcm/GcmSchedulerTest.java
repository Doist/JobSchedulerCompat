package com.doist.jobschedulercompat.scheduler.gcm;

import com.google.android.gms.common.ConnectionResult;

import com.doist.jobschedulercompat.BuildConfig;
import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.PersistableBundle;
import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.util.JobCreator;
import com.doist.jobschedulercompat.util.NoopAsyncJobService;
import com.doist.jobschedulercompat.util.ShadowGoogleApiAvailability;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.doist.jobschedulercompat.scheduler.gcm.GcmScheduler.PARAM_REQUIRES_CHARGING;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = Build.VERSION_CODES.KITKAT, shadows = {ShadowGoogleApiAvailability.class})
public class GcmSchedulerTest {
    private Context context;
    private GcmScheduler scheduler;

    @BeforeClass
    public static void enableGcm() {
        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SUCCESS);
    }

    @Before
    public void setup() {
        context = RuntimeEnvironment.application;
        scheduler = new GcmScheduler(context, JobStore.get(context));
    }

    @After
    public void teardown() {
        NoopAsyncJobService.stopAll();
    }

    @Test
    public void testScheduleBroadcasts() {
        JobInfo job = JobCreator.create(context, 0)
                .setMinimumLatency(TimeUnit.HOURS.toMillis(2))
                .setOverrideDeadline(TimeUnit.DAYS.toMillis(1))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING)
                .setPersisted(true)
                .build();
        scheduler.schedule(job);

        Intent intent = getLastBroadcastIntent();
        assertNotNull(intent);
        assertIntentMatchesJobInfo(intent, job);

        PersistableBundle extras = new PersistableBundle();
        extras.putString("test", "test");
        job = JobCreator.create(context, 1)
                .setRequiresCharging(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setExtras(extras)
                .build();
        scheduler.schedule(job);

        intent = getLastBroadcastIntent();
        assertNotNull(intent);
        assertIntentMatchesJobInfo(intent, job);

        job = JobCreator.create(context, 2)
                .setPeriodic(TimeUnit.MINUTES.toMillis(30))
                .setRequiresDeviceIdle(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .build();
        scheduler.schedule(job);

        intent = getLastBroadcastIntent();
        assertNotNull(intent);
        assertIntentMatchesJobInfo(intent, job);
    }

    @Test
    public void testCancelBroadcasts() {
        scheduler.cancel(0);

        Intent intent = getLastBroadcastIntent();
        assertNotNull(intent);
        assertEquals(GcmScheduler.ACTION_SCHEDULE, intent.getAction());
        assertEquals(GcmScheduler.PACKAGE_GMS, intent.getPackage());
        assertEquals(GcmScheduler.SCHEDULER_ACTION_CANCEL_TASK,
                     intent.getStringExtra(GcmScheduler.BUNDLE_PARAM_SCHEDULER_ACTION));
        assertThat(intent.getParcelableExtra(GcmScheduler.BUNDLE_PARAM_TOKEN), instanceOf(PendingIntent.class));
        ComponentName component = intent.getParcelableExtra(GcmScheduler.PARAM_COMPONENT);
        assertNotNull(component);
        assertEquals(GcmJobService.class.getName(), component.getClassName());
    }

    @Test
    public void testCancelAllBroadcasts() {
        scheduler.cancelAll();

        Intent intent = getLastBroadcastIntent();
        assertNotNull(intent);
        assertEquals(GcmScheduler.ACTION_SCHEDULE, intent.getAction());
        assertEquals(GcmScheduler.PACKAGE_GMS, intent.getPackage());
        assertEquals(GcmScheduler.SCHEDULER_ACTION_CANCEL_ALL,
                     intent.getStringExtra(GcmScheduler.BUNDLE_PARAM_SCHEDULER_ACTION));
        assertThat(intent.getParcelableExtra(GcmScheduler.BUNDLE_PARAM_TOKEN),
                   instanceOf(PendingIntent.class));
        ComponentName component = intent.getParcelableExtra(GcmScheduler.PARAM_COMPONENT);
        assertNotNull(component);
        assertEquals(GcmJobService.class.getName(), component.getClassName());
    }

    private void assertIntentMatchesJobInfo(Intent intent, JobInfo job) {
        assertEquals(GcmScheduler.ACTION_SCHEDULE, intent.getAction());
        assertEquals(GcmScheduler.PACKAGE_GMS, intent.getPackage());
        assertEquals(GcmScheduler.SCHEDULER_ACTION_SCHEDULE_TASK,
                     intent.getStringExtra(GcmScheduler.BUNDLE_PARAM_SCHEDULER_ACTION));
        assertThat(intent.getParcelableExtra(GcmScheduler.BUNDLE_PARAM_TOKEN), instanceOf(PendingIntent.class));

        assertEquals(job.getId(), (int) Integer.valueOf(intent.getStringExtra(GcmScheduler.PARAM_TAG)));
        assertEquals(GcmJobService.class.getName(), intent.getStringExtra(GcmScheduler.PARAM_SERVICE));
        assertTrue(intent.getBooleanExtra(GcmScheduler.PARAM_UPDATE_CURRENT, false));
        assertEquals(job.isPersisted(), intent.getBooleanExtra(GcmScheduler.PARAM_PERSISTED, false));

        if (job.hasEarlyConstraint() || job.hasLateConstraint()) {
            assertEquals(GcmScheduler.TRIGGER_TYPE_EXECUTION_WINDOW,
                         intent.getIntExtra(GcmScheduler.PARAM_TRIGGER_TYPE, -1));
            if (job.isPeriodic()) {
                assertEquals(TimeUnit.MILLISECONDS.toSeconds(job.getIntervalMillis()),
                             intent.getLongExtra(GcmScheduler.PARAM_TRIGGER_WINDOW_PERIOD, -1L));
                assertEquals(TimeUnit.MILLISECONDS.toSeconds(job.getIntervalMillis()),
                             intent.getLongExtra(GcmScheduler.PARAM_TRIGGER_WINDOW_FLEX, -1L));
            } else {
                assertEquals(TimeUnit.MILLISECONDS.toSeconds(job.getMinLatencyMillis()),
                             intent.getLongExtra(GcmScheduler.PARAM_TRIGGER_WINDOW_START, -1L));
                if (job.hasLateConstraint()) {
                    assertEquals(TimeUnit.MILLISECONDS.toSeconds(job.getMaxExecutionDelayMillis()),
                                 intent.getLongExtra(GcmScheduler.PARAM_TRIGGER_WINDOW_END, -1L));
                } else {
                    assertEquals(TimeUnit.DAYS.toSeconds(7),
                                 intent.getLongExtra(GcmScheduler.PARAM_TRIGGER_WINDOW_END, -1L));
                }
            }
        } else {
            assertEquals(GcmScheduler.TRIGGER_TYPE_IMMEDIATE,
                         intent.getIntExtra(GcmScheduler.PARAM_TRIGGER_TYPE, -1));
            assertEquals(0L, intent.getLongExtra(GcmScheduler.PARAM_TRIGGER_WINDOW_START, -1L));
            assertEquals(0L, intent.getLongExtra(GcmScheduler.PARAM_TRIGGER_WINDOW_END, -1L));
        }

        assertEquals(job.isRequireCharging(), intent.getBooleanExtra(PARAM_REQUIRES_CHARGING, false));
        int requiredNetwork = GcmScheduler.NETWORK_STATE_ANY;
        switch (job.getNetworkType()) {
            case JobInfo.NETWORK_TYPE_ANY:
            case JobInfo.NETWORK_TYPE_NOT_ROAMING:
                requiredNetwork = GcmScheduler.NETWORK_STATE_CONNECTED;
                break;

            case JobInfo.NETWORK_TYPE_UNMETERED:
                requiredNetwork = GcmScheduler.NETWORK_STATE_UNMETERED;
                break;
        }
        assertEquals(requiredNetwork, intent.getIntExtra(GcmScheduler.PARAM_REQUIRED_NETWORK, -1));

        Bundle retryStrategy = intent.getBundleExtra(GcmScheduler.PARAM_RETRY_STRATEGY);
        int backoffPolicy = job.getBackoffPolicy() == JobInfo.BACKOFF_POLICY_LINEAR
                            ? GcmScheduler.RETRY_POLICY_LINEAR : GcmScheduler.RETRY_POLICY_EXPONENTIAL;
        assertEquals(backoffPolicy, retryStrategy.getInt(GcmScheduler.PARAM_RETRY_STRATEGY_POLICY, -1));
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(job.getInitialBackoffMillis()),
                     retryStrategy.getInt(GcmScheduler.PARAM_RETRY_STRATEGY_INITIAL_BACKOFF_SEC, -1));
        assertEquals(TimeUnit.HOURS.toSeconds(5),
                     retryStrategy.getInt(GcmScheduler.PARAM_RETRY_STRATEGY_MAXIMUM_BACKOFF_SEC, -1));

        assertEquals(job.getExtras().toMap(10),
                     new PersistableBundle(intent.getBundleExtra(GcmScheduler.PARAM_EXTRAS)).toMap(10));
    }

    private Intent getLastBroadcastIntent() {
        List<Intent> broadcastIntents = ShadowApplication.getInstance().getBroadcastIntents();
        int count = broadcastIntents.size();
        if (count > 0) {
            return broadcastIntents.get(count - 1);
        } else {
            return null;
        }
    }
}
