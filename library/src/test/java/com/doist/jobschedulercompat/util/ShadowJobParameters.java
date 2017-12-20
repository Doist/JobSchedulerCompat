package com.doist.jobschedulercompat.util;

import com.doist.jobschedulercompat.job.JobStatus;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;

import android.app.job.JobParameters;
import android.os.PersistableBundle;

@Implements(JobParameters.class)
public class ShadowJobParameters {
    private JobStatus jobStatus;

    public static JobParameters newInstance(JobStatus jobStatus) {
        JobParameters jobParameters = Shadow.newInstanceOf(JobParameters.class);
        ShadowJobParameters shadowJobParameters = Shadow.extract(jobParameters);
        shadowJobParameters.setJobStatus(jobStatus);
        return jobParameters;
    }

    public ShadowJobParameters() {
    }

    private void setJobStatus(JobStatus jobStatus) {
        this.jobStatus = jobStatus;
    }

    @Implementation
    public int getJobId() {
        return jobStatus.getJobId();
    }

    @Implementation
    public PersistableBundle getExtras() {
        return jobStatus.getJob().getExtras().toPersistableBundle();
    }
}
