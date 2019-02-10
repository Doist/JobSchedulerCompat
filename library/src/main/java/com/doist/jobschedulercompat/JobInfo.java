package com.doist.jobschedulercompat;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ComponentName;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
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
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;

/** @see android.app.job.JobInfo */
public class JobInfo {
    private static final String LOG_TAG = "JobInfoCompat";

    @IntDef({
            NETWORK_TYPE_NONE,
            NETWORK_TYPE_ANY,
            NETWORK_TYPE_UNMETERED,
            NETWORK_TYPE_NOT_ROAMING,
            NETWORK_TYPE_CELLULAR
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
    public static final int NETWORK_TYPE_CELLULAR = 4;

    /** @see android.app.job.JobInfo#NETWORK_TYPE_METERED */
    @Deprecated
    public static final int NETWORK_TYPE_METERED = NETWORK_TYPE_CELLULAR;

    /** @see android.app.job.JobInfo#NETWORK_BYTES_UNKNOWN */
    public static final int NETWORK_BYTES_UNKNOWN = -1;

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
    private final ComponentName service;
    private final PersistableBundle extras;
    private final Bundle transientExtras;
    private final ClipData clipData;
    private final int clipGrantFlags;
    private final int constraintFlags;
    private final TriggerContentUri[] triggerContentUris;
    private final long triggerContentUpdateDelay;
    private final long triggerContentMaxDelay;
    private final boolean hasEarlyConstraint;
    private final boolean hasLateConstraint;
    private final int networkType;
    private final NetworkRequest networkRequest;
    private final long networkDownloadBytes;
    private final long networkUploadBytes;
    private final long minLatencyMillis;
    private final long maxExecutionDelayMillis;
    private final boolean isPeriodic;
    private final boolean isPersisted;
    private final long intervalMillis;
    private final long flexMillis;
    private final long initialBackoffMillis;
    private final int backoffPolicy;
    private final boolean importantWhileForeground;
    private final boolean prefetch;

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public JobInfo(int jobId, ComponentName service, PersistableBundle extras, Bundle transientExtras,
                   ClipData clipData, int clipGrantFlags, int constraintFlags, TriggerContentUri[] triggerContentUris,
                   long triggerContentUpdateDelay, long triggerContentMaxDelay, boolean hasEarlyConstraint,
                   boolean hasLateConstraint, int networkType, NetworkRequest networkRequest, long networkDownloadBytes,
                   long networkUploadBytes, long minLatencyMillis, long maxExecutionDelayMillis, boolean isPeriodic,
                   boolean isPersisted, long intervalMillis, long flexMillis, long initialBackoffMillis,
                   int backoffPolicy, boolean importantWhileForeground, boolean prefetch) {
        this.jobId = jobId;
        this.service = service;
        this.extras = extras;
        this.transientExtras = transientExtras;
        this.clipData = clipData;
        this.clipGrantFlags = clipGrantFlags;
        this.constraintFlags = constraintFlags;
        this.triggerContentUris = triggerContentUris;
        this.triggerContentUpdateDelay = triggerContentUpdateDelay;
        this.triggerContentMaxDelay = triggerContentMaxDelay;
        this.hasEarlyConstraint = hasEarlyConstraint;
        this.hasLateConstraint = hasLateConstraint;
        this.networkType = networkType;
        this.networkRequest = networkRequest;
        this.networkDownloadBytes = networkDownloadBytes;
        this.networkUploadBytes = networkUploadBytes;
        this.minLatencyMillis = minLatencyMillis;
        this.maxExecutionDelayMillis = maxExecutionDelayMillis;
        this.isPeriodic = isPeriodic;
        this.isPersisted = isPersisted;
        this.intervalMillis = intervalMillis;
        this.flexMillis = flexMillis;
        this.initialBackoffMillis = initialBackoffMillis;
        this.backoffPolicy = backoffPolicy;
        this.importantWhileForeground = importantWhileForeground;
        this.prefetch = prefetch;
    }

    /** @see android.app.job.JobInfo#getId() */
    public int getId() {
        return jobId;
    }

    /** @see android.app.job.JobInfo#getService() */
    public @NonNull ComponentName getService() {
        return service;
    }

    /** @see android.app.job.JobInfo#getExtras() */
    public @NonNull PersistableBundle getExtras() {
        return extras;
    }

    /** @see android.app.job.JobInfo#getTransientExtras() */
    public @NonNull Bundle getTransientExtras() {
        return transientExtras;
    }

    /** @see android.app.job.JobInfo#getClipData() */
    @RequiresApi(Build.VERSION_CODES.O)
    public @Nullable ClipData getClipData() {
        return clipData;
    }

    /** @see android.app.job.JobInfo#getClipGrantFlags() */
    @RequiresApi(Build.VERSION_CODES.O)
    public int getClipGrantFlags() {
        return clipGrantFlags;
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
    public @Nullable TriggerContentUri[] getTriggerContentUris() {
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
    public @NetworkType int getNetworkType() {
        return networkType;
    }

    /** @see android.app.job.JobInfo#getRequiredNetwork() */
    @RequiresApi(Build.VERSION_CODES.P)
    public NetworkRequest getRequiredNetwork() {
        return networkRequest;
    }

    /** @see android.app.job.JobInfo#getEstimatedNetworkDownloadBytes() */
    public long getEstimatedNetworkDownloadBytes() {
        return networkDownloadBytes;
    }

    /** @see android.app.job.JobInfo#getEstimatedNetworkUploadBytes() */
    public long getEstimatedNetworkUploadBytes() {
        return networkUploadBytes;
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
        return flexMillis;
    }

    /** @see android.app.job.JobInfo#getInitialBackoffMillis() */
    public long getInitialBackoffMillis() {
        final long minBackoff = getMinBackoffMillis();
        return initialBackoffMillis >= minBackoff ? initialBackoffMillis : minBackoff;
    }

    /** @see android.app.job.JobInfo#getBackoffPolicy() */
    public @BackoffPolicy int getBackoffPolicy() {
        return backoffPolicy;
    }

    /** @see android.app.job.JobInfo#isImportantWhileForeground() */
    public boolean isImportantWhileForeground() {
        return importantWhileForeground;
    }

    /** @see android.app.job.JobInfo#isPrefetch() */
    public boolean isPrefetch() {
        return prefetch;
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
        public @Flags int getFlags() {
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
        private ClipData clipData;
        private int clipGrantFlags;
        private boolean importantWhileForeground;
        private boolean prefetch;
        // Requirements.
        private int constraintFlags;
        private int networkType;
        private NetworkRequest networkRequest;
        private long networkDownloadBytes = NETWORK_BYTES_UNKNOWN;
        private long networkUploadBytes = NETWORK_BYTES_UNKNOWN;
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
        public Builder(int jobId, @NonNull ComponentName jobService) {
            this.jobId = jobId;
            this.jobService = jobService;
        }

        /** @see android.app.job.JobInfo.Builder#setExtras(android.os.PersistableBundle) */
        public Builder setExtras(PersistableBundle extras) {
            this.extras = extras;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setTransientExtras(Bundle) */
        public Builder setTransientExtras(Bundle transientExtras) {
            this.transientExtras = transientExtras;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setClipData(ClipData, int) */
        @RequiresApi(Build.VERSION_CODES.O)
        public Builder setClipData(@Nullable ClipData clip, int grantFlags) {
            clipData = clip;
            clipGrantFlags = grantFlags;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setRequiredNetworkType(int) */
        public Builder setRequiredNetworkType(@NetworkType int networkType) {
            this.networkType = networkType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                syncRequiredNetworkAndType(null, networkType);
            }
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setRequiredNetwork(NetworkRequest) */
        @RequiresApi(Build.VERSION_CODES.P)
        public Builder setRequiredNetwork(NetworkRequest networkRequest) {
            this.networkRequest = networkRequest;
            syncRequiredNetworkAndType(networkRequest, null);
            return this;
        }

        @TargetApi(Build.VERSION_CODES.P)
        private void syncRequiredNetworkAndType(NetworkRequest networkRequest, Integer networkType) {
            if (networkType == null) {
                if (networkRequest == null) {
                    this.networkType = NETWORK_TYPE_NONE;
                } else if (networkRequest.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
                    this.networkType = NETWORK_TYPE_UNMETERED;
                } else if (networkRequest.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)) {
                    this.networkType = NETWORK_TYPE_NOT_ROAMING;
                } else if (networkRequest.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    this.networkType = NETWORK_TYPE_CELLULAR;
                } else {
                    this.networkType = NETWORK_TYPE_ANY;
                }
            } else {
                final NetworkRequest.Builder builder = new NetworkRequest.Builder();
                builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                builder.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
                if (networkType == NETWORK_TYPE_UNMETERED) {
                    builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
                } else if (networkType == NETWORK_TYPE_NOT_ROAMING) {
                    builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
                } else if (networkType == NETWORK_TYPE_CELLULAR) {
                    builder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
                }
                this.networkRequest = builder.build();
            }
        }

        /** @see android.app.job.JobInfo.Builder#setEstimatedNetworkBytes(long, long) */
        public Builder setEstimatedNetworkBytes(long downloadBytes, long uploadBytes) {
            networkDownloadBytes = downloadBytes;
            networkUploadBytes = uploadBytes;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setRequiresCharging(boolean) */
        public Builder setRequiresCharging(boolean requiresCharging) {
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
        public Builder setRequiresDeviceIdle(boolean requiresDeviceIdle) {
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
        public Builder setPeriodic(long intervalMillis) {
            return setPeriodic(intervalMillis, intervalMillis);
        }

        /** @see android.app.job.JobInfo.Builder#setPeriodic(long, long) */
        public Builder setPeriodic(long intervalMillis, long flexMillis) {
            final long minPeriod = getMinPeriodMillis();
            if (intervalMillis < minPeriod) {
                Log.w(LOG_TAG, "Requested interval " + intervalMillis + " for job " + jobId
                        + " is too small; raising to " + minPeriod);
                intervalMillis = minPeriod;
            }

            final long percentClamp = 5 * intervalMillis / 100;
            final long minFlex = Math.max(percentClamp, getMinFlexMillis());
            if (flexMillis < minFlex) {
                Log.w(LOG_TAG, "Requested flex " + flexMillis + " for job " + jobId
                        + " is too small; raising to " + minFlex);
                flexMillis = minFlex;
            }

            isPeriodic = true;
            this.intervalMillis = intervalMillis;
            this.flexMillis = flexMillis;
            hasEarlyConstraint = hasLateConstraint = true;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setMinimumLatency(long) */
        public Builder setMinimumLatency(long minLatencyMillis) {
            this.minLatencyMillis = minLatencyMillis;
            hasEarlyConstraint = true;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setOverrideDeadline(long) */
        public Builder setOverrideDeadline(long maxExecutionDelayMillis) {
            this.maxExecutionDelayMillis = maxExecutionDelayMillis;
            hasLateConstraint = true;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setBackoffCriteria(long, int) */
        public Builder setBackoffCriteria(long initialBackoffMillis, @BackoffPolicy int backoffPolicy) {
            final long minBackoff = getMinBackoffMillis();
            if (initialBackoffMillis < minBackoff) {
                Log.w(LOG_TAG, "Requested backoff " + initialBackoffMillis + " for job " + jobId
                        + " is too small; raising to " + minBackoff);
                initialBackoffMillis = minBackoff;
            }

            this.initialBackoffMillis = initialBackoffMillis;
            this.backoffPolicy = backoffPolicy;
            backoffPolicySet = true;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setImportantWhileForeground(boolean) */
        public Builder setImportantWhileForeground(boolean importantWhileForeground) {
            this.importantWhileForeground = importantWhileForeground;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setPrefetch(boolean) */
        public Builder setPrefetch(boolean prefetch) {
            this.prefetch = prefetch;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#setPersisted(boolean) */
        public Builder setPersisted(boolean isPersisted) {
            this.isPersisted = isPersisted;
            return this;
        }

        /** @see android.app.job.JobInfo.Builder#build() */
        public JobInfo build() {
            // Don't allow jobs with no constraints.
            if (!hasEarlyConstraint && !hasLateConstraint && constraintFlags == 0
                    && networkType == NETWORK_TYPE_NONE && networkRequest == null && triggerContentUris == null) {
                throw new IllegalArgumentException(
                        "You're trying to build a job without constraints, this is not allowed.");
            }

            // Check that network estimates require network type.
            if ((networkDownloadBytes > 0 || networkUploadBytes > 0)
                    && (networkType == NETWORK_TYPE_NONE && networkRequest == null)) {
                throw new IllegalArgumentException(
                        "Can't provide estimated network usage without requiring a network");
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

            if (importantWhileForeground && hasEarlyConstraint) {
                throw new IllegalArgumentException("An important while foreground job cannot have a time delay");
            }

            if (backoffPolicySet && (constraintFlags & CONSTRAINT_FLAG_DEVICE_IDLE) != 0) {
                throw new IllegalArgumentException(
                        "An idle mode job will not respect any back-off policy, so calling setBackoffCriteria with"
                                + " setRequiresDeviceIdle is an error.");
            }

            // Make our own copy.
            extras = new PersistableBundle(extras);

            return new JobInfo(
                    jobId, jobService, extras, transientExtras, clipData, clipGrantFlags, constraintFlags,
                    triggerContentUris != null ? triggerContentUris.toArray(new TriggerContentUri[0]) : null,
                    triggerContentUpdateDelay, triggerContentMaxDelay, hasEarlyConstraint, hasLateConstraint,
                    networkType, networkRequest, networkDownloadBytes, networkDownloadBytes, minLatencyMillis,
                    maxExecutionDelayMillis, isPeriodic, isPersisted, intervalMillis, flexMillis, initialBackoffMillis,
                    backoffPolicy, importantWhileForeground, prefetch);
        }
    }
}
