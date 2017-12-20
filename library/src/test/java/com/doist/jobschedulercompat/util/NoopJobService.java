package com.doist.jobschedulercompat.util;

import com.doist.jobschedulercompat.JobParameters;
import com.doist.jobschedulercompat.JobService;

public class NoopJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }


}
