package io.github.oyuha12.hyundaimotors.facility;

import ai.pleos.playground.navi.constants.NaviErrorCode;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import io.github.oyuha12.hyundaimotors.R;

/**
 * Where our route request stands in the vehicle's navigation app, from NaviHelper events.
 * Plain Java on the main thread, so the order of events can be checked in unit tests.
 *
 * <p>Every request first cancels any route the navigation app is still guiding. The
 * cancellation event that answers it is ours, not the driver's, so it is not shown. Events
 * before our first request (a route the driver set) are ignored.
 */
public final class RouteSession {
    public enum Phase {
        IDLE,
        /** NaviHelper is not connected. */
        UNAVAILABLE,
        /** There was no place to route to, for example a video place still marked TODO. */
        NO_DESTINATION,
        REQUESTED,
        /** onRouteStarted: the navigation app began the route. */
        STARTED,
        /** onRoutingCompleted: the route is found and guided. */
        GUIDING,
        ARRIVED,
        /** onRouteCancelled, other than our own cancel before a request. */
        CANCELLED,
        /** onError, or the request itself failed. */
        FAILED
    }

    private Phase phase = Phase.IDLE;
    @Nullable private NaviErrorCode error;
    @Nullable private String destination;
    private boolean ownCancelPending;

    /** We cancelled the old route and asked for this one. */
    public void onRequested(String destination) {
        this.destination = destination;
        phase = Phase.REQUESTED;
        error = null;
        ownCancelPending = true;
    }

    /** The request could not be sent, for example without permission. */
    public void onRequestFailed() {
        phase = Phase.FAILED;
        error = null;
        ownCancelPending = false;
    }

    public void onUnavailable() {
        phase = Phase.UNAVAILABLE;
        error = null;
        ownCancelPending = false;
    }

    public void onNoDestination() {
        destination = null;
        phase = Phase.NO_DESTINATION;
        error = null;
        ownCancelPending = false;
    }

    public void onRouteStarted() {
        if (destination == null) return;
        ownCancelPending = false;
        phase = Phase.STARTED;
        error = null;
    }

    public void onRoutingCompleted() {
        if (destination == null) return;
        ownCancelPending = false;
        phase = Phase.GUIDING;
        error = null;
    }

    /** @return whether the cancellation is shown: false when it answers our own cancel */
    public boolean onRouteCancelled() {
        if (destination == null) return false;
        if (ownCancelPending) {
            ownCancelPending = false;
            return false;
        }
        phase = Phase.CANCELLED;
        return true;
    }

    public void onDestinationArrived() {
        if (destination == null) return;
        ownCancelPending = false;
        phase = Phase.ARRIVED;
    }

    public void onError(NaviErrorCode code) {
        if (destination == null) return;
        ownCancelPending = false;
        phase = Phase.FAILED;
        error = code;
    }

    /** Forget the route, for a new situation. The navigation app keeps whatever it guides. */
    public void clear() {
        phase = Phase.IDLE;
        error = null;
        destination = null;
        ownCancelPending = false;
    }

    public Phase phase() {
        return phase;
    }

    @Nullable
    public NaviErrorCode error() {
        return error;
    }

    @Nullable
    public String destination() {
        return destination;
    }

    /** Our route is requested, started or guided: there is something to cancel. */
    public boolean isActive() {
        return phase == Phase.REQUESTED || isGuiding();
    }

    /** While the navigation app guides our route, turn-by-turn information is worth asking for. */
    public boolean isGuiding() {
        return phase == Phase.STARTED || phase == Phase.GUIDING;
    }

    /** What the screen says now, or 0 for nothing. */
    @StringRes
    public int message() {
        switch (phase) {
            case UNAVAILABLE:
                return R.string.route_failed;
            case NO_DESTINATION:
                return R.string.route_no_destination;
            case REQUESTED:
                return R.string.route_requested;
            case STARTED:
                return R.string.route_started;
            case GUIDING:
                return R.string.route_guiding;
            case ARRIVED:
                return R.string.route_arrived;
            case CANCELLED:
                return R.string.route_cancelled;
            case FAILED:
                return error == null ? R.string.route_request_failed : errorMessage(error);
            case IDLE:
            default:
                return 0;
        }
    }

    /** Each NaviErrorCode in words. */
    @StringRes
    public static int errorMessage(NaviErrorCode code) {
        switch (code) {
            case CAUSE_LOW_BATTERY:
                return R.string.route_error_low_battery;
            case CAUSE_SEARCH_ROUTE_OVER_DISTANCE:
                return R.string.route_error_too_far;
            case CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE:
                return R.string.route_error_too_close;
            case CAUSE_SEARCH_TRIP:
                return R.string.route_error_trip;
            case CAUSE_SEARCH_ROUTE:
            default:
                return R.string.route_error_search;
        }
    }
}
