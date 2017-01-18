package com.doist.jobschedulercompat;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

import java.util.HashMap;
import java.util.Map;

/**
 * Same as android.os.PersistableBundle, with minor modifications and unused code removed.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public final class PersistableBundle implements Parcelable {
    private static final String LOG_TAG = "PersistableBundleCompat";

    public static final PersistableBundle EMPTY;
    static {
        EMPTY = new PersistableBundle();
    }

    private final Map<String, Object> map;

    public PersistableBundle() {
        this.map = new HashMap<>();
    }

    public PersistableBundle(PersistableBundle bundle) {
        this.map = new HashMap<>(bundle.map);
    }

    public PersistableBundle(Bundle bundle) {
        map = new HashMap<>(bundle.size());
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            if(value == null || value instanceof String || value instanceof Integer || value instanceof Long
                    || value instanceof Double || value instanceof String[] || value instanceof int[]
                    || value instanceof long[] || value instanceof double[]) {
                map.put(key, value);
            } else if (value instanceof Bundle) {
                map.put(key, new PersistableBundle((Bundle) value));
            } else {
                throw new IllegalArgumentException("Unsupported value type key=" + key + " value=" + value);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public PersistableBundle(android.os.PersistableBundle bundle) {
        map = new HashMap<>(bundle.size());
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            if(value == null || value instanceof String || value instanceof Integer || value instanceof Long
                    || value instanceof Double || value instanceof String[] || value instanceof int[]
                    || value instanceof long[] || value instanceof double[]) {
                map.put(key, value);
            } else if (value instanceof android.os.PersistableBundle) {
                map.put(key, new PersistableBundle((android.os.PersistableBundle) value));
            } else {
                throw new IllegalArgumentException("Unsupported value type key=" + key + " value=" + value);
            }
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @SuppressWarnings("unchecked")
    public PersistableBundle(Map<String, ?> map, int depth) {
        if (depth <= 0) {
            this.map = new HashMap<>(0);
        } else {
            this.map = new HashMap<>(map);
            for (String key : this.map.keySet()) {
                Object object = this.map.get(key);
                if (object instanceof Map) {
                    this.map.put(key, new PersistableBundle((Map<String, Object>) object, depth - 1));
                }
            }
        }
    }

    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public int size() {
        return map.size();
    }

    public Object get(String key) {
        return map.get(key);
    }

    public String getString(String key, String defaultValue) {
        Object value = map.get(key);
        if (value instanceof String) {
            return (String) value;
        } else {
            return defaultValue;
        }
    }

    public String getString(String key) {
        return getString(key, null);
    }

    public int getInt(String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else {
            return defaultValue;
        }
    }

    public int getInt(String key) {
        return getInt(key, 0);
    }

    public long getLong(String key, long defaultValue) {
        Object value = map.get(key);
        if (value instanceof Long) {
            return (Long) value;
        } else {
            return defaultValue;
        }
    }

    public long getLong(String key) {
        return getLong(key, 0L);
    }

    public double getDouble(String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Double) {
            return (Double) value;
        } else {
            return defaultValue;
        }
    }

    public double getDouble(String key) {
        return getDouble(key, 0.0);
    }

    public String[] getStringArray(String key) {
        Object value = map.get(key);
        if (value instanceof String[]) {
            return (String[]) value;
        } else {
            return null;
        }
    }

    public int[] getIntArray(String key) {
        Object value = map.get(key);
        if (value instanceof int[]) {
            return (int[]) value;
        } else {
            return null;
        }
    }

    public long[] getLongArray(String key) {
        Object value = map.get(key);
        if (value instanceof long[]) {
            return (long[]) value;
        } else {
            return null;
        }
    }

    public double[] getDoubleArray(String key) {
        Object value = map.get(key);
        if (value instanceof double[]) {
            return (double[]) value;
        } else {
            return null;
        }
    }

    public PersistableBundle getPersistableBundleCompat(String key) {
        Object value = map.get(key);
        if (value instanceof PersistableBundle) {
            return (PersistableBundle) value;
        } else {
            return null;
        }
    }

    public void putString(String key, String value) {
        map.put(key, value);
    }

    public void putInt(String key, int value) {
        map.put(key, value);
    }

    public void putLong(String key, long value) {
        map.put(key, value);
    }

    public void putDouble(String key, double value) {
        map.put(key, value);
    }

    public void putStringArray(String key, String[] value) {
        map.put(key, value);
    }

    public void putIntArray(String key, int[] value) {
        map.put(key, value);
    }

    public void putLongArray(String key, long[] value) {
        map.put(key, value);
    }

    public void putDoubleArray(String key, double[] value) {
        map.put(key, value);
    }

    public void putPersistableBundleCompat(String key, PersistableBundle value) {
        map.put(key, value);
    }

    public void putAll(PersistableBundle bundle) {
        map.putAll(bundle.map);
    }

    public void remove(String key) {
        map.remove(key);
    }

    public void clear() {
        map.clear();
    }

    @NonNull
    public Bundle toBundle() {
        Bundle bundle = new Bundle(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                bundle.putString(key, null);
            } else if (value instanceof String) {
                bundle.putString(key, (String) value);
            } else if (value instanceof Integer) {
                bundle.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                bundle.putLong(key, (Long) value);
            } else if (value instanceof Double) {
                bundle.putDouble(key, (Double) value);
            } else if (value instanceof String[]) {
                bundle.putStringArray(key, (String[]) value);
            } else if (value instanceof int[]) {
                bundle.putIntArray(key, (int[]) value);
            } else if (value instanceof long[]) {
                bundle.putLongArray(key, (long[]) value);
            } else if (value instanceof double[]) {
                bundle.putDoubleArray(key, (double[]) value);
            } else if (value instanceof PersistableBundle) {
                bundle.putBundle(key, ((PersistableBundle) value).toBundle());
            }
        }
        return bundle;
    }

    @NonNull
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public android.os.PersistableBundle toPersistableBundle() {
        android.os.PersistableBundle bundle = new android.os.PersistableBundle(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                bundle.putString(key, null);
            } else if (value instanceof String) {
                bundle.putString(key, (String) value);
            } else if (value instanceof Integer) {
                bundle.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                bundle.putLong(key, (Long) value);
            } else if (value instanceof Double) {
                bundle.putDouble(key, (Double) value);
            } else if (value instanceof String[]) {
                bundle.putStringArray(key, (String[]) value);
            } else if (value instanceof int[]) {
                bundle.putIntArray(key, (int[]) value);
            } else if (value instanceof long[]) {
                bundle.putLongArray(key, (long[]) value);
            } else if (value instanceof double[]) {
                bundle.putDoubleArray(key, (double[]) value);
            } else if (value instanceof PersistableBundle) {
                bundle.putPersistableBundle(key, ((PersistableBundle) value).toPersistableBundle());
            }
        }
        return bundle;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public Map<String, ?> toMap(int depth) {
        if (depth <= 0) {
            return null;
        }
        Map<String, Object> map = new HashMap<>(this.map);
        for (String key : map.keySet()) {
            Object object = map.get(key);
            if (object instanceof PersistableBundle) {
                map.put(key, ((PersistableBundle) object).toMap(depth - 1));
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    PersistableBundle(Parcel in) {
        this.map = (HashMap<String, Object>) in.readHashMap(PersistableBundle.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeMap(map);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<PersistableBundle> CREATOR =
            new Parcelable.Creator<PersistableBundle>() {
        public PersistableBundle createFromParcel(Parcel in) {
            return new PersistableBundle(in);
        }

        public PersistableBundle[] newArray(int size) {
            return new PersistableBundle[size];
        }
    };
}
