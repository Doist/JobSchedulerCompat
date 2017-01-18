package com.doist.jobschedulercompat.scheduler.alarm;

import com.doist.jobschedulercompat.BuildConfig;
import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.job.JobStatus;
import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.util.DeviceTestUtils;
import com.doist.jobschedulercompat.util.JobCreator;
import com.doist.jobschedulercompat.util.ShadowNetworkInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.util.ServiceController;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;

import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = Build.VERSION_CODES.KITKAT, shadows = {ShadowNetworkInfo.class})
public class AlarmReceiverTest {
    private Context context;
    private PackageManager packageManager;
    private JobStore jobStore;
    private ServiceController<AlarmJobService> service;

    @Before
    public void setup() {
        context = RuntimeEnvironment.application;
        packageManager = context.getPackageManager();
        jobStore = JobStore.get(context);
        service = Robolectric.buildService(AlarmJobService.class).create();
    }

    @Test
    public void testBaseReceiverLifecycle() {
        ComponentName receiver = new ComponentName(context, AlarmReceiver.class);

        assertFalse(DeviceTestUtils.isComponentEnabled(packageManager, receiver));

        service.startCommand(0, 0);

        assertFalse(DeviceTestUtils.isComponentEnabled(packageManager, receiver));

        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0).setMinimumLatency(TimeUnit.HOURS.toMillis(1)).build(),
                AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertTrue(DeviceTestUtils.isComponentEnabled(packageManager, receiver));

        jobStore.remove(0);
        service.startCommand(0, 0);

        assertFalse(DeviceTestUtils.isComponentEnabled(packageManager, receiver));
    }

    @Test
    public void testChargingReceiverLifecycle() {
        ComponentName receiver = new ComponentName(context, AlarmReceiver.BatteryReceiver.class);

        assertFalse(DeviceTestUtils.isComponentEnabled(packageManager, receiver));

        DeviceTestUtils.setCharging(context, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0).setRequiresCharging(true).build(), AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertTrue(DeviceTestUtils.isComponentEnabled(packageManager, receiver));

        DeviceTestUtils.setCharging(context, true);
        service.startCommand(0, 0);

        assertFalse(DeviceTestUtils.isComponentEnabled(packageManager, receiver));
    }

    @Test
    public void testConnectivityReceiverLifecycle() {
        ComponentName receiver = new ComponentName(context, AlarmReceiver.ConnectivityReceiver.class);

        assertFalse(DeviceTestUtils.isComponentEnabled(packageManager, receiver));

        DeviceTestUtils.setNetworkInfo(context, false, false, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build(),
                AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertTrue(DeviceTestUtils.isComponentEnabled(packageManager, receiver));

        DeviceTestUtils.setNetworkInfo(context, true, false, false);
        service.startCommand(0, 0);

        assertFalse(DeviceTestUtils.isComponentEnabled(packageManager, receiver));
    }

    @Test
    public void testReceiversStartService() {
        new AlarmReceiver().onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));

        assertEquals(ShadowApplication.getInstance().getNextStartedService().getComponent().getClassName(),
                     AlarmJobService.class.getName());

        new AlarmReceiver.BatteryReceiver().onReceive(context, new Intent(Intent.ACTION_POWER_CONNECTED));

        assertEquals(ShadowApplication.getInstance().getNextStartedService().getComponent().getClassName(),
                     AlarmJobService.class.getName());

        new AlarmReceiver.ConnectivityReceiver().onReceive(
                context, new Intent(ConnectivityManager.CONNECTIVITY_ACTION));

        assertEquals(ShadowApplication.getInstance().getNextStartedService().getComponent().getClassName(),
                     AlarmJobService.class.getName());
    }
}
