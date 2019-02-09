package com.doist.jobschedulercompat.scheduler.alarm;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.job.JobStatus;
import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.util.DeviceTestUtils;
import com.doist.jobschedulercompat.util.JobCreator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

import android.app.Application;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.KITKAT)
public class AlarmContentObserverServiceTest {

    private Application application;
    private JobStore jobStore;
    private ServiceController<ContentObserverService> service;

    @Before
    public void setup() {
        application = ApplicationProvider.getApplicationContext();
        jobStore = JobStore.get(application);
        service = Robolectric.buildService(ContentObserverService.class).create();
    }

    @After
    public void teardown() {
        JobCreator.interruptJobs();
        jobStore.clear();
    }

    @Test
    public void testObserversRegistered() {
        Uri uri = Uri.parse("doist.com");

        ContentResolver contentResolver = application.getContentResolver();
        assertEquals(0, shadowOf(contentResolver).getContentObservers(uri).size());

        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application, 0)
                          .addTriggerContentUri(new JobInfo.TriggerContentUri(uri, 0))
                          .build(),
                AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertEquals(1, shadowOf(contentResolver).getContentObservers(uri).size());
        assertThat(shadowOf(contentResolver).getContentObservers(uri), hasItem(isA(ContentObserverService.Observer.class)));
    }

    @Test
    public void testObserversFire() {
        Uri[] uris = new Uri[]{Uri.parse("doist.com"), Uri.parse("todoist.com"), Uri.parse("twistapp.com")};

        for (int i = 0; i < uris.length; i++) {
            jobStore.add(JobStatus.createFromJobInfo(
                    JobCreator.create(application, i, 5000)
                              .addTriggerContentUri(new JobInfo.TriggerContentUri(uris[i], 0))
                              .build(),
                    AlarmScheduler.TAG));
        }
        service.startCommand(0, 0);

        ContentResolver contentResolver = application.getContentResolver();
        for (Uri uri : uris) {
            assertEquals(0, shadowOf(application).getBoundServiceConnections().size());
            assertEquals(1, shadowOf(contentResolver).getContentObservers(uri).size());
            contentResolver.notifyChange(uri, null);
            DeviceTestUtils.advanceTime(JobStatus.DEFAULT_TRIGGER_MAX_DELAY);
            assertEquals(1, shadowOf(contentResolver).getContentObservers(uri).size());
            assertEquals(AlarmJobService.class.getCanonicalName(),
                         shadowOf(application).getNextStartedService().getComponent().getClassName());
        }
    }
}
