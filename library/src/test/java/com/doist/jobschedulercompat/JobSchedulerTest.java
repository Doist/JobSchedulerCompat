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
import org.robolectric.annotation.Config;

import android.app.Application;
import android.os.Build;
import android.os.SystemClock;

import java.util.concurrent.TimeUnit;

import androidx.test.core.app.ApplicationProvider;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

@RunWith(RobolectricTestRunner.class)
public class JobSchedulerTest {
    private Application application;
    private JobScheduler jobScheduler;
    private JobStore jobStore;
    private Scheduler noopScheduler;

    @Before
    public void setup() {
        application = ApplicationProvider.getApplicationContext();
        jobScheduler = JobScheduler.get(application);
        jobStore = JobStore.get(application);

        noopScheduler = new NoopScheduler(application);
        jobScheduler.schedulers.put(noopScheduler.getTag(), noopScheduler);
    }

    @After
    public void teardown() {
        synchronized (JobStore.LOCK) {
            jobStore.clear();
        }
    }

    @Test
    public void testSchedule() {
        JobInfo job = JobCreator.create(application).setRequiresCharging(true).build();
        jobScheduler.schedule(job);

        assertJobSchedulerContains(job.getId());

        JobInfo job2 = JobCreator.create(application).setRequiresCharging(true).build();
        jobScheduler.schedule(job2);

        assertJobSchedulerContains(job.getId(), job2.getId());

        jobScheduler.schedule(job);

        assertJobSchedulerContains(job.getId(), job2.getId());
    }

    @Test(expected = IllegalStateException.class)
    public void testScheduleHasUpperLimit() {
        for (int i = 0; i <= JobScheduler.MAX_JOBS + 1; i++) {
            jobScheduler.schedule(
                    JobCreator.create(application, 2000).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build());
        }
    }

    @Test
    public void testCancel() {
        JobInfo job = JobCreator.create(application).setRequiresDeviceIdle(true).build();
        jobScheduler.schedule(job);
        JobInfo job2 = JobCreator.create(application).setRequiresDeviceIdle(true).build();
        jobScheduler.schedule(job2);

        assertJobSchedulerContains(job.getId(), job2.getId());

        jobScheduler.cancel(job.getId());

        assertJobSchedulerContains(job2.getId());

        jobScheduler.cancel(job2.getId());

        assertJobSchedulerContains();
    }

    @Test
    public void testCancelAll() {
        JobInfo job = JobCreator.create(application).setMinimumLatency(TimeUnit.HOURS.toMillis(1)).build();
        jobScheduler.schedule(job);
        JobInfo job2 = JobCreator.create(application).setMinimumLatency(TimeUnit.HOURS.toMillis(1)).build();
        jobScheduler.schedule(job2);
        JobInfo job3 = JobCreator.create(application).setMinimumLatency(TimeUnit.HOURS.toMillis(1)).build();
        jobScheduler.schedule(job3);

        assertJobSchedulerContains(job.getId(), job2.getId(), job3.getId());

        jobScheduler.cancelAll();

        assertJobSchedulerContains();
    }

    @Test
    public void testJobFinishedSuccess() {
        JobInfo job = JobCreator.create(application).setRequiresCharging(true).build();
        JobStatus jobStatus = JobStatus.createFromJobInfo(job, noopScheduler.getTag());
        jobStore.add(jobStatus);

        jobScheduler.onJobCompleted(job.getId(), false);
        assertJobSchedulerContains(/* Nothing. */);
    }

    @Test
    public void testPeriodicJobFinishedSuccess() {
        long timeMs = TimeUnit.MINUTES.toMillis(15);
        JobInfo job = JobCreator.create(application).setPeriodic(timeMs).build();
        JobStatus jobStatus = JobStatus.createFromJobInfo(job, noopScheduler.getTag());
        jobStore.add(jobStatus);
        Robolectric.getForegroundThreadScheduler().advanceBy(timeMs, TimeUnit.MILLISECONDS);

        jobScheduler.onJobCompleted(job.getId(), false);
        JobStatus newJobStatus = jobScheduler.getJob(job.getId());
        assertEquals(jobStatus.getEarliestRunTimeElapsed() + timeMs, newJobStatus.getEarliestRunTimeElapsed());
        assertEquals(jobStatus.getLatestRunTimeElapsed() + timeMs, newJobStatus.getLatestRunTimeElapsed());
    }

    @Test
    public void testPeriodicJobFinishedFailure() {
        long currentTimeMs = SystemClock.elapsedRealtime();
        long timeMs = TimeUnit.MINUTES.toMillis(15);
        long timeMaxMs = TimeUnit.HOURS.toMillis(5);

        JobInfo job = JobCreator.create(application)
                                .setPeriodic(timeMs)
                                .setBackoffCriteria(timeMs, JobInfo.BACKOFF_POLICY_LINEAR)
                                .build();
        JobStatus jobStatus = JobStatus.createFromJobInfo(job, noopScheduler.getTag());
        jobStore.add(jobStatus);

        // Fail until it reaches 5 hours.
        for (int i = 0; i < timeMaxMs / timeMs; i++) {
            jobScheduler.onJobCompleted(job.getId(), true);
            jobStatus = jobScheduler.getJob(job.getId());
            assertEquals(currentTimeMs + timeMs * (i + 1), jobStatus.getEarliestRunTimeElapsed());
        }
        jobScheduler.onJobCompleted(job.getId(), true);
        jobStatus = jobStore.getJob(job.getId());
        assertEquals(currentTimeMs + timeMaxMs, jobStatus.getEarliestRunTimeElapsed());
    }

    @Test
    public void testJobFinishedFailureExponential() {
        long currentTimeMs = SystemClock.elapsedRealtime();
        long timeMs = TimeUnit.MINUTES.toMillis(15);
        long timeMaxMs = TimeUnit.HOURS.toMillis(5);

        JobInfo job = JobCreator.create(application)
                                .setRequiresCharging(true)
                                .setBackoffCriteria(timeMs, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                                .build();
        JobStatus jobStatus = JobStatus.createFromJobInfo(job, noopScheduler.getTag());
        jobStore.add(jobStatus);

        // Fail until we reach five hours.
        for (int i = 0; i < Math.sqrt(timeMaxMs) / Math.sqrt(timeMs); i++) {
            jobScheduler.onJobCompleted(job.getId(), true);
            jobStatus = jobScheduler.getJob(job.getId());
            assertEquals(currentTimeMs + timeMs * Math.pow(2, i), jobStatus.getEarliestRunTimeElapsed(), 1);
        }
        jobScheduler.onJobCompleted(job.getId(), true);
        jobStatus = jobScheduler.getJob(job.getId());
        assertEquals(currentTimeMs + timeMaxMs, jobStatus.getEarliestRunTimeElapsed(), 1);
    }

    @Test
    public void testJobsByScheduler() {
        JobInfo job = JobCreator.create(application).setRequiresCharging(true).build();
        jobStore.add(JobStatus.createFromJobInfo(job, AlarmScheduler.TAG));
        JobInfo job2 = JobCreator.create(application).setRequiresDeviceIdle(true).build();
        jobStore.add(JobStatus.createFromJobInfo(job2, AlarmScheduler.TAG));
        JobInfo job3 = JobCreator.create(application).setPeriodic(TimeUnit.HOURS.toMillis(1)).build();
        jobStore.add(JobStatus.createFromJobInfo(job3, AlarmScheduler.TAG));
        JobInfo job4 = JobCreator.create(application).setMinimumLatency(TimeUnit.HOURS.toMillis(1)).build();
        jobScheduler.schedule(job4);
        JobInfo job5 = JobCreator.create(application).setRequiresCharging(true).build();
        jobScheduler.schedule(job5);
        JobInfo job6 = JobCreator.create(application).setRequiresDeviceIdle(true).build();
        jobScheduler.schedule(job6);

        assertJobSchedulerContains(job.getId(), job2.getId(), job3.getId(), job4.getId(), job5.getId(), job6.getId());

        assertThat(jobScheduler.getJobsByScheduler(AlarmScheduler.TAG), hasSize(3));
    }

    @Test
    @Config(sdk = {Build.VERSION_CODES.O, Build.VERSION_CODES.P}, shadows = {ShadowGoogleApiAvailability.class})
    public void testSchedulerInApi26() {
        JobInfo api21Job = JobCreator.create(application).setRequiresCharging(true).build();
        JobInfo api24Job = JobCreator.create(application).setPeriodic(15 * 60 * 1000L, 5 * 60 * 1000L).build();
        JobInfo api26Job = JobCreator.create(application).setRequiresBatteryNotLow(true).build();

        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SERVICE_MISSING);

        assertThat(jobScheduler.getSchedulerForJob(application, api21Job), instanceOf(JobSchedulerSchedulerV26.class));
        assertThat(jobScheduler.getSchedulerForJob(application, api24Job), instanceOf(JobSchedulerSchedulerV26.class));
        assertThat(jobScheduler.getSchedulerForJob(application, api26Job), instanceOf(JobSchedulerSchedulerV26.class));

        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SUCCESS);

        assertThat(jobScheduler.getSchedulerForJob(application, api21Job), instanceOf(JobSchedulerSchedulerV26.class));
        assertThat(jobScheduler.getSchedulerForJob(application, api24Job), instanceOf(JobSchedulerSchedulerV26.class));
        assertThat(jobScheduler.getSchedulerForJob(application, api26Job), instanceOf(JobSchedulerSchedulerV26.class));
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.N, shadows = {ShadowGoogleApiAvailability.class})
    public void testSchedulerInApi24() {
        JobInfo api21Job = JobCreator.create(application).setRequiresCharging(true).build();
        JobInfo api24Job = JobCreator.create(application).setPeriodic(15 * 60 * 1000L, 5 * 60 * 1000L).build();
        JobInfo api26Job = JobCreator.create(application).setRequiresBatteryNotLow(true).build();

        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SERVICE_MISSING);

        assertThat(jobScheduler.getSchedulerForJob(application, api21Job), instanceOf(JobSchedulerSchedulerV24.class));
        assertThat(jobScheduler.getSchedulerForJob(application, api24Job), instanceOf(JobSchedulerSchedulerV24.class));
        assertThat(jobScheduler.getSchedulerForJob(application, api26Job), instanceOf(AlarmScheduler.class));

        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SUCCESS);

        assertThat(jobScheduler.getSchedulerForJob(application, api21Job), instanceOf(JobSchedulerSchedulerV24.class));
        assertThat(jobScheduler.getSchedulerForJob(application, api24Job), instanceOf(JobSchedulerSchedulerV24.class));
        assertThat(jobScheduler.getSchedulerForJob(application, api26Job), instanceOf(AlarmScheduler.class));
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.LOLLIPOP, shadows = {ShadowGoogleApiAvailability.class})
    public void testSchedulerInApi21() {
        JobInfo api21Job = JobCreator.create(application).setRequiresCharging(true).build();
        JobInfo api24Job = JobCreator.create(application).setPeriodic(15 * 60 * 1000L, 5 * 60 * 1000L).build();
        JobInfo api26Job = JobCreator.create(application).setRequiresBatteryNotLow(true).build();

        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SERVICE_MISSING);

        assertThat(jobScheduler.getSchedulerForJob(application, api21Job), instanceOf(JobSchedulerSchedulerV21.class));
        assertThat(jobScheduler.getSchedulerForJob(application, api24Job), instanceOf(AlarmScheduler.class));
        assertThat(jobScheduler.getSchedulerForJob(application, api26Job), instanceOf(AlarmScheduler.class));

        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SUCCESS);

        assertThat(jobScheduler.getSchedulerForJob(application, api21Job), instanceOf(JobSchedulerSchedulerV21.class));
        assertThat(jobScheduler.getSchedulerForJob(application, api24Job), instanceOf(GcmScheduler.class));
        assertThat(jobScheduler.getSchedulerForJob(application, api26Job), instanceOf(AlarmScheduler.class));
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.KITKAT, shadows = {ShadowGoogleApiAvailability.class})
    public void testSchedulerInApi19() {
        JobInfo api21Job = JobCreator.create(application).setRequiresCharging(true).build();
        JobInfo api24Job = JobCreator.create(application).setPeriodic(15 * 60 * 1000L, 5 * 60 * 1000L).build();
        JobInfo api26Job = JobCreator.create(application).setRequiresBatteryNotLow(true).build();

        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SERVICE_MISSING);

        assertThat(jobScheduler.getSchedulerForJob(application, api21Job), instanceOf(AlarmScheduler.class));
        assertThat(jobScheduler.getSchedulerForJob(application, api24Job), instanceOf(AlarmScheduler.class));
        assertThat(jobScheduler.getSchedulerForJob(application, api26Job), instanceOf(AlarmScheduler.class));

        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SUCCESS);

        assertThat(jobScheduler.getSchedulerForJob(application, api21Job), instanceOf(GcmScheduler.class));
        assertThat(jobScheduler.getSchedulerForJob(application, api24Job), instanceOf(GcmScheduler.class));
        assertThat(jobScheduler.getSchedulerForJob(application, api26Job), instanceOf(AlarmScheduler.class));
    }

    @Test
    public void testSchedulerForTag() {
        assertThat(jobScheduler.getSchedulerForTag(application, JobSchedulerSchedulerV26.TAG),
                   instanceOf(JobSchedulerSchedulerV26.class));
        assertThat(jobScheduler.getSchedulerForTag(application, JobSchedulerSchedulerV24.TAG),
                   instanceOf(JobSchedulerSchedulerV24.class));
        assertThat(jobScheduler.getSchedulerForTag(application, JobSchedulerSchedulerV21.TAG),
                   instanceOf(JobSchedulerSchedulerV21.class));
        assertThat(jobScheduler.getSchedulerForTag(application, GcmScheduler.TAG),
                   instanceOf(GcmScheduler.class));
        assertThat(jobScheduler.getSchedulerForTag(application, AlarmScheduler.TAG),
                   instanceOf(AlarmScheduler.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSchedulerForUnknownTagShouldFail() {
        jobScheduler.getSchedulerForTag(application, "unknown");
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
