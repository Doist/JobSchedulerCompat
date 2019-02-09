package com.doist.jobschedulercompat.util;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import android.net.NetworkInfo;

import static org.robolectric.Shadows.shadowOf;

@Implements(NetworkInfo.class)
public class ShadowNetworkInfo extends org.robolectric.shadows.ShadowNetworkInfo {
    private boolean isRoaming;

    public static NetworkInfo newInstance(
            NetworkInfo.DetailedState detailedState, int type, int subType, boolean isAvailable, boolean isConnected,
            boolean isRoaming) {
        NetworkInfo networkInfo = org.robolectric.shadows.ShadowNetworkInfo.newInstance(
                detailedState, type, subType, isAvailable,
                isConnected ? NetworkInfo.State.CONNECTED : NetworkInfo.State.DISCONNECTED);
        ShadowNetworkInfo info = (ShadowNetworkInfo) shadowOf(networkInfo);
        info.setRoaming(isRoaming);
        return networkInfo;
    }

    public ShadowNetworkInfo() {
    }

    @Implementation
    public boolean isRoaming() {
        return isRoaming;
    }

    public void setRoaming(boolean isRoaming) {
        this.isRoaming = isRoaming;
    }
}
