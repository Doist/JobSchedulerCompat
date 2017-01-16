package com.doist.jobschedulercompat;

import android.content.ComponentName;
import android.support.annotation.RestrictTo;
import android.util.Log;

/** @see android.app.job.JobInfo */
public class JobInfo {
    private static final String LOG_TAG = "JobInfoCompat";

    /** @see android.app.job.JobInfo#NETWORK_TYPE_NONE */
    public static final int NETWORK_TYPE_NONE = 0;
    /** @see android.app.job.JobInfo#NETWORK_TYPE_ANY */
    public static final int NETWORK_TYPE_ANY = 1;
    /** @see android.app.job.JobInfo#NETWORK_TYPE_UNMETERED */
    public static final int NETWORK_TYPE_UNMETERED = 2;
    /** @see android.app.job.JobInfo#NETWORK_TYPE_NOT_ROAMING */
    public static final int NETWORK_TYPE_NOT_ROAMING = 3;

    /** @see android.app.job.JobInfo#DEFAULT_INITIAL_BACKOFF_MILLIS */
    public static final long DEFAULT_INITIAL_BACKOFF_MILLIS = 30000L;  // 30 seconds.

    /** @see android.app.job.JobInfo#MAX_BACKOFF_DELAY_MILLIS */
    public static final long MAX_BACKOFF_DELAY_MILLIS = 5 * 60 * 60 * 1000;  // 5 hours.

    /** @see android.app.job.JobInfo#BACKOFF_POLICY_LINEAR */
    public static final int BACKOFF_POLICY_LINEAR = 0;

    /** @see android.app.job.JobInfo#BACKOFF_POLICY_EXPONENTIAL */
    public static final int BACKOFF_POLICY_EXPONENTIAL = 1;

    /** Same as android.app.job.JobInfo#DEFAULT_BACKOFF_POLICY. */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static final int DEFAULT_BACKOFF_POLICY = BACKOFF_POLICY_EXPONENTIAL;

    private static final long MIN_PERIOD_MILLIS = 15 * 60 * 1000L; // 15 minutes.

    /** @see android.app.job.JobInfo#getMinPeriodMillis() */
    public static final long getMinPeriodMillis() {
        return MIN_PERIOD_MILLIS;
    }

    private final int jobId;
    private final PersistableBundle extras;
    private final ComponentName service;
    private final boolean requireCharging;
    private final boolean requireDeviceIdle;
    private final int networkType;
    private final long minLatencyMillis;
    private final long maxExecutionDelayMillis;
    private final boolean isPeriodic;
    private final boolean hasEarlyConstraint;
    private final boolean hasLateConstraint;
    private final boolean isPersisted;
    private final long intervalMillis;
    private final long initialBackoffMillis;
    private final int backoffPolicy;

    private JobInfo(int jobId, PersistableBundle extras, ComponentName service, boolean requireCharging,
                    boolean requireDeviceIdle, int networkType, long minLatencyMillis, long maxExecutionDelayMillis,
                    boolean isPeriodic, boolean hasEarlyConstraint, boolean hasLateConstraint, boolean isPersisted,
                    long intervalMillis, long initialBackoffMillis, int backoffPolicy) {
        this.jobId = jobId;
        this.extras = extras;
        this.service = service;
        this.requireCharging = requireCharging;
        this.requireDeviceIdle = requireDeviceIdle;
        this.networkType = networkType;
        this.minLatencyMillis = minLatencyMillis;
        this.maxExecutionDelayMillis = maxExecutionDelayMillis;
        this.isPeriodic = isPeriodic;
        this.hasEarlyConstraint = hasEarlyConstraint;
        this.hasLateConstraint = hasLateConstraint;
        this.isPersisted = isPersisted;
        this.intervalMillis = intervalMillis;
        this.initialBackoffMillis = initialBackoffMillis;
        this.backoffPolicy = backoffPolicy;
    }

    /** @see android.app.job.JobInfo#getId() */
    public int getId() {
        return jobId;
    }

    /** @see android.app.job.JobInfo#getExtras() */
    public PersistableBundle getExtras() {
        return extras;
    }

    /** @see android.app.job.JobInfo#getService() */
    public ComponentName getService() {
        return service;
    }

    /** @see android.app.job.JobInfo#isRequireCharging() */
    public boolean isRequireCharging() {
        return requireCharging;
    }

    /** @see android.app.job.JobInfo#isRequireDeviceIdle() */
    public boolean isRequireDeviceIdle() {
        return requireDeviceIdle;
    }

    /** @see android.app.job.JobInfo#getNetworkType() */
    public int getNetworkType() {
        return networkType;
    }

    /** @see android.app.job.JobInfo#getMinLatencyMillis() */
    public long getMinLatencyMillis() {
        return minLatencyMillis;
    }

    /** @see android.app.job.JobInfo#getMaxExecutionDelayMillis() */
    public long getMaxExecutionDelayMillis() {
        return maxExecutionDelayMillis;
    }

    /** @see android.app.job.JobInfo#isPeriodic() */
    public boolean isPeriodic() {
        return isPeriodic;
    }

    /** @see android.app.job.JobInfo#isPersisted() */
    public boolean isPersisted() {
        return isPersisted;
    }

    /** @see android.app.job.JobInfo#getIntervalMillis() */
    public long getIntervalMillis() {
        return intervalMillis >= getMinPeriodMillis() ? intervalMillis : getMinPeriodMillis();
    }

    /** @see android.app.job.JobInfo#getInitialBackoffMillis() */
    public long getInitialBackoffMillis() {
        return initialBackoffMillis;
    }

    /** @see android.app.job.JobInfo#getBackoffPolicy() */
    public int getBackoffPolicy() {
        return backoffPolicy;
    }

    /** Same as android.app.job.JobInfo#hasEarlyConstraint(). */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public boolean hasEarlyConstraint() {
        return hasEarlyConstraint;
    }

    /** Same as android.app.job.JobInfo#hasLateConstraint(). */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public boolean hasLateConstraint() {
        return hasLateConstraint;
    }

    /** @see android.app.job.JobInfo.Builder */
    public static final class Builder {
        private final int jobId;
        private final ComponentName service;
        private PersistableBundle extras = PersistableBundle.EMPTY;
        // Requirements.
        private boolean requiresCharging;
        private boolean requiresDeviceIdle;
        private int networkType;
        private boolean isPersisted;
        // One-off parameters.
        private long minLatencyMillis;
        private long maxExecutionDelayMillis;
        // Periodic parameters.
        private boolean isPeriodic;
        private boolean hasEarlyConstraint;
        private boolean hasLateConstraint;
        private long intervalMillis;
        // Back-off parameters.
        private long initialBackoffMillis = DEFAULT_INITIAL_BACKOFF_MILLIS;
        private int backoffPolicy = DEFAULT_BACKOFF_POLICY;
        private boolean backoffPolicySet = false;

        /** @see android.app.job.JobInfo.Builder#Builder(int, ComponentName) */
        public Builder(int jobId, ComponentName service) {
            this.service = service;
            this.jobId = jobId;
        }

        /** @see android.app.job.JobInfo.Builder#setExtras(android.os.PersistableBundle) */
        public JobInfo.Builder setExtras(PersistableBundle extras) {
            this.extras = extras;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setRequiredNetworkType(int) */
        public JobInfo.Builder setRequiredNetworkType(int networkType) {
            this.networkType = networkType;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setRequiresCharging(boolean) */
        public JobInfo.Builder setRequiresCharging(boolean requiresCharging) {
            this.requiresCharging = requiresCharging;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setRequiresDeviceIdle(boolean) */
        public JobInfo.Builder setRequiresDeviceIdle(boolean requiresDeviceIdle) {
            this.requiresDeviceIdle = requiresDeviceIdle;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setPeriodic(long) */
        public JobInfo.Builder setPeriodic(long intervalMillis) {
            isPeriodic = true;
            this.intervalMillis = intervalMillis;
            hasEarlyConstraint = hasLateConstraint = true;
            return this;
        }

        /** {@see android.app.job.JobInfo.Builder#setMinimumLatency(long) */
        public JobInfo.Builder setMinimumLatency(long minLatencyMillis) {
            this.minLatencyMillis = minLatencyMillis;
            hasEarlyConstraint = true;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setOverrideDeadline(long) */
        public JobInfo.Builder setOverrideDeadline(long maxExecutionDelayMillis) {
            this.maxExecutionDelayMillis = maxExecutionDelayMillis;
            hasLateConstraint = true;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setBackoffCriteria(long, int) */
        public JobInfo.Builder setBackoffCriteria(long initialBackoffMillis, int backoffPolicy) {
            this.initialBackoffMillis = initialBackoffMillis;
            this.backoffPolicy = backoffPolicy;
            backoffPolicySet = true;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setPersisted(boolean) */
        public JobInfo.Builder setPersisted(boolean isPersisted) {
            this.isPersisted = isPersisted;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#build() */
        public JobInfo build() {
            // Don't allow jobs without a service.
            if (service == null) {
                throw new IllegalArgumentException(
                        "You're trying to build a job without a service, this is not allowed.");
            }

            // Don't allow jobs with no constraints.
            if (!hasEarlyConstraint && !hasLateConstraint && !requiresCharging && !requiresDeviceIdle
                    && networkType == NETWORK_TYPE_NONE) {
                throw new IllegalArgumentException(
                        "You're trying to build a job without constraints, this is not allowed.");
            }

            // Check that latency or deadlines were not set on a periodic job.
            if (isPeriodic && (maxExecutionDelayMillis != 0L)) {
                throw new IllegalArgumentException("Can't call setOverrideDeadline() on a periodic job.");
            }
            if (isPeriodic && (minLatencyMillis != 0L)) {
                throw new IllegalArgumentException("Can't call setMinimumLatency() on a periodic job");
            }

            if (backoffPolicySet && requiresDeviceIdle) {
                throw new IllegalArgumentException(
                        "An idle mode job will not respect any back-off policy, so calling setBackoffCriteria with"
                                + " setRequiresDeviceIdle is an error.");
            }

            // Make our own copy.
            extras = new PersistableBundle(extras);

            JobInfo job = new JobInfo(
                    jobId, extras, service, requiresCharging, requiresDeviceIdle, networkType, minLatencyMillis,
                    maxExecutionDelayMillis, isPeriodic, hasEarlyConstraint, hasLateConstraint, isPersisted,
                    intervalMillis, initialBackoffMillis, backoffPolicy);

            if (job.isPeriodic()) {
                if (job.intervalMillis != job.getIntervalMillis()) {
                    Log.w(LOG_TAG, "Specified interval for " + jobId + " is " + intervalMillis + "ms."
                            + " Clamped to " + job.getIntervalMillis() + "ms");
                }
            }
            return job;
        }
    }
}
