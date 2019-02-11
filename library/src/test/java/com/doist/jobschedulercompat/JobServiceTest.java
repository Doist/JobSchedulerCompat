package com.doist.jobschedulercompat;

import com.doist.jobschedulercompat.util.NoopAsyncJobService;
import com.doist.jobschedulercompat.util.NoopJobService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import android.os.Bundle;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class JobServiceTest {
    private static final int TIMEOUT_MS = 200;
    private static final int PARALLEL_COUNT = 1000;

    private JobParameters params = new JobParameters(0, PersistableBundle.EMPTY, Bundle.EMPTY, null, null, null, false);

    @Test
    public void testFinishesSynchronously() {
        JobService.Binder binder =
                (JobService.Binder) Robolectric.buildService(NoopJobService.class).create().get().onBind(null);

        assertFalse(binder.startJob(params, null));
    }

    @Test
    public void testFinishesAsynchronously() throws InterruptedException {
        JobService.Binder binder =
                (JobService.Binder) Robolectric.buildService(NoopAsyncJobService.class).create().get().onBind(null);

        final CountDownLatch doneSignal = new CountDownLatch(1);
        assertTrue(binder.startJob(params, new JobService.Binder.Callback() {
            @Override
            public void jobFinished(JobParameters params, boolean needsReschedule) {
                doneSignal.countDown();
            }
        }));

        assertTrue(doneSignal.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testDoesntFinishIfStopped() throws InterruptedException {
        JobService.Binder binder =
                (JobService.Binder) Robolectric.buildService(NoopAsyncJobService.class).create().get().onBind(null);

        final CountDownLatch doneSignal = new CountDownLatch(1);
        final CountDownLatch continueSignal = new CountDownLatch(1);
        assertTrue(binder.startJob(params, new JobService.Binder.Callback() {
            @Override
            public void jobFinished(JobParameters params, boolean needsReschedule) {
                try {
                    continueSignal.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    doneSignal.countDown();
                } catch (InterruptedException e) {
                    // All good!
                }
            }
        }));
        binder.stopJob(params);
        continueSignal.countDown();

        assertFalse(doneSignal.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testMultipleFinish() throws InterruptedException {
        JobService.Binder binder =
                (JobService.Binder) Robolectric.buildService(NoopAsyncJobService.class).create().get().onBind(null);

        final CountDownLatch doneSignal = new CountDownLatch(PARALLEL_COUNT);
        for (int i = 0; i < PARALLEL_COUNT; i++) {
            JobParameters jobParams = new JobParameters(
                    params.getJobId() + i, params.getExtras(), params.getTransientExtras(), null,
                    params.getTriggeredContentUris(), params.getTriggeredContentAuthorities(),
                    params.isOverrideDeadlineExpired());
            assertTrue(binder.startJob(jobParams, new JobService.Binder.Callback() {
                @Override
                public void jobFinished(JobParameters params, boolean needsReschedule) {
                    doneSignal.countDown();
                }
            }));
        }

        assertTrue(doneSignal.await(TIMEOUT_MS * Math.max(1, PARALLEL_COUNT / 4), TimeUnit.MILLISECONDS));
    }
}
