package com.doist.jobschedulercompat.job;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.util.NoopJobService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import android.content.ComponentName;
import android.net.Uri;
import android.os.SystemClock;

import java.util.concurrent.TimeUnit;

import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class JobStatusTest {
    private ComponentName component;

    @Before
    public void setup() {
        component = new ComponentName(ApplicationProvider.getApplicationContext(), NoopJobService.class);
    }

    @Test
    public void testSingleConstraints() {
        JobStatus jobStatus;

        // Charging.
        jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component).setRequiresCharging(true).build(), "noop");
        assertTrue(jobStatus.hasChargingConstraint());
        assertFalse(jobStatus.isReady());
        assertFalse(jobStatus.isConstraintSatisfied(JobStatus.CONSTRAINT_CHARGING));
        jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_CHARGING, true);
        assertTrue(jobStatus.isConstraintSatisfied(JobStatus.CONSTRAINT_CHARGING));
        assertTrue(jobStatus.isReady());

        // Battery not low.
        jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component).setRequiresBatteryNotLow(true).build(), "noop");
        assertTrue(jobStatus.hasPowerConstraint());
        assertFalse(jobStatus.isReady());
        assertFalse(jobStatus.isConstraintSatisfied(JobStatus.CONSTRAINT_BATTERY_NOT_LOW));
        jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_BATTERY_NOT_LOW, true);
        assertTrue(jobStatus.isConstraintSatisfied(JobStatus.CONSTRAINT_BATTERY_NOT_LOW));
        assertTrue(jobStatus.isReady());

        // Storage not low.
        jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component).setRequiresStorageNotLow(true).build(), "noop");
        assertTrue(jobStatus.hasStorageNotLowConstraint());
        assertFalse(jobStatus.isReady());
        assertFalse(jobStatus.isConstraintSatisfied(JobStatus.CONSTRAINT_STORAGE_NOT_LOW));
        jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_STORAGE_NOT_LOW, true);
        assertTrue(jobStatus.isConstraintSatisfied(JobStatus.CONSTRAINT_STORAGE_NOT_LOW));
        assertTrue(jobStatus.isReady());

        // Idle.
        jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component).setRequiresDeviceIdle(true).build(), "noop");
        assertTrue(jobStatus.hasIdleConstraint());
        assertFalse(jobStatus.isReady());
        assertFalse(jobStatus.isConstraintSatisfied(JobStatus.CONSTRAINT_IDLE));
        jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_IDLE, true);
        assertTrue(jobStatus.isConstraintSatisfied(JobStatus.CONSTRAINT_IDLE));
        assertTrue(jobStatus.isReady());

        // Any connectivity.
        jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build(), "noop");
        assertTrue(jobStatus.hasConnectivityConstraint());
        assertTrue(jobStatus.needsAnyConnectivity());
        assertFalse(jobStatus.isReady());
        assertFalse(jobStatus.isConstraintSatisfied(JobStatus.CONSTRAINT_CONNECTIVITY));
        jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_CONNECTIVITY, true);
        assertTrue(jobStatus.isConstraintSatisfied(JobStatus.CONSTRAINT_CONNECTIVITY));
        assertTrue(jobStatus.isReady());

        // Unmetered connectivity.
        jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED).build(), "noop");
        assertTrue(jobStatus.hasConnectivityConstraint());
        assertTrue(jobStatus.needsUnmeteredConnectivity());
        assertFalse(jobStatus.isReady());
        assertFalse(jobStatus.isConstraintSatisfied(JobStatus.CONSTRAINT_UNMETERED));
        jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_UNMETERED, true);
        assertTrue(jobStatus.isConstraintSatisfied(JobStatus.CONSTRAINT_UNMETERED));
        assertTrue(jobStatus.isReady());

        // Not roaming connectivity.
        jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING).build(), "noop");
        assertTrue(jobStatus.hasConnectivityConstraint());
        assertTrue(jobStatus.needsNonRoamingConnectivity());
        assertFalse(jobStatus.isReady());
        assertFalse(jobStatus.isConstraintSatisfied(JobStatus.CONSTRAINT_NOT_ROAMING));
        jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_NOT_ROAMING, true);
        assertTrue(jobStatus.isConstraintSatisfied(JobStatus.CONSTRAINT_NOT_ROAMING));
        assertTrue(jobStatus.isReady());

        // Metered connectivity.
        jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_CELLULAR).build(), "noop");
        assertTrue(jobStatus.hasConnectivityConstraint());
        assertTrue(jobStatus.needsMeteredConnectivity());
        assertFalse(jobStatus.isReady());
        assertFalse(jobStatus.isConstraintSatisfied(JobStatus.CONSTRAINT_METERED));
        jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_METERED, true);
        assertTrue(jobStatus.isConstraintSatisfied(JobStatus.CONSTRAINT_METERED));
        assertTrue(jobStatus.isReady());

        // Minimum latency.
        jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component)
                        .setMinimumLatency(TimeUnit.MICROSECONDS.toMillis(15)).build(), "noop");
        assertTrue(jobStatus.hasTimingDelayConstraint());
        assertFalse(jobStatus.isReady());
        assertFalse(jobStatus.isConstraintSatisfied(JobStatus.CONSTRAINT_TIMING_DELAY));
        jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_TIMING_DELAY, true);
        assertTrue(jobStatus.isConstraintSatisfied(JobStatus.CONSTRAINT_TIMING_DELAY));
        assertTrue(jobStatus.isReady());

        // Trigger content uri.
        jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component)
                        .addTriggerContentUri(new JobInfo.TriggerContentUri(Uri.EMPTY, 0)).build(), "noop");
        assertTrue(jobStatus.hasContentTriggerConstraint());
        assertFalse(jobStatus.isReady());
        assertFalse(jobStatus.isConstraintSatisfied(JobStatus.CONSTRAINT_CONTENT_TRIGGER));
        jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_CONTENT_TRIGGER, true);
        assertTrue(jobStatus.isConstraintSatisfied(JobStatus.CONSTRAINT_CONTENT_TRIGGER));
        assertTrue(jobStatus.isReady());
    }

    @Test
    public void testDeadlineTrumpsAllConstraints() {
        JobStatus jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .setMinimumLatency(TimeUnit.HOURS.toMillis(24))
                        .setRequiresCharging(true)
                        .setOverrideDeadline(1L).build(),
                "noop");
        assertTrue(jobStatus.hasDeadlineConstraint());
        assertFalse(jobStatus.isReady());
        jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_DEADLINE, true);
        assertTrue(jobStatus.isReady());
    }

    @Test
    public void testTimingsFollowSystemClock() {
        long minimumLatency = TimeUnit.MINUTES.toMillis(15);
        long overrideDeadline = TimeUnit.HOURS.toMillis(2);

        JobStatus jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setMinimumLatency(minimumLatency)
                        .setOverrideDeadline(overrideDeadline)
                        .build(),
                "noop");
        compareTimestampsSubjectToCallLatency(
                jobStatus.getEarliestRunTimeElapsed(), SystemClock.elapsedRealtime() + minimumLatency);
        compareTimestampsSubjectToCallLatency(
                jobStatus.getLatestRunTimeElapsed(), SystemClock.elapsedRealtime() + overrideDeadline);
    }

    @Test
    public void testSetTriggerDelays() {
        JobStatus jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component)
                        .addTriggerContentUri(new JobInfo.TriggerContentUri(Uri.EMPTY, 0))
                        .setTriggerContentUpdateDelay(TimeUnit.SECONDS.toMillis(30))
                        .setTriggerContentMaxDelay(TimeUnit.MINUTES.toMillis(30))
                        .build(), "noop");
        assertEquals(jobStatus.getTriggerContentUpdateDelay(), TimeUnit.SECONDS.toMillis(30));
        assertEquals(jobStatus.getTriggerContentMaxDelay(), TimeUnit.MINUTES.toMillis(30));
    }

    @Test
    public void testDefaultTriggerDelays() {
        JobStatus jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component)
                        .addTriggerContentUri(new JobInfo.TriggerContentUri(Uri.EMPTY, 0))
                        .build(), "noop");
        assertEquals(jobStatus.getTriggerContentUpdateDelay(), JobStatus.DEFAULT_TRIGGER_UPDATE_DELAY);
        assertEquals(jobStatus.getTriggerContentMaxDelay(), JobStatus.DEFAULT_TRIGGER_MAX_DELAY);
    }

    @Test
    public void testMinimumTriggerDelays() {
        JobStatus jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component)
                        .addTriggerContentUri(new JobInfo.TriggerContentUri(Uri.EMPTY, 0))
                        .setTriggerContentUpdateDelay(0L)
                        .setTriggerContentMaxDelay(0L)
                        .build(), "noop");
        assertEquals(jobStatus.getTriggerContentUpdateDelay(), JobStatus.MIN_TRIGGER_UPDATE_DELAY);
        assertEquals(jobStatus.getTriggerContentMaxDelay(), JobStatus.MIN_TRIGGER_MAX_DELAY);
    }

    /**
     * Comparing timestamps before and after method calls involves some latency.
     */
    private void compareTimestampsSubjectToCallLatency(long ts1, long ts2) {
        assertTrue(Math.abs(ts1 - ts2) < 5);
    }
}
