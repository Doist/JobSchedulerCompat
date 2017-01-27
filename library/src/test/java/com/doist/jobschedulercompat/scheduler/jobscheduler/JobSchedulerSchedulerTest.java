package com.doist.jobschedulercompat.scheduler.jobscheduler;

import com.doist.jobschedulercompat.BuildConfig;
import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.PersistableBundle;
import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.util.JobCreator;
import com.doist.jobschedulercompat.util.NoopAsyncJobService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import android.annotation.TargetApi;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@TargetApi(Build.VERSION_CODES.N)
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class JobSchedulerSchedulerTest {
    private Context context;
    private JobSchedulerScheduler scheduler;
    private JobScheduler jobScheduler;

    @Before
    public void setup() {
        context = RuntimeEnvironment.application;
        scheduler = new JobSchedulerScheduler(context, JobStore.get(context));
        jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }

    @After
    public void teardown() {
        NoopAsyncJobService.stopAll();
    }

    @Test
    public void testScheduleJob() {
        JobInfo job = createJob(0);
        scheduler.schedule(job);

        android.app.job.JobInfo nativeJob = getPendingJob(0);
        assertNotNull(nativeJob);
        assertNativeJobInfoMatchesJobInfo(nativeJob, job);

        job = createJob(1);
        scheduler.schedule(job);

        nativeJob = getPendingJob(1);
        assertNotNull(nativeJob);
        assertNativeJobInfoMatchesJobInfo(nativeJob, job);

        job = createJob(2);
        scheduler.schedule(job);

        nativeJob = getPendingJob(2);
        assertNotNull(nativeJob);
        assertNativeJobInfoMatchesJobInfo(nativeJob, job);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.LOLLIPOP)
    public void testScheduleNotRoamingJobOnLollipop() {
        scheduler.schedule(JobCreator.create(context, 0)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING).build());

        android.app.job.JobInfo nativeJob = getPendingJob(0);
        assertNotNull(nativeJob);
        assertEquals(nativeJob.getNetworkType(), android.app.job.JobInfo.NETWORK_TYPE_ANY);
    }

    @Test
    public void testCancelJob() {
        scheduler.schedule(createJob(0));
        scheduler.cancel(0);

        assertEquals(0, jobScheduler.getAllPendingJobs().size());
    }

    @Test
    public void testCancelAllBroadcasts() {
        scheduler.schedule(createJob(0));
        scheduler.schedule(createJob(1));
        scheduler.schedule(createJob(2));
        scheduler.cancelAll();

        assertEquals(0, jobScheduler.getAllPendingJobs().size());
    }

    private void assertNativeJobInfoMatchesJobInfo(android.app.job.JobInfo nativeJob, JobInfo job) {
        assertEquals(nativeJob.getId(), job.getId());
        assertEquals(new PersistableBundle(nativeJob.getExtras()).toMap(10), job.getExtras().toMap(10));
        assertEquals(nativeJob.getService(), new ComponentName(context, JobSchedulerJobService.class));
        assertEquals(nativeJob.isRequireCharging(), job.isRequireCharging());
        assertEquals(nativeJob.isRequireDeviceIdle(), job.isRequireDeviceIdle());
        assertEquals(nativeJob.getNetworkType(), job.getNetworkType());
        assertEquals(nativeJob.getMinLatencyMillis(), job.getMinLatencyMillis());
        assertEquals(nativeJob.getMaxExecutionDelayMillis(), job.getMaxExecutionDelayMillis());
        assertEquals(nativeJob.isPeriodic(), job.isPeriodic());
        assertEquals(nativeJob.isPersisted(), job.isPersisted());
        assertEquals(nativeJob.getInitialBackoffMillis(), job.getInitialBackoffMillis());
        assertEquals(nativeJob.getBackoffPolicy(), job.getBackoffPolicy());
    }

    private JobInfo createJob(int id) {
        switch (id) {
            case 0:
                return JobCreator.create(context, id, 0)
                        .setMinimumLatency(TimeUnit.HOURS.toMillis(2))
                        .setOverrideDeadline(TimeUnit.DAYS.toMillis(1))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING)
                        .setPersisted(true)
                        .build();

            case 1:
                PersistableBundle extras = new PersistableBundle();
                extras.putString("test", "test");
                return JobCreator.create(context, id, 0)
                        .setRequiresCharging(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setExtras(extras)
                        .build();

            case 2:
            default:
                return JobCreator.create(context, id, 0)
                        .setPeriodic(TimeUnit.MINUTES.toMillis(30))
                        .setRequiresDeviceIdle(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .build();
        }
    }

    /* Robolectric doesn't shadow JobScheduler#getPendingJob, so filter manually here. */
    private android.app.job.JobInfo getPendingJob(int jobId) {
        List<android.app.job.JobInfo> jobs = jobScheduler.getAllPendingJobs();
        for (android.app.job.JobInfo job : jobs) {
            if (job.getId() == jobId) {
                return job;
            }
        }
        return null;
    }
}
