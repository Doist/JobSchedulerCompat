package com.doist.jobschedulercompat.scheduler.gcm;

import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class GcmIntentParser {
    /** The Parcelable class that wraps the Binder we need to access. */
    private static final String PENDING_CALLBACK_CLASS = "com.google.android.gms.gcm.PendingCallback";
    /** The key for the tag. */
    static final String BUNDLE_KEY_TAG = "tag";
    /** The key for the extras. */
    static final String BUNDLE_KEY_EXTRAS = "extras";
    /** The key for the triggered uris. */
    static final String BUNDLE_KEY_TRIGGERED_URIS = "triggered_uris";
    /** The key for the wrapped Binder. */
    static final String BUNDLE_KEY_CALLBACK = "callback";
    /** A magic number that indicates the following bytes belong to a Bundle. */
    private static final int BUNDLE_MAGIC = 0x4C444E42;
    /** A magic number that indicates the following value is a Parcelable. */
    private static final int VAL_PARCELABLE = 4;

    private static Boolean shouldReadKeysAsStrings = null;

    private int jobId;
    private Bundle extras;
    private List<Uri> triggeredContentUris;
    private List<String> triggeredContentAuthorities;
    private IBinder callback;

    /**
     * Iterates over the map looking for the {@link #BUNDLE_KEY_CALLBACK} key to try and read the {@link IBinder}
     * straight from the parcelled data. This is entirely dependent on the implementation of Parcel, but these specific
     * parts of {@link Parcel} / {@link Bundle} haven't changed since 2008 and newer versions of Android will ship
     * with newer versions of Google Play services which embed the IBinder directly into the {@link Bundle}
     * (no need to deal with the {@link android.os.Parcelable} issues).
     */
    GcmIntentParser(Bundle data) throws RuntimeException {
        if (data == null) {
            throw new IllegalArgumentException();
        }

        jobId = Integer.valueOf(data.getString(BUNDLE_KEY_TAG));

        extras = data.getBundle(BUNDLE_KEY_EXTRAS);

        triggeredContentUris = data.getParcelableArrayList(BUNDLE_KEY_TRIGGERED_URIS);

        if (triggeredContentUris != null) {
            triggeredContentAuthorities = new ArrayList<>();
            for (Uri triggeredContentUri : triggeredContentUris) {
                triggeredContentAuthorities.add(triggeredContentUri.getAuthority());
            }
        }

        Parcel parcel = toParcel(data);
        try {
            int numEntries = checkNonEmptyBundleHeader(parcel);
            for (int i = 0; i < numEntries; i++) {
                String key = null;
                if (shouldReadKeysAsStrings()) {
                    key = parcel.readString();
                } else {
                    Object entryKeyObj = parcel.readValue(getClass().getClassLoader());
                    if (entryKeyObj instanceof String) {
                        key = (String) entryKeyObj;
                    }
                }

                if (key == null) {
                    continue;
                }

                if (BUNDLE_KEY_CALLBACK.equals(key)
                        && parcel.readInt() == VAL_PARCELABLE
                        && PENDING_CALLBACK_CLASS.equals(parcel.readString())) {
                    callback = parcel.readStrongBinder();
                    break;
                }
            }
        } finally {
            parcel.recycle();
        }

        if (extras == null || callback == null) {
            throw new IllegalArgumentException();
        }
    }

    int getJobId() {
        return jobId;
    }

    @NonNull
    Bundle getExtras() {
        return extras;
    }

    @Nullable
    public List<Uri> getTriggeredContentUris() {
        return triggeredContentUris;
    }

    @Nullable
    public List<String> getTriggeredContentAuthorities() {
        return triggeredContentAuthorities;
    }

    @NonNull
    IBinder getCallback() {
        return callback;
    }

    private static Parcel toParcel(Bundle data) {
        Parcel serialized = Parcel.obtain();
        data.writeToParcel(serialized, 0);
        serialized.setDataPosition(0);
        return serialized;
    }

    /**
     * Checks whether {@link Parcel#readString()} or {@link Parcel#readValue(ClassLoader)} should be used to access
     * Bundle keys from a serialized Parcel. Commit https://android.googlesource.com/platform/frameworks/base/+
     * /9c3e74fI57bda9eb79ceaaa9c1b94ad49d9e462b52102149 (which only officially landed in Lollipop) changed from using
     * writeValue to writeString for Bundle keys. Some OEMs have pulled this change into their KitKat fork, so we can't
     * trust the SDK version check. Instead, we'll write a dummy Bundle to a Parcel and figure it out using that.
     *
     * The check is cached because the result doesn't change during runtime.
     */
    private static synchronized boolean shouldReadKeysAsStrings() {
        // readString() should always be used on L+, but if the check is short-circuited there'd be no evidence that
        // this code is functioning correctly on KitKat devices that have the corresponding writeString() change.
        if (shouldReadKeysAsStrings == null) {
            Bundle testBundle = new Bundle();
            testBundle.putString("key", "value");
            Parcel testParcel = toParcel(testBundle);
            try {
                int entries = checkNonEmptyBundleHeader(testParcel);
                shouldReadKeysAsStrings = entries == 1 && "key".equals(testParcel.readString());
            } catch (RuntimeException e) {
                shouldReadKeysAsStrings = false;
            } finally {
                testParcel.recycle();
            }
        }

        return shouldReadKeysAsStrings;
    }

    /**
     * Checks that parcel contains a properly formatted Bundle by checking its header.
     *
     * Bundles are written out in a specific format.
     *
     * First, a header, which consists of:
     *
     * <ol>
     * <li>length (int)
     * <li>magic number ({@link #BUNDLE_MAGIC}) (int)
     * <li>number of entries (int)
     * </ol>
     *
     * <p>Then the map values, each of which looks like this:
     *
     * <ol>
     * <li>string key
     * <li>int type marker
     * <li>(any) parceled value
     * </ol>
     *
     * @return the number of map entries
     */
    private static int checkNonEmptyBundleHeader(Parcel parcel) {
        // Length.
        checkCondition(parcel.readInt() > 0);
        // Magic number.
        checkCondition(parcel.readInt() == BUNDLE_MAGIC);
        // Number of entries.
        return parcel.readInt();
    }

    private static void checkCondition(boolean condition) {
        if (!condition) {
            throw new IllegalStateException();
        }
    }

}
