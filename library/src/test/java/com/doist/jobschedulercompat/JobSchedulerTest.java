package com.doist.jobschedulercompat;

import com.google.android.gms.common.ConnectionResult;

import com.doist.jobschedulercompat.job.JobStatus;
import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.scheduler.alarm.AlarmScheduler;
import com.doist.jobschedulercompat.scheduler.gcm.GcmScheduler;
import com.doist.jobschedulercompat.scheduler.jobscheduler.JobSchedulerScheduler;
import com.doist.jobschedulercompat.util.JobCreator;
import com.doist.jobschedulercompat.util.ShadowGoogleApiAvailability;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import android.content.Context;
import android.os.Build;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class JobSchedulerTest {
    private Context context;
    private JobScheduler jobScheduler;
    private JobStore jobStore;

    @Before
    public void setup() {
        context = RuntimeEnvironment.application;
        jobScheduler = JobScheduler.get(context);
        jobStore = JobStore.get(context);
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
    public void testSchedulerChange() {
        JobStatus jobStatus = JobStatus.createFromJobInfo(
                JobCreator.create(context, 0).setPeriodic(TimeUnit.HOURS.toMillis(1)).build(), AlarmScheduler.TAG);
        jobStore.add(jobStatus);

        assertJobSchedulerContains(0);

        jobScheduler.onJobCompleted(0, true);

        jobStatus = jobScheduler.getJob(0);
        assertThat(jobStatus.getScheduler(), not(AlarmScheduler.TAG));
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
    @Config(sdk = Build.VERSION_CODES.KITKAT, shadows = {ShadowGoogleApiAvailability.class})
    public void testBestSchedulerForKitkat() {
        assertThat(jobScheduler.getBestScheduler(context, jobStore), instanceOf(AlarmScheduler.class));

        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SUCCESS);

        assertThat(jobScheduler.getBestScheduler(context, jobStore), instanceOf(GcmScheduler.class));
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.LOLLIPOP, shadows = {ShadowGoogleApiAvailability.class})
    public void testBestSchedulerForLollipop() {
        assertThat(jobScheduler.getBestScheduler(context, jobStore), instanceOf(JobSchedulerScheduler.class));

        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SUCCESS);

        assertThat(jobScheduler.getBestScheduler(context, jobStore), instanceOf(JobSchedulerScheduler.class));
    }

    @Test
    public void testSchedulerForTag() {
        assertThat(jobScheduler.getSchedulerForTag(JobSchedulerScheduler.TAG, context, jobStore),
                   instanceOf(JobSchedulerScheduler.class));
        assertThat(jobScheduler.getSchedulerForTag(GcmScheduler.TAG, context, jobStore),
                   instanceOf(GcmScheduler.class));
        assertThat(jobScheduler.getSchedulerForTag(AlarmScheduler.TAG, context, jobStore),
                   instanceOf(AlarmScheduler.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSchedulerForUnknownTagShouldFail() {
        jobScheduler.getSchedulerForTag("noop", context, jobStore);
    }

    private void assertJobSchedulerContains(int... ids) {
        assertThat(jobScheduler.getAllPendingJobs(), hasSize(ids.length));
        for (int id : ids) {
            assertNotNull(jobScheduler.getPendingJob(id));
        }
    }
}
