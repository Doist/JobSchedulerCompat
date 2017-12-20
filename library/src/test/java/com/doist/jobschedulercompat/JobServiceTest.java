package com.doist.jobschedulercompat;

import com.doist.jobschedulercompat.util.NoopAsyncJobService;
import com.doist.jobschedulercompat.util.NoopJobService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.os.Bundle;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class JobServiceTest {
    private static final long THREAD_WAIT_MS = 100;
    private static final Object THREAD_LOCK = new Object();

    private JobParameters params =
            new JobParameters(0, PersistableBundle.EMPTY, Bundle.EMPTY, false, null, null);

    @Test
    public void testFinishesSynchronously() {
        JobService.Binder binder =
                (JobService.Binder) Robolectric.buildService(NoopJobService.class).create().get().onBind(null);

        assertFalse(binder.startJob(params, null));
    }

    @Test
    public void testFinishesAsynchronously() {
        JobService.Binder binder =
                (JobService.Binder) Robolectric.buildService(NoopAsyncJobService.class).create().get().onBind(null);

        final AtomicBoolean finished = new AtomicBoolean(false);
        assertTrue(binder.startJob(params, new JobService.Binder.Callback() {
            @Override
            public void jobFinished(JobParameters params, boolean needsReschedule) {
                finished.set(true);
                synchronized (THREAD_LOCK) {
                    THREAD_LOCK.notify();
                }
            }
        }));

        synchronized (THREAD_LOCK) {
            try {
                THREAD_LOCK.wait(THREAD_WAIT_MS);
            } catch (InterruptedException e) {
                // Do nothing.
            }
        }
        assertTrue(finished.get());
    }

    @Test
    public void testDoesntFinishIfStopped() {
        JobService.Binder binder =
                (JobService.Binder) Robolectric.buildService(NoopAsyncJobService.class).create().get().onBind(null);

        final AtomicBoolean finished = new AtomicBoolean(false);
        assertTrue(binder.startJob(params, new JobService.Binder.Callback() {
            @Override
            public void jobFinished(JobParameters params, boolean needsReschedule) {
                finished.set(true);
                synchronized (THREAD_LOCK) {
                    THREAD_LOCK.notify();
                }
            }
        }));
        binder.stopJob(params);

        synchronized (THREAD_LOCK) {
            try {
                THREAD_LOCK.wait(THREAD_WAIT_MS);
            } catch (InterruptedException e) {
                // Do nothing.
            }
        }
        assertFalse(finished.get());
    }

    @Test
    public void testMultipleFinish() {
        JobService.Binder binder =
                (JobService.Binder) Robolectric.buildService(NoopAsyncJobService.class).create().get().onBind(null);

        final AtomicInteger finished = new AtomicInteger(0);
        for (int i = 0; i < 10; i++) {
            JobParameters params = new JobParameters(
                    this.params.getJobId() + i, this.params.getExtras(), this.params.getTransientExtras(),
                    this.params.isOverrideDeadlineExpired(), this.params.getTriggeredContentUris(),
                    this.params.getTriggeredContentAuthorities());
            assertTrue(binder.startJob(params, new JobService.Binder.Callback() {
                @Override
                public void jobFinished(JobParameters params, boolean needsReschedule) {
                    finished.incrementAndGet();
                    synchronized (THREAD_LOCK) {
                        THREAD_LOCK.notify();
                    }
                }
            }));
        }

        for (int i = 0; i < 10; i++) {
            synchronized (THREAD_LOCK) {
                try {
                    THREAD_LOCK.wait(THREAD_WAIT_MS);
                } catch (InterruptedException e) {
                    // Do nothing.
                }
            }
        }
        assertEquals(10, finished.get());
    }
}
