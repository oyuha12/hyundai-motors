package io.github.oyuha12.hyundaimotors.facility;

import ai.pleos.playground.navi.constants.NaviErrorCode;
import ai.pleos.playground.navi.constants.RouteOption;
import ai.pleos.playground.navi.data.RouteInfo;
import ai.pleos.playground.navi.data.RouteStartInfo;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import android.content.Context;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import kotlin.Unit;

/** Sends destinations to the vehicle's navigation app, which draws and guides the route. */
public final class RouteNavigator {
    public enum Status { IDLE, REQUESTED, STARTED, FAILED }

    private static final String TAG = "RouteNavigator";

    private final MutableLiveData<Status> status = new MutableLiveData<>(Status.IDLE);
    @Nullable private NaviHelper naviHelper;
    private boolean connected;

    private final NaviHelperEventListener events = new NaviHelperEventListener() {
        @Override
        public void onRouteStarted(RouteStartInfo info) {
            status.postValue(Status.STARTED);
        }

        @Override
        public void onError(NaviErrorCode code) {
            Log.w(TAG, "Route error: " + code);
            status.postValue(Status.FAILED);
        }
    };

    public RouteNavigator(Context context) {
        try {
            naviHelper = new NaviHelper(context.getApplicationContext());
            naviHelper.initialize(
                    () -> {
                        connected = true;
                        naviHelper.addListener(events);
                        return Unit.INSTANCE;
                    },
                    error -> {
                        Log.w(TAG, "NaviHelper unavailable", error);
                        return Unit.INSTANCE;
                    });
        } catch (RuntimeException | LinkageError error) {
            Log.w(TAG, "NaviHelper unavailable", error);
        }
    }

    public LiveData<Status> getStatus() {
        return status;
    }

    public void clearStatus() {
        status.setValue(Status.IDLE);
    }

    public void navigateTo(Facility facility) {
        if (naviHelper == null || !connected) {
            status.setValue(Status.FAILED);
            return;
        }
        status.setValue(Status.REQUESTED);
        try {
            naviHelper.requestRoute(new RouteInfo(
                    facility.latitude,
                    facility.longitude,
                    null,
                    null,
                    facility.name,
                    "",
                    "",
                    "0",
                    RouteOption.RECOMMENDED,
                    "",
                    false));
        } catch (RuntimeException error) {
            Log.w(TAG, "Route request failed", error);
            status.setValue(Status.FAILED);
        }
    }

    public void release() {
        if (naviHelper == null) return;
        try {
            if (connected) naviHelper.removeListener(events);
            naviHelper.release();
        } catch (RuntimeException error) {
            Log.w(TAG, "NaviHelper release failed", error);
        }
    }
}
