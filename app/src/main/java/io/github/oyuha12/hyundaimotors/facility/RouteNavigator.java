package io.github.oyuha12.hyundaimotors.facility;

import ai.pleos.playground.navi.constants.NaviErrorCode;
import ai.pleos.playground.navi.constants.NavigationStatus;
import ai.pleos.playground.navi.constants.RouteOption;
import ai.pleos.playground.navi.data.DestinationArrivedInfo;
import ai.pleos.playground.navi.data.RouteInfo;
import ai.pleos.playground.navi.data.RouteStartInfo;
import ai.pleos.playground.navi.data.TBTInfo;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.List;
import kotlin.Unit;

/**
 * Sends destinations to the vehicle's navigation app through Pleos NaviHelper, which draws and
 * guides the route, and follows its events: started, guiding, cancelled, arrived, or failed with
 * a NaviErrorCode ({@link RouteSession}). While our route is guided it asks for turn-by-turn
 * information. Everything is logged under the tag "RouteNavigator" for checks in Logcat.
 *
 * <p>Requires pleos.car.permission.NAVI_ROUTE_SEARCH (requestRoute, cancelRoute) and
 * pleos.car.permission.NAVI_ROUTE (getTBTInfo), both normal permissions in the manifest.
 */
public final class RouteNavigator {
    private static final String TAG = "RouteNavigator";
    /** How often to ask for turn-by-turn information while a route is guided. */
    private static final long TBT_POLL_MILLIS = 5_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final RouteSession session = new RouteSession();
    /** What the screen says about the route, as a string resource, or 0. */
    private final MutableLiveData<Integer> message = new MutableLiveData<>(0);
    /** The next turn while our route is guided, or null. */
    private final MutableLiveData<TBTInfo> nextTurn = new MutableLiveData<>();
    @Nullable private NaviHelper naviHelper;
    private boolean connected;

    // NaviHelper may call these on any thread; the session lives on the main thread.
    private final NaviHelperEventListener events = new NaviHelperEventListener() {
        @Override
        public void onRouteStarted(RouteStartInfo info) {
            Log.i(TAG, "onRouteStarted: " + info.getName() + ", " + info.getDistance() + " m, "
                    + info.getDuration() + " s");
            handler.post(() -> {
                session.onRouteStarted();
                publish();
            });
        }

        @Override
        public void onRoutingCompleted() {
            Log.i(TAG, "onRoutingCompleted");
            handler.post(() -> {
                session.onRoutingCompleted();
                publish();
            });
        }

        @Override
        public void onRouteCancelled() {
            handler.post(() -> {
                boolean shown = session.onRouteCancelled();
                Log.i(TAG, shown ? "onRouteCancelled" : "onRouteCancelled (our cancel before a request)");
                publish();
            });
        }

        @Override
        public void onDestinationArrived(DestinationArrivedInfo info) {
            Log.i(TAG, "onDestinationArrived: " + info.getName());
            handler.post(() -> {
                session.onDestinationArrived();
                publish();
            });
        }

        @Override
        public void onError(NaviErrorCode code) {
            Log.w(TAG, "onError: " + code);
            handler.post(() -> {
                session.onError(code);
                publish();
            });
        }

        @Override
        public void onNavigationStatus(NavigationStatus status) {
            Log.i(TAG, "onNavigationStatus: " + status);
        }

        @Override
        public void onTBTInfo(List<TBTInfo> turns) {
            TBTInfo next = TurnGuide.next(turns);
            handler.post(() -> {
                if (!session.isGuiding()) return;
                if (next != null) {
                    Log.d(TAG, "onTBTInfo: " + next.getType() + " in " + next.getDistance() + " m");
                }
                nextTurn.setValue(next);
            });
        }
    };

    private final Runnable pollTurns = new Runnable() {
        @Override
        public void run() {
            if (naviHelper == null || !connected || !session.isGuiding()) return;
            try {
                naviHelper.getTBTInfo();
            } catch (RuntimeException error) {
                Log.w(TAG, "getTBTInfo failed", error);
                return;
            }
            handler.postDelayed(this, TBT_POLL_MILLIS);
        }
    };

    public RouteNavigator(Context context) {
        try {
            naviHelper = new NaviHelper(context.getApplicationContext());
            naviHelper.initialize(
                    () -> {
                        Log.i(TAG, "NaviHelper initialize: connected");
                        handler.post(() -> {
                            connected = true;
                            naviHelper.addListener(events);
                        });
                        return Unit.INSTANCE;
                    },
                    error -> {
                        Log.w(TAG, "NaviHelper initialize: failed", error);
                        return Unit.INSTANCE;
                    });
        } catch (RuntimeException | LinkageError error) {
            Log.w(TAG, "NaviHelper unavailable", error);
        }
    }

    /** What the screen says about the route, as a string resource, or 0. */
    public LiveData<Integer> getMessage() {
        return message;
    }

    /** The next turn while our route is guided, or null. */
    public LiveData<TBTInfo> getNextTurn() {
        return nextTurn;
    }

    /** Forgets the route on screen, for a new situation. The navigation app is not touched. */
    public void clearStatus() {
        session.clear();
        publish();
    }

    /**
     * Ends the route we asked for, if it is still going: the navigation app drops the
     * destination and the screen forgets it. A route the driver set alone is left alone.
     */
    public void endRoute() {
        if (session.isActive() && naviHelper != null && connected) {
            try {
                naviHelper.cancelRoute();
                Log.i(TAG, "cancelRoute: situation ended");
            } catch (RuntimeException error) {
                Log.w(TAG, "cancelRoute failed", error);
            }
        }
        session.clear();
        publish();
    }

    /** There is no place to route to, for example a video place still marked TODO. */
    public void reportNoDestination() {
        Log.i(TAG, "requestRoute skipped: no destination set");
        session.onNoDestination();
        publish();
    }

    /**
     * Clears any destination the navigation app still has, then asks for a route to this place,
     * with its name and, if known, its address.
     */
    public void navigateTo(Facility facility) {
        if (naviHelper == null || !connected) {
            Log.w(TAG, "requestRoute skipped: NaviHelper not connected");
            session.onUnavailable();
            publish();
            return;
        }
        RouteInfo route = new RouteInfo(
                facility.latitude,
                facility.longitude,
                null,
                null,
                facility.name,
                "",
                facility.address == null ? "" : facility.address,
                "0",
                RouteOption.RECOMMENDED,
                "",
                false);
        try {
            naviHelper.cancelRoute();
            session.onRequested(facility.name);
            naviHelper.requestRoute(route);
            Log.i(TAG, "requestRoute: " + facility.name + " (" + facility.latitude + ", "
                    + facility.longitude + ")");
        } catch (RuntimeException error) {
            Log.w(TAG, "requestRoute failed", error);
            session.onRequestFailed();
        }
        publish();
    }

    private void publish() {
        message.setValue(session.message());
        handler.removeCallbacks(pollTurns);
        if (session.isGuiding()) {
            handler.post(pollTurns);
        } else {
            nextTurn.setValue(null);
        }
    }

    public void release() {
        handler.removeCallbacksAndMessages(null);
        if (naviHelper == null) return;
        try {
            if (connected) naviHelper.removeListener(events);
            naviHelper.release();
        } catch (RuntimeException error) {
            Log.w(TAG, "NaviHelper release failed", error);
        }
    }
}
