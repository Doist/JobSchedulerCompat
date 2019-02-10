package com.doist.jobschedulercompat;

import android.net.Network;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;

/** @see android.app.job.JobParameters */
public class JobParameters {
    private final int jobId;
    private final PersistableBundle extras;
    private final Bundle transientExtras;
    private final Network network;
    private final Uri[] triggeredContentUris;
    private final String[] triggeredContentAuthorities;
    private final boolean overrideDeadlineExpired;

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public JobParameters(int jobId, PersistableBundle extras, Bundle transientExtras, Network network,
                         Uri[] triggeredContentUris, String[] triggeredContentAuthorities, boolean overrideDeadlineExpired) {
        this.jobId = jobId;
        this.extras = extras;
        this.transientExtras = transientExtras;
        this.network = network;
        this.triggeredContentUris = triggeredContentUris;
        this.triggeredContentAuthorities = triggeredContentAuthorities;
        this.overrideDeadlineExpired = overrideDeadlineExpired;
    }

    /** @see android.app.job.JobParameters#getJobId() */
    public int getJobId() {
        return jobId;
    }

    /** @see android.app.job.JobParameters#getExtras() */
    @NonNull
    public PersistableBundle getExtras() {
        return extras;
    }

    /** @see android.app.job.JobParameters#getTransientExtras() */
    @NonNull
    public Bundle getTransientExtras() {
        return transientExtras;
    }

    /** @see android.app.job.JobParameters#getNetwork() */
    @RequiresApi(Build.VERSION_CODES.P)
    @Nullable
    public Network getNetwork() {
        return network;
    }

    /** @see android.app.job.JobParameters#getTriggeredContentUris() */
    @Nullable
    public Uri[] getTriggeredContentUris() {
        return triggeredContentUris;
    }

    /** @see android.app.job.JobParameters#getTriggeredContentAuthorities() */
    @Nullable
    public String[] getTriggeredContentAuthorities() {
        return triggeredContentAuthorities;
    }

    /** @see android.app.job.JobParameters#isOverrideDeadlineExpired() */
    public boolean isOverrideDeadlineExpired() {
        return overrideDeadlineExpired;
    }
}
