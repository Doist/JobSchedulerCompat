package com.doist.jobschedulercompat;

import android.content.ComponentName;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/** @see android.app.job.JobInfo */
public class JobInfo {
    private static final String LOG_TAG = "JobInfoCompat";

    @IntDef({
            NETWORK_TYPE_NONE,
            NETWORK_TYPE_ANY,
            NETWORK_TYPE_UNMETERED,
            NETWORK_TYPE_NOT_ROAMING,
            NETWORK_TYPE_METERED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface NetworkType {
    }

    /** @see android.app.job.JobInfo#NETWORK_TYPE_NONE */
    public static final int NETWORK_TYPE_NONE = 0;
    /** @see android.app.job.JobInfo#NETWORK_TYPE_ANY */
    public static final int NETWORK_TYPE_ANY = 1;
    /** @see android.app.job.JobInfo#NETWORK_TYPE_UNMETERED */
    public static final int NETWORK_TYPE_UNMETERED = 2;
    /** @see android.app.job.JobInfo#NETWORK_TYPE_NOT_ROAMING */
    public static final int NETWORK_TYPE_NOT_ROAMING = 3;
    /** @see android.app.job.JobInfo#NETWORK_TYPE_METERED */
    public static final int NETWORK_TYPE_METERED = 4;

    /** @see android.app.job.JobInfo#DEFAULT_INITIAL_BACKOFF_MILLIS */
    public static final long DEFAULT_INITIAL_BACKOFF_MILLIS = 30000L;  // 30 seconds.

    /** @see android.app.job.JobInfo#MAX_BACKOFF_DELAY_MILLIS */
    public static final long MAX_BACKOFF_DELAY_MILLIS = 5 * 60 * 60 * 1000;  // 5 hours.

    @IntDef({
            BACKOFF_POLICY_LINEAR,
            BACKOFF_POLICY_EXPONENTIAL
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface BackoffPolicy {
    }

    /** @see android.app.job.JobInfo#BACKOFF_POLICY_LINEAR */
    public static final int BACKOFF_POLICY_LINEAR = 0;

    /** @see android.app.job.JobInfo#BACKOFF_POLICY_EXPONENTIAL */
    public static final int BACKOFF_POLICY_EXPONENTIAL = 1;

    /** Same as android.app.job.JobInfo#MIN_PERIOD_MILLIS */
    private static final long MIN_PERIOD_MILLIS = 15 * 60 * 1000L; // 15 minutes.

    /** Same as android.app.job.JobInfo#MIN_FLEX_MILLIS */
    private static final long MIN_FLEX_MILLIS = 5 * 60 * 1000L; // 5 minutes.

    /** Same as android.app.job.JobInfo#MIN_BACKOFF_MILLIS */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static final long MIN_BACKOFF_MILLIS = 10 * 1000L; // 10 seconds.

    /** @see android.app.job.JobInfo#getMinPeriodMillis() */
    public static final long getMinPeriodMillis() {
        return MIN_PERIOD_MILLIS;
    }

    /** @see android.app.job.JobInfo#getMinFlexMillis() */
    public static final long getMinFlexMillis() {
        return MIN_FLEX_MILLIS;
    }

    /** Same as android.app.job.JobInfo#getMinBackoffMillis() */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static final long getMinBackoffMillis() {
        return MIN_BACKOFF_MILLIS;
    }

    /** Same as android.app.job.JobInfo#DEFAULT_BACKOFF_POLICY */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static final int DEFAULT_BACKOFF_POLICY = BACKOFF_POLICY_EXPONENTIAL;

    /** Same as android.app.job.JobInfo#CONSTRAINT_FLAG_CHARGING */
    public static final int CONSTRAINT_FLAG_CHARGING = 1;
    /** Same as android.app.job.JobInfo#CONSTRAINT_FLAG_BATTERY_NOT_LOW */
    public static final int CONSTRAINT_FLAG_BATTERY_NOT_LOW = 1 << 1;
    /** Same as android.app.job.JobInfo#CONSTRAINT_FLAG_DEVICE_IDLE */
    public static final int CONSTRAINT_FLAG_DEVICE_IDLE = 1 << 2;
    /** Same as android.app.job.JobInfo#CONSTRAINT_FLAG_STORAGE_NOT_LOW */
    public static final int CONSTRAINT_FLAG_STORAGE_NOT_LOW = 1 << 3;

    private final int jobId;
    private final PersistableBundle extras;
    private final Bundle transientExtras;
    private final ComponentName service;
    private final int constraintFlags;
    private final TriggerContentUri[] triggerContentUris;
    private final long triggerContentUpdateDelay;
    private final long triggerContentMaxDelay;
    private final boolean hasEarlyConstraint;
    private final boolean hasLateConstraint;
    private final int networkType;
    private final long minLatencyMillis;
    private final long maxExecutionDelayMillis;
    private final boolean isPeriodic;
    private final boolean isPersisted;
    private final long intervalMillis;
    private final long flexMillis;
    private final long initialBackoffMillis;
    private final int backoffPolicy;

    public JobInfo(int jobId, PersistableBundle extras, Bundle transientExtras, ComponentName service,
                   int constraintFlags, TriggerContentUri[] triggerContentUris, long triggerContentUpdateDelay,
                   long triggerContentMaxDelay, boolean hasEarlyConstraint, boolean hasLateConstraint, int networkType,
                   long minLatencyMillis, long maxExecutionDelayMillis, boolean isPeriodic, boolean isPersisted,
                   long intervalMillis, long flexMillis, long initialBackoffMillis, int backoffPolicy) {
        this.jobId = jobId;
        this.extras = extras;
        this.transientExtras = transientExtras;
        this.service = service;
        this.constraintFlags = constraintFlags;
        this.triggerContentUris = triggerContentUris;
        this.triggerContentUpdateDelay = triggerContentUpdateDelay;
        this.triggerContentMaxDelay = triggerContentMaxDelay;
        this.hasEarlyConstraint = hasEarlyConstraint;
        this.hasLateConstraint = hasLateConstraint;
        this.networkType = networkType;
        this.minLatencyMillis = minLatencyMillis;
        this.maxExecutionDelayMillis = maxExecutionDelayMillis;
        this.isPeriodic = isPeriodic;
        this.isPersisted = isPersisted;
        this.intervalMillis = intervalMillis;
        this.flexMillis = flexMillis;
        this.initialBackoffMillis = initialBackoffMillis;
        this.backoffPolicy = backoffPolicy;
    }

    /** @see android.app.job.JobInfo#getId() */
    public int getId() {
        return jobId;
    }

    /** @see android.app.job.JobInfo#getExtras() */
    public @NonNull
    PersistableBundle getExtras() {
        return extras;
    }

    /** @see android.app.job.JobInfo#getTransientExtras() */
    public @NonNull
    Bundle getTransientExtras() {
        return transientExtras;
    }

    /** @see android.app.job.JobInfo#getService() */
    public @NonNull
    ComponentName getService() {
        return service;
    }

    /** @see android.app.job.JobInfo#isRequireCharging() */
    public boolean isRequireCharging() {
        return (constraintFlags & CONSTRAINT_FLAG_CHARGING) != 0;
    }

    /** @see android.app.job.JobInfo#isRequireBatteryNotLow() */
    public boolean isRequireBatteryNotLow() {
        return (constraintFlags & CONSTRAINT_FLAG_BATTERY_NOT_LOW) != 0;
    }

    /** @see android.app.job.JobInfo#isRequireDeviceIdle() */
    public boolean isRequireDeviceIdle() {
        return (constraintFlags & CONSTRAINT_FLAG_DEVICE_IDLE) != 0;
    }

    /** @see android.app.job.JobInfo#isRequireStorageNotLow() () */
    public boolean isRequireStorageNotLow() {
        return (constraintFlags & CONSTRAINT_FLAG_STORAGE_NOT_LOW) != 0;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public int getConstraintFlags() {
        return constraintFlags;
    }

    /** @see android.app.job.JobInfo#getTriggerContentUris() */
    public @Nullable
    TriggerContentUri[] getTriggerContentUris() {
        return triggerContentUris;
    }

    /** @see android.app.job.JobInfo#getTriggerContentUpdateDelay() */
    public long getTriggerContentUpdateDelay() {
        return triggerContentUpdateDelay;
    }

    /** @see android.app.job.JobInfo#getTriggerContentMaxDelay() */
    public long getTriggerContentMaxDelay() {
        return triggerContentMaxDelay;
    }

    /** @see android.app.job.JobInfo#getNetworkType() */
    public @NetworkType
    int getNetworkType() {
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
        final long minInterval = getMinPeriodMillis();
        return intervalMillis >= minInterval ? intervalMillis : minInterval;
    }

    /** @see android.app.job.JobInfo#getFlexMillis() */
    public long getFlexMillis() {
        long interval = getIntervalMillis();
        long percentClamp = 5 * interval / 100;
        long clampedFlex = Math.max(flexMillis, Math.max(percentClamp, getMinFlexMillis()));
        return clampedFlex <= interval ? clampedFlex : interval;
    }

    /** @see android.app.job.JobInfo#getInitialBackoffMillis() */
    public long getInitialBackoffMillis() {
        final long minBackoff = getMinBackoffMillis();
        return initialBackoffMillis >= minBackoff ? initialBackoffMillis : minBackoff;
    }

    /** @see android.app.job.JobInfo#getBackoffPolicy() */
    public @BackoffPolicy
    int getBackoffPolicy() {
        return backoffPolicy;
    }

    /** Same as android.app.job.JobInfo#hasEarlyConstraint() */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public boolean hasEarlyConstraint() {
        return hasEarlyConstraint;
    }

    /** Same as android.app.job.JobInfo#hasLateConstraint() */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public boolean hasLateConstraint() {
        return hasLateConstraint;
    }

    /** @see android.app.job.JobInfo.TriggerContentUri */
    public static final class TriggerContentUri implements Parcelable {
        private final Uri uri;
        private final int flags;

        @Retention(RetentionPolicy.SOURCE)
        @IntDef(flag = true, value = {FLAG_NOTIFY_FOR_DESCENDANTS})
        public @interface Flags {
        }

        /** @see android.app.job.JobInfo.TriggerContentUri#FLAG_NOTIFY_FOR_DESCENDANTS */
        public static final int FLAG_NOTIFY_FOR_DESCENDANTS = 1 << 0;

        /** @see android.app.job.JobInfo.TriggerContentUri() */
        public TriggerContentUri(@NonNull Uri uri, @Flags int flags) {
            this.uri = uri;
            this.flags = flags;
        }

        /** @see android.app.job.JobInfo.TriggerContentUri#getUri() */
        public Uri getUri() {
            return uri;
        }

        /** @see android.app.job.JobInfo.TriggerContentUri#getFlags() */
        public @Flags
        int getFlags() {
            return flags;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TriggerContentUri)) {
                return false;
            }
            TriggerContentUri t = (TriggerContentUri) o;
            return (uri != null ? uri.equals(t.uri) : t.uri == null) && flags == t.flags;
        }

        @Override
        public int hashCode() {
            return (uri == null ? 0 : uri.hashCode()) ^ flags;
        }

        private TriggerContentUri(Parcel in) {
            uri = Uri.CREATOR.createFromParcel(in);
            flags = in.readInt();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            uri.writeToParcel(out, flags);
            out.writeInt(this.flags);
        }

        public static final Creator<TriggerContentUri> CREATOR = new Creator<TriggerContentUri>() {
            @Override
            public TriggerContentUri createFromParcel(Parcel in) {
                return new TriggerContentUri(in);
            }

            @Override
            public TriggerContentUri[] newArray(int size) {
                return new TriggerContentUri[size];
            }
        };
    }

    /** @see android.app.job.JobInfo.Builder */
    public static final class Builder {
        private final int jobId;
        private final ComponentName jobService;
        private PersistableBundle extras = PersistableBundle.EMPTY;
        private Bundle transientExtras = Bundle.EMPTY;
        // Requirements.
        private int constraintFlags;
        private int networkType;
        private ArrayList<TriggerContentUri> triggerContentUris;
        private long triggerContentUpdateDelay = -1;
        private long triggerContentMaxDelay = -1;
        private boolean isPersisted;
        // One-off parameters.
        private long minLatencyMillis;
        private long maxExecutionDelayMillis;
        // Periodic parameters.
        private boolean isPeriodic;
        private boolean hasEarlyConstraint;
        private boolean hasLateConstraint;
        private long intervalMillis;
        private long flexMillis;
        // Back-off parameters.
        private long initialBackoffMillis = DEFAULT_INITIAL_BACKOFF_MILLIS;
        private int backoffPolicy = DEFAULT_BACKOFF_POLICY;
        private boolean backoffPolicySet = false;

        /** @see android.app.job.JobInfo.Builder#Builder(int, ComponentName) */
        public Builder(int jobId, ComponentName jobService) {
            this.jobId = jobId;
            this.jobService = jobService;
        }

        /** @see android.app.job.JobInfo.Builder#setExtras(android.os.PersistableBundle) */
        public JobInfo.Builder setExtras(PersistableBundle extras) {
            this.extras = extras;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setTransientExtras(Bundle) */
        public JobInfo.Builder setTransientExtras(Bundle transientExtras) {
            this.transientExtras = transientExtras;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setRequiredNetworkType(int) */
        public JobInfo.Builder setRequiredNetworkType(@NetworkType int networkType) {
            this.networkType = networkType;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setRequiresCharging(boolean) */
        public JobInfo.Builder setRequiresCharging(boolean requiresCharging) {
            constraintFlags = (constraintFlags & ~CONSTRAINT_FLAG_CHARGING)
                    | (requiresCharging ? CONSTRAINT_FLAG_CHARGING : 0);
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setRequiresBatteryNotLow(boolean) */
        public Builder setRequiresBatteryNotLow(boolean batteryNotLow) {
            constraintFlags = (constraintFlags & ~CONSTRAINT_FLAG_BATTERY_NOT_LOW)
                    | (batteryNotLow ? CONSTRAINT_FLAG_BATTERY_NOT_LOW : 0);
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setRequiresDeviceIdle(boolean) */
        public JobInfo.Builder setRequiresDeviceIdle(boolean requiresDeviceIdle) {
            constraintFlags = (constraintFlags & ~CONSTRAINT_FLAG_DEVICE_IDLE)
                    | (requiresDeviceIdle ? CONSTRAINT_FLAG_DEVICE_IDLE : 0);
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setRequiresStorageNotLow(boolean) */
        public Builder setRequiresStorageNotLow(boolean storageNotLow) {
            constraintFlags = (constraintFlags & ~CONSTRAINT_FLAG_STORAGE_NOT_LOW)
                    | (storageNotLow ? CONSTRAINT_FLAG_STORAGE_NOT_LOW : 0);
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#addTriggerContentUri(android.app.job.JobInfo.TriggerContentUri) */
        public Builder addTriggerContentUri(@NonNull TriggerContentUri uri) {
            if (triggerContentUris == null) {
                triggerContentUris = new ArrayList<>();
            }
            triggerContentUris.add(uri);
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setTriggerContentUpdateDelay(long) */
        public Builder setTriggerContentUpdateDelay(long durationMs) {
            triggerContentUpdateDelay = durationMs;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setTriggerContentMaxDelay(long) */
        public Builder setTriggerContentMaxDelay(long durationMs) {
            triggerContentMaxDelay = durationMs;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setPeriodic(long) */
        public JobInfo.Builder setPeriodic(long intervalMillis) {
            return setPeriodic(intervalMillis, intervalMillis);
        }

        /** @see android.app.job.JobInfo.Builder#setPeriodic(long, long) */
        public JobInfo.Builder setPeriodic(long intervalMillis, long flexMillis) {
            isPeriodic = true;
            this.intervalMillis = intervalMillis;
            this.flexMillis = flexMillis;
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
        public JobInfo.Builder setBackoffCriteria(long initialBackoffMillis, @BackoffPolicy int backoffPolicy) {
            this.initialBackoffMillis = initialBackoffMillis;
            this.backoffPolicy = backoffPolicy;
            backoffPolicySet = true;
            return this;
        }

        @RestrictTo(RestrictTo.Scope.LIBRARY)
        public boolean isBackoffPolicySet() {
            return backoffPolicySet;
        }

        /** @see android.app.job.JobInfo.Builder#setPersisted(boolean) */
        public JobInfo.Builder setPersisted(boolean isPersisted) {
            this.isPersisted = isPersisted;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#build() */
        public JobInfo build() {
            // Don't allow jobs without a service.
            if (jobService == null) {
                throw new IllegalArgumentException(
                        "You're trying to build a job without a service, this is not allowed.");
            }

            // Don't allow jobs with no constraints.
            if (!hasEarlyConstraint && !hasLateConstraint && constraintFlags == 0
                    && networkType == NETWORK_TYPE_NONE && triggerContentUris == null) {
                throw new IllegalArgumentException(
                        "You're trying to build a job without constraints, this is not allowed.");
            }

            // Check that latency or deadlines were not set on a periodic job.
            if (isPeriodic) {
                if (maxExecutionDelayMillis != 0L) {
                    throw new IllegalArgumentException("Can't call setOverrideDeadline() on a periodic job.");
                }
                if (minLatencyMillis != 0L) {
                    throw new IllegalArgumentException("Can't call setMinimumLatency() on a periodic job");
                }
                if (triggerContentUris != null) {
                    throw new IllegalArgumentException("Can't call addTriggerContentUri() on a periodic job");
                }
            }

            if (isPersisted) {
                if (triggerContentUris != null) {
                    throw new IllegalArgumentException("Can't call addTriggerContentUri() on a persisted job");
                }
                if (!transientExtras.isEmpty()) {
                    throw new IllegalArgumentException("Can't call setTransientExtras() on a persisted job");
                }
            }

            if (backoffPolicySet && (constraintFlags & CONSTRAINT_FLAG_DEVICE_IDLE) != 0) {
                throw new IllegalArgumentException(
                        "An idle mode job will not respect any back-off policy, so calling setBackoffCriteria with"
                                + " setRequiresDeviceIdle is an error.");
            }

            // Make our own copy.
            extras = new PersistableBundle(extras);

            JobInfo job = new JobInfo(
                    jobId, extras, transientExtras, jobService, constraintFlags,
                    triggerContentUris != null ?
                    triggerContentUris.toArray(new TriggerContentUri[0]) : null,
                    triggerContentUpdateDelay, triggerContentMaxDelay, hasEarlyConstraint, hasLateConstraint,
                    networkType, minLatencyMillis, maxExecutionDelayMillis, isPeriodic, isPersisted,
                    intervalMillis, flexMillis, initialBackoffMillis, backoffPolicy);

            if (job.isPeriodic()) {
                if (job.intervalMillis != job.getIntervalMillis()) {
                    Log.w(LOG_TAG, "Specified interval for " + jobId + " is " + intervalMillis + "ms."
                            + " Clamped to " + job.getIntervalMillis() + "ms");
                }
                if (job.flexMillis != job.getFlexMillis()) {
                    Log.w(LOG_TAG, "Specified flex for " + jobId + " is " + flexMillis + "ms."
                            + " Clamped to " + job.getFlexMillis() + "ms");
                }
            }

            return job;
        }
    }
}
