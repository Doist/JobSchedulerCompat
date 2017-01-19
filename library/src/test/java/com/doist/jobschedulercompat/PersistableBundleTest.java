package com.doist.jobschedulercompat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class PersistableBundleTest {
    @Test
    public void testCopyConstructor() {
        PersistableBundle bundle = getFilledBundle(10);
        PersistableBundle copy = new PersistableBundle(bundle);

        assertEquals(bundle.toMap(10), copy.toMap(10));
    }

    @Test
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void testPersistableBundleConstructor() {
        android.os.PersistableBundle platformBundle = new android.os.PersistableBundle();
        platformBundle.putString("string", "string");
        platformBundle.putInt("int", 0);
        platformBundle.putLong("long", 0);
        platformBundle.putDouble("double", 0);
        platformBundle.putStringArray("string_array", new String[]{"one", "two", "three"});
        platformBundle.putIntArray("int_array", new int[]{1, 2, 3});
        platformBundle.putLongArray("long_array", new long[]{1, 2, 3});
        platformBundle.putDoubleArray("double_array", new double[]{1, 2, 3});

        PersistableBundle bundle = new PersistableBundle(platformBundle);
        assertEquals(platformBundle.getString("string"), bundle.getString("string"));
        assertEquals(platformBundle.getInt("int"), bundle.getInt("int"));
        assertEquals(platformBundle.getLong("long"), bundle.getLong("long"));
        assertEquals(platformBundle.getDouble("double"), bundle.getDouble("double"), 0.01);
        assertArrayEquals(platformBundle.getStringArray("string_array"), bundle.getStringArray("string_array"));
        assertArrayEquals(platformBundle.getIntArray("int_array"), bundle.getIntArray("int_array"));
        assertArrayEquals(platformBundle.getLongArray("long_array"), bundle.getLongArray("long_array"));
        assertArrayEquals(platformBundle.getDoubleArray("double_array"), bundle.getDoubleArray("double_array"), 0.01);
    }

    @Test
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void testBundleConstructor() {
        Bundle platformBundle = new Bundle();
        platformBundle.putString("string", "string");
        platformBundle.putInt("int", 0);
        platformBundle.putLong("long", 0);
        platformBundle.putDouble("double", 0);
        platformBundle.putStringArray("string_array", new String[]{"one", "two", "three"});
        platformBundle.putIntArray("int_array", new int[]{1, 2, 3});
        platformBundle.putLongArray("long_array", new long[]{1, 2, 3});
        platformBundle.putDoubleArray("double_array", new double[]{1, 2, 3});

        PersistableBundle bundle = new PersistableBundle(platformBundle);
        assertEquals(platformBundle.getString("string"), bundle.getString("string"));
        assertEquals(platformBundle.getInt("int"), bundle.getInt("int"));
        assertEquals(platformBundle.getLong("long"), bundle.getLong("long"));
        assertEquals(platformBundle.getDouble("double"), bundle.getDouble("double"), 0.01);
        assertArrayEquals(platformBundle.getStringArray("string_array"), bundle.getStringArray("string_array"));
        assertArrayEquals(platformBundle.getIntArray("int_array"), bundle.getIntArray("int_array"));
        assertArrayEquals(platformBundle.getLongArray("long_array"), bundle.getLongArray("long_array"));
        assertArrayEquals(platformBundle.getDoubleArray("double_array"), bundle.getDoubleArray("double_array"), 0.01);
    }

    @Test
    public void testMapConstructor() {
        Map<String, Object> map = new HashMap<>();
        map.put("string", "string");
        map.put("int", 0);
        map.put("long", 0);
        map.put("double", 0);
        map.put("string_array", new String[]{"one", "two", "three"});
        map.put("int_array", new int[]{1, 2, 3});
        map.put("long_array", new long[]{1, 2, 3});
        map.put("double_array", new double[]{1, 2, 3});
        map.put("map", new HashMap<String, Object>() {{
            put("int", 0);
        }});

        assertEquals(map, new PersistableBundle(map, 10).toMap(10));
        map.remove("map");
        assertEquals(map, new PersistableBundle(map, 1).toMap(1));
    }

    @Test
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void testPersistableBundleBooleans() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean("boolean", true);
        bundle.putBooleanArray("boolean_array", new boolean[]{true, false, true});

        android.os.PersistableBundle platformBundle = bundle.toPersistableBundle();

        PersistableBundle convertedBundle = new PersistableBundle(platformBundle);

        assertEquals(bundle.getBoolean("boolean"), convertedBundle.getBoolean("boolean"));
        assertArrayEquals(bundle.getBooleanArray("boolean_array"), convertedBundle.getBooleanArray("boolean_array"));
    }

    @Test
    @Config(constants = BuildConfig.class, sdk = Build.VERSION_CODES.LOLLIPOP_MR1)
    public void testPersistableBundleBooleansOnLollipop() {
        testPersistableBundleBooleans();
    }

    @Test
    public void testProxyMethods() {
        PersistableBundle bundle = new PersistableBundle();

        assertTrue(bundle.isEmpty());
        assertFalse(bundle.containsKey("test"));
        assertFalse(bundle.containsKey("ok"));

        bundle.putString("test", "ok");

        assertFalse(bundle.isEmpty());
        assertTrue(bundle.containsKey("test"));
        assertFalse(bundle.containsKey("ok"));
        assertEquals("ok", bundle.get("test"));
    }

    @Test
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void testPersistableBundleConversion() {
        PersistableBundle bundle = getFilledBundle(10);
        PersistableBundle convertedBundle = new PersistableBundle(bundle.toPersistableBundle());

        assertEquals(bundle.toMap(10), convertedBundle.toMap(10));
    }

    @Test
    public void testBundleConversion() {
        PersistableBundle bundle = getFilledBundle(10);
        PersistableBundle convertedBundle = new PersistableBundle(bundle.toBundle());

        assertEquals(bundle.toMap(10), convertedBundle.toMap(10));
    }

    @Test
    public void testMapConversion() {
        PersistableBundle bundle = getFilledBundle(10);
        PersistableBundle convertedBundle = new PersistableBundle(bundle.toMap(10), 10);

        assertEquals(bundle.toMap(10), convertedBundle.toMap(10));
    }

    @Test
    public void testParcelling() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("string", "string");
        bundle.putInt("int", 0);
        bundle.putLong("long", 0);
        bundle.putBoolean("boolean", true);
        // Can't use double or any array, as the instances would be different and equals() would fail.
        Parcel parcel = Parcel.obtain();
        parcel.writeValue(bundle);
        byte[] bytes = parcel.marshall();
        parcel.recycle();
        parcel = Parcel.obtain();
        parcel.unmarshall(bytes, 0, bytes.length);
        parcel.setDataPosition(0);
        PersistableBundle parcelledBundle =
                (PersistableBundle) parcel.readValue(PersistableBundle.class.getClassLoader());

        assertEquals(bundle.toMap(1), parcelledBundle.toMap(1));
    }

    private PersistableBundle getFilledBundle(int depth) {
        PersistableBundle persistableBundle = new PersistableBundle();
        PersistableBundle currentBundle = persistableBundle;
        for (int i = 0; i < depth; i++) {
            if (i > 0) {
                PersistableBundle innerBundle = new PersistableBundle();
                currentBundle.putPersistableBundleCompat("bundle", innerBundle);
                currentBundle = innerBundle;
            }
            currentBundle.putString("string", "string");
            currentBundle.putInt("int", 0);
            currentBundle.putLong("long", 0);
            currentBundle.putDouble("double", 0);
            currentBundle.putBoolean("boolean", true);
            currentBundle.putStringArray("string_array", new String[]{"one", "two", "three"});
            currentBundle.putIntArray("int_array", new int[]{1, 2, 3});
            currentBundle.putLongArray("long_array", new long[]{1, 2, 3});
            currentBundle.putDoubleArray("double_array", new double[]{1, 2, 3});
            currentBundle.putBooleanArray("boolean_array", new boolean[]{true, false, true});
        }
        return persistableBundle;
    }
}