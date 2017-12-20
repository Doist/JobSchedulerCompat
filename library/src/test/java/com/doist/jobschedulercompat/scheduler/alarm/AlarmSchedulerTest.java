package com.doist.jobschedulercompat.scheduler.alarm;

import com.doist.jobschedulercompat.BuildConfig;
import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.job.JobStatus;
import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.util.JobCreator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import java.util.concurrent.TimeUnit;

import edu.emory.mathcs.backport.java.util.Collections;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = Build.VERSION_CODES.KITKAT)
public class AlarmSchedulerTest {
    private JobInfo job;
    private JobStore jobStore;
    private AlarmScheduler scheduler;

    @Before
    public void setup() {
        Context context = RuntimeEnvironment.application;
        job = JobCreator.create(context, 0, 5000)
                        .addTriggerContentUri(new JobInfo.TriggerContentUri(Uri.parse("doist.com"), 0))
                        .setMinimumLatency(TimeUnit.HOURS.toMillis(1) /* Random constraint. */)
                        .build();
        jobStore = JobStore.get(context);
        scheduler = new AlarmScheduler(context);
    }

    @After
    public void teardown() {
        jobStore.clear();
    }

    @Test
    public void testScheduleRunsService() {
        scheduler.schedule(job);

        assertEquals(ShadowApplication.getInstance().getNextStartedService().getComponent().getClassName(),
                     AlarmJobService.class.getName());
    }

    @Test
    public void testCancelRunsService() {
        scheduler.cancel(0);

        assertEquals(ShadowApplication.getInstance().getNextStartedService().getComponent().getClassName(),
                     AlarmJobService.class.getName());
    }

    @Test
    public void testCancelAllRunsService() {
        scheduler.cancelAll();

        assertEquals(ShadowApplication.getInstance().getNextStartedService().getComponent().getClassName(),
                     AlarmJobService.class.getName());
    }

    @Test
    public void testJobFinishedRunsService() {
        scheduler.onJobCompleted(0, false);

        assertEquals(ShadowApplication.getInstance().getNextStartedService().getComponent().getClassName(),
                     AlarmJobService.class.getName());

        scheduler.onJobCompleted(0, true);

        assertEquals(ShadowApplication.getInstance().getNextStartedService().getComponent().getClassName(),
                     AlarmJobService.class.getName());
    }

    @Test
    public void testJobRescheduledPassesUriAuthorityForward() {
        Uri changedUri = job.getTriggerContentUris()[0].getUri();
        String changedAuthority = changedUri.getAuthority();

        JobStatus failedJobStatus = new JobStatus(job, AlarmScheduler.TAG, 0, 0);
        failedJobStatus.changedUris = Collections.singleton(changedUri);
        failedJobStatus.changedAuthorities = Collections.singleton(changedAuthority);
        JobStatus newJobStatus = new JobStatus(job, AlarmScheduler.TAG, 0, 0);
        scheduler.onJobRescheduled(newJobStatus, failedJobStatus);

        assertThat(newJobStatus.changedUris, hasItem(changedUri));
        assertThat(newJobStatus.changedAuthorities, hasItem(changedAuthority));
    }
}
