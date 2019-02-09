package com.doist.jobschedulercompat.scheduler.gcm;

import com.google.android.gms.common.ConnectionResult;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.PersistableBundle;
import com.doist.jobschedulercompat.util.JobCreator;
import com.doist.jobschedulercompat.util.ShadowGoogleApiAvailability;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.app.Application;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.test.core.app.ApplicationProvider;

import static com.doist.jobschedulercompat.scheduler.gcm.GcmScheduler.PARAM_REQUIRES_CHARGING;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.KITKAT, shadows = {ShadowGoogleApiAvailability.class})
public class GcmSchedulerTest {
    private Application application;
    private GcmScheduler scheduler;

    @BeforeClass
    public static void enableGcm() {
        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SUCCESS);
    }

    @Before
    public void setup() {
        application = ApplicationProvider.getApplicationContext();
        scheduler = new GcmScheduler(application);
    }

    @Test
    public void testScheduleBroadcasts() {
        JobInfo job = JobCreator.create(application, 0)
                                .setMinimumLatency(TimeUnit.HOURS.toMillis(2))
                                .setOverrideDeadline(TimeUnit.DAYS.toMillis(1))
                                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                                .setPersisted(true)
                                .build();
        scheduler.schedule(job);

        Intent intent = getLastBroadcastIntent();
        assertNotNull(intent);
        assertIntentMatchesJobInfo(intent, job);

        PersistableBundle extras = new PersistableBundle();
        extras.putString("test", "test");
        job = JobCreator.create(application, 1)
                        .setExtras(extras)
                        .setPeriodic(TimeUnit.MINUTES.toMillis(30))
                        .setRequiresCharging(true)
                        .setRequiresDeviceIdle(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .build();
        scheduler.schedule(job);

        intent = getLastBroadcastIntent();
        assertNotNull(intent);
        assertIntentMatchesJobInfo(intent, job);

        job = JobCreator.create(application, 2)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING)
                        .addTriggerContentUri(new JobInfo.TriggerContentUri(Uri.parse("doist.com"), 0))
                        .addTriggerContentUri(new JobInfo.TriggerContentUri(
                                Uri.parse("todoist.com"), JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                        .addTriggerContentUri(new JobInfo.TriggerContentUri(Uri.parse("twistapp.com"), 0))
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
        } else if (job.getTriggerContentUris() != null) {
            JobInfo.TriggerContentUri[] triggerContentUris = job.getTriggerContentUris();
            int size = triggerContentUris.length;
            Uri[] uriArray = new Uri[size];
            int[] flagsArray = new int[size];
            for (int i = 0; i < size; i++) {
                JobInfo.TriggerContentUri triggerContentUri = triggerContentUris[i];
                uriArray[i] = triggerContentUri.getUri();
                flagsArray[i] = triggerContentUri.getFlags();
            }
            assertEquals(GcmScheduler.TRIGGER_TYPE_CONTENT_URI,
                         intent.getIntExtra(GcmScheduler.PARAM_TRIGGER_TYPE, -1));
            assertArrayEquals(uriArray, intent.getParcelableArrayExtra(GcmScheduler.PARAM_CONTENT_URI_ARRAY));
            assertArrayEquals(flagsArray, intent.getIntArrayExtra(GcmScheduler.PARAM_CONTENT_URI_FLAGS_ARRAY));
        } else {
            assertEquals(GcmScheduler.TRIGGER_TYPE_IMMEDIATE,
                         intent.getIntExtra(GcmScheduler.PARAM_TRIGGER_TYPE, -1));
            assertEquals(0L, intent.getLongExtra(GcmScheduler.PARAM_TRIGGER_WINDOW_START, -1L));
            assertEquals(1L, intent.getLongExtra(GcmScheduler.PARAM_TRIGGER_WINDOW_END, -1L));
        }

        assertEquals(job.isRequireCharging(), intent.getBooleanExtra(PARAM_REQUIRES_CHARGING, false));
        int requiredNetwork = GcmScheduler.NETWORK_STATE_ANY;
        switch (job.getNetworkType()) {
            case JobInfo.NETWORK_TYPE_ANY:
                requiredNetwork = GcmScheduler.NETWORK_STATE_CONNECTED;
                break;

            case JobInfo.NETWORK_TYPE_UNMETERED:
                requiredNetwork = GcmScheduler.NETWORK_STATE_UNMETERED;
                break;

            default:
                requiredNetwork = GcmScheduler.NETWORK_STATE_ANY;
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
        List<Intent> broadcastIntents = shadowOf(application).getBroadcastIntents();
        int count = broadcastIntents.size();
        if (count > 0) {
            return broadcastIntents.get(count - 1);
        } else {
            return null;
        }
    }
}
