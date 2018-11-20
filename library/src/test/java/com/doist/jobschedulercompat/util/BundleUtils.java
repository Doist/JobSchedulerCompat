package com.doist.jobschedulercompat.util;

import android.os.Bundle;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class BundleUtils {
    public static Map<String, ?> toMap(Bundle bundle, int depth) {
        if (depth <= 0) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        for (String key : bundle.keySet()) {
            Object object = map.get(key);
            if (object instanceof Bundle) {
                map.put(key, toMap(((Bundle) object), depth - 1));
            } else {
                map.put(key, object);
            }
        }
        return map;
    }
}
