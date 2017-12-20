package com.doist.jobschedulercompat;

import com.google.android.gms.common.ConnectionResult;

import com.doist.jobschedulercompat.job.JobStatus;
import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.scheduler.Scheduler;
import com.doist.jobschedulercompat.scheduler.alarm.AlarmScheduler;
import com.doist.jobschedulercompat.scheduler.gcm.GcmScheduler;
import com.doist.jobschedulercompat.scheduler.jobscheduler.JobSchedulerSchedulerV21;
import com.doist.jobschedulercompat.scheduler.jobscheduler.JobSchedulerSchedulerV24;
import com.doist.jobschedulercompat.scheduler.jobscheduler.JobSchedulerSchedulerV26;
import com.doist.jobschedulercompat.util.JobCreator;
import com.doist.jobschedulercompat.util.NoopScheduler;
import com.doist.jobschedulercompat.util.ShadowGoogleApiAvailability;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class JobSchedulerTest {
    private Context context;
    private JobScheduler jobScheduler;
    private JobStore jobStore;
    private Scheduler noopScheduler;

    @Before
    public void setup() {
        context = RuntimeEnvironment.application;
        jobScheduler = JobScheduler.get(context);
        jobStore = JobStore.get(context);

        noopScheduler = new NoopScheduler(context);
        jobScheduler.schedulers.put(noopScheduler.getTag(), noopScheduler);
    }

    @After
    public void teardown() {
        jobStore.clear();
    }

    @Test
    public void testSchedule() {
        jobScheduler.schedule(JobCreator.create(context, 0).setRequiresCharging(true).build());

        assertJobSchedulerContains(0);

        jobScheduler.schedule(JobCreator.create(context, 1).setRequiresCharging(true).build());

        assertJobSchedulerContains(0, 1);

        jobScheduler.schedule(JobCreator.create(context, 0).setRequiresCharging(true).build());

        assertJobSchedulerContains(0, 1);
    }

    @Test(expected = IllegalStateException.class)
    public void testScheduleHasUpperLimit() {
        for (int i = 0; i <= JobScheduler.MAX_JOBS + 1; i++) {
            jobScheduler.schedule(
                    JobCreator.create(context, i, 2000).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build());
        }
    }

    @Test
    public void testCancel() {
        jobScheduler.schedule(JobCreator.create(context, 0).setRequiresDeviceIdle(true).build());
        jobScheduler.schedule(JobCreator.create(context, 1).setRequiresDeviceIdle(true).build());

        assertJobSchedulerContains(0, 1);

        jobScheduler.cancel(0);

        assertJobSchedulerContains(1);

        jobScheduler.cancel(1);

        assertJobSchedulerContains();
    }

    @Test
    public void testCancelAll() {
        jobScheduler.schedule(JobCreator.create(context, 0).setMinimumLatency(TimeUnit.HOURS.toMillis(1)).build());
        jobScheduler.schedule(JobCreator.create(context, 1).setMinimumLatency(TimeUnit.HOURS.toMillis(1)).build());
        jobScheduler.schedule(JobCreator.create(context, 2).setMinimumLatency(TimeUnit.HOURS.toMillis(1)).build());

        assertJobSchedulerContains(0, 1, 2);

        jobScheduler.cancelAll();

        assertJobSchedulerContains();
    }

    @Test
    public void testJobFinishedSuccess() {
        JobStatus jobStatus = JobStatus.createFromJobInfo(
                JobCreator.create(context, 0).setRequiresCharging(true /* Random constraint. */).build(),
                noopScheduler.getTag());
        jobStore.add(jobStatus);

        jobScheduler.onJobCompleted(0, false);
        assertJobSchedulerContains(/* Nothing. */);
    }

    @Test
    public void testPeriodicJobFinishedSuccess() {
        long timeMs = TimeUnit.MINUTES.toMillis(15);
        JobStatus jobStatus = JobStatus.createFromJobInfo(
                JobCreator.create(context, 0).setPeriodic(timeMs).build(), noopScheduler.getTag());
        jobStore.add(jobStatus);
        Robolectric.getForegroundThreadScheduler().advanceBy(timeMs, TimeUnit.MILLISECONDS);

        jobScheduler.onJobCompleted(0, false);
        JobStatus newJobStatus = jobScheduler.getJob(0);
        assertEquals(jobStatus.getEarliestRunTimeElapsed() + timeMs, newJobStatus.getEarliestRunTimeElapsed());
        assertEquals(jobStatus.getLatestRunTimeElapsed() + timeMs, newJobStatus.getLatestRunTimeElapsed());
    }

    @Test
    public void testPeriodicJobFinishedFailure() {
        long currentTimeMs = SystemClock.elapsedRealtime();
        long timeMs = TimeUnit.MINUTES.toMillis(15);
        long timeMaxMs = TimeUnit.HOURS.toMillis(5);

        JobStatus jobStatus = JobStatus.createFromJobInfo(
                JobCreator.create(context, 0)
                          .setPeriodic(timeMs)
                          .setBackoffCriteria(timeMs, JobInfo.BACKOFF_POLICY_LINEAR)
                          .build(), noopScheduler.getTag());
        jobStore.add(jobStatus);

        // Fail until it reaches 5 hours.
        for (int i = 0; i < timeMaxMs / timeMs; i++) {
            jobScheduler.onJobCompleted(0, true);
            jobStatus = jobScheduler.getJob(0);
            assertEquals(currentTimeMs + timeMs * (i + 1), jobStatus.getEarliestRunTimeElapsed());
        }
        jobScheduler.onJobCompleted(0, true);
        jobStatus = jobStore.getJob(0);
        assertEquals(currentTimeMs + timeMaxMs, jobStatus.getEarliestRunTimeElapsed());
    }

    @Test
    public void testJobFinishedFailureExponential() {
        long currentTimeMs = SystemClock.elapsedRealtime();
        long timeMs = TimeUnit.MINUTES.toMillis(15);
        long timeMaxMs = TimeUnit.HOURS.toMillis(5);

        JobStatus jobStatus = JobStatus.createFromJobInfo(
                JobCreator.create(context, 0)
                          .setRequiresCharging(true /* Random constraint. */)
                          .setBackoffCriteria(timeMs, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                          .build(), noopScheduler.getTag());
        jobStore.add(jobStatus);

        // Fail until we reach five hours.
        for (int i = 0; i < Math.sqrt(timeMaxMs) / Math.sqrt(timeMs); i++) {
            jobScheduler.onJobCompleted(0, true);
            jobStatus = jobScheduler.getJob(0);
            assertEquals(currentTimeMs + timeMs * Math.pow(2, i), jobStatus.getEarliestRunTimeElapsed(), 1);
        }
        jobScheduler.onJobCompleted(0, true);
        jobStatus = jobScheduler.getJob(0);
        assertEquals(currentTimeMs + timeMaxMs, jobStatus.getEarliestRunTimeElapsed(), 1);
    }

    @Test
    public void testJobsByScheduler() {
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0).setRequiresCharging(true).build(), AlarmScheduler.TAG));
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 1).setRequiresDeviceIdle(true).build(), AlarmScheduler.TAG));
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 2).setPeriodic(TimeUnit.HOURS.toMillis(1)).build(), AlarmScheduler.TAG));
        jobScheduler.schedule(JobCreator.create(context, 3).setMinimumLatency(TimeUnit.HOURS.toMillis(1)).build());
        jobScheduler.schedule(JobCreator.create(context, 4).setRequiresCharging(true).build());
        jobScheduler.schedule(JobCreator.create(context, 5).setRequiresDeviceIdle(true).build());

        assertJobSchedulerContains(0, 1, 2, 3, 4, 5);

        assertThat(jobScheduler.getJobsByScheduler(AlarmScheduler.TAG), hasSize(3));
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.O, shadows = {ShadowGoogleApiAvailability.class})
    public void testSchedulerInApi26() {
        JobInfo api21Job = JobCreator.create(context, 0).setRequiresCharging(true).build();
        JobInfo api24Job = JobCreator.create(context, 3).setPeriodic(15 * 60 * 1000L, 5 * 60 * 1000L).build();
        JobInfo api26Job = JobCreator.create(context, 1).setRequiresBatteryNotLow(true).build();

        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SERVICE_MISSING);

        assertThat(jobScheduler.getSchedulerForJob(context, api21Job), instanceOf(JobSchedulerSchedulerV26.class));
        assertThat(jobScheduler.getSchedulerForJob(context, api24Job), instanceOf(JobSchedulerSchedulerV26.class));
        assertThat(jobScheduler.getSchedulerForJob(context, api26Job), instanceOf(JobSchedulerSchedulerV26.class));

        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SUCCESS);

        assertThat(jobScheduler.getSchedulerForJob(context, api21Job), instanceOf(JobSchedulerSchedulerV26.class));
        assertThat(jobScheduler.getSchedulerForJob(context, api24Job), instanceOf(JobSchedulerSchedulerV26.class));
        assertThat(jobScheduler.getSchedulerForJob(context, api26Job), instanceOf(JobSchedulerSchedulerV26.class));
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.N, shadows = {ShadowGoogleApiAvailability.class})
    public void testSchedulerInApi24() {
        JobInfo api21Job = JobCreator.create(context, 0).setRequiresCharging(true).build();
        JobInfo api24Job = JobCreator.create(context, 3).setPeriodic(15 * 60 * 1000L, 5 * 60 * 1000L).build();
        JobInfo api26Job = JobCreator.create(context, 1).setRequiresBatteryNotLow(true).build();

        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SERVICE_MISSING);

        assertThat(jobScheduler.getSchedulerForJob(context, api21Job), instanceOf(JobSchedulerSchedulerV24.class));
        assertThat(jobScheduler.getSchedulerForJob(context, api24Job), instanceOf(JobSchedulerSchedulerV24.class));
        assertThat(jobScheduler.getSchedulerForJob(context, api26Job), instanceOf(AlarmScheduler.class));

        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SUCCESS);

        assertThat(jobScheduler.getSchedulerForJob(context, api21Job), instanceOf(JobSchedulerSchedulerV24.class));
        assertThat(jobScheduler.getSchedulerForJob(context, api24Job), instanceOf(JobSchedulerSchedulerV24.class));
        assertThat(jobScheduler.getSchedulerForJob(context, api26Job), instanceOf(AlarmScheduler.class));
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.LOLLIPOP, shadows = {ShadowGoogleApiAvailability.class})
    public void testSchedulerInApi21() {
        JobInfo api21Job = JobCreator.create(context, 0).setRequiresCharging(true).build();
        JobInfo api24Job = JobCreator.create(context, 3).setPeriodic(15 * 60 * 1000L, 5 * 60 * 1000L).build();
        JobInfo api26Job = JobCreator.create(context, 1).setRequiresBatteryNotLow(true).build();

        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SERVICE_MISSING);

        assertThat(jobScheduler.getSchedulerForJob(context, api21Job), instanceOf(JobSchedulerSchedulerV21.class));
        assertThat(jobScheduler.getSchedulerForJob(context, api24Job), instanceOf(AlarmScheduler.class));
        assertThat(jobScheduler.getSchedulerForJob(context, api26Job), instanceOf(AlarmScheduler.class));

        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SUCCESS);

        assertThat(jobScheduler.getSchedulerForJob(context, api21Job), instanceOf(JobSchedulerSchedulerV21.class));
        assertThat(jobScheduler.getSchedulerForJob(context, api24Job), instanceOf(GcmScheduler.class));
        assertThat(jobScheduler.getSchedulerForJob(context, api26Job), instanceOf(AlarmScheduler.class));
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.KITKAT, shadows = {ShadowGoogleApiAvailability.class})
    public void testSchedulerInApi19() {
        JobInfo api21Job = JobCreator.create(context, 0).setRequiresCharging(true).build();
        JobInfo api24Job = JobCreator.create(context, 3).setPeriodic(15 * 60 * 1000L, 5 * 60 * 1000L).build();
        JobInfo api26Job = JobCreator.create(context, 1).setRequiresBatteryNotLow(true).build();

        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SERVICE_MISSING);

        assertThat(jobScheduler.getSchedulerForJob(context, api21Job), instanceOf(AlarmScheduler.class));
        assertThat(jobScheduler.getSchedulerForJob(context, api24Job), instanceOf(AlarmScheduler.class));
        assertThat(jobScheduler.getSchedulerForJob(context, api26Job), instanceOf(AlarmScheduler.class));

        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SUCCESS);

        assertThat(jobScheduler.getSchedulerForJob(context, api21Job), instanceOf(GcmScheduler.class));
        assertThat(jobScheduler.getSchedulerForJob(context, api24Job), instanceOf(GcmScheduler.class));
        assertThat(jobScheduler.getSchedulerForJob(context, api26Job), instanceOf(AlarmScheduler.class));
    }

    @Test
    public void testSchedulerForTag() {
        assertThat(jobScheduler.getSchedulerForTag(context, JobSchedulerSchedulerV26.TAG),
                   instanceOf(JobSchedulerSchedulerV26.class));
        assertThat(jobScheduler.getSchedulerForTag(context, JobSchedulerSchedulerV24.TAG),
                   instanceOf(JobSchedulerSchedulerV24.class));
        assertThat(jobScheduler.getSchedulerForTag(context, JobSchedulerSchedulerV21.TAG),
                   instanceOf(JobSchedulerSchedulerV21.class));
        assertThat(jobScheduler.getSchedulerForTag(context, GcmScheduler.TAG),
                   instanceOf(GcmScheduler.class));
        assertThat(jobScheduler.getSchedulerForTag(context, AlarmScheduler.TAG),
                   instanceOf(AlarmScheduler.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSchedulerForUnknownTagShouldFail() {
        jobScheduler.getSchedulerForTag(context, "unknown");
    }

    private void assertJobSchedulerContains(int... ids) {
        assertThat(jobScheduler.getAllPendingJobs(), hasSize(ids.length));
        assertThat(jobStore.getJobs(), hasSize(ids.length));
        for (int id : ids) {
            assertNotNull(jobScheduler.getPendingJob(id));
            assertNotNull(jobStore.getJob(id));
        }
    }
}
