package io.github.oyuha12.hyundaimotors.facility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import ai.pleos.playground.navi.constants.NaviErrorCode;
import io.github.oyuha12.hyundaimotors.R;
import io.github.oyuha12.hyundaimotors.facility.RouteSession.Phase;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** NaviHelper events in the orders the navigation app may send them. */
public class RouteSessionTest {
    private final RouteSession session = new RouteSession();

    @Test
    public void fullRoute_requestedStartedGuidedArrived() {
        assertEquals(0, session.message());
        session.onRequested("대피 장소");
        assertEquals(Phase.REQUESTED, session.phase());
        assertEquals(R.string.route_requested, session.message());
        session.onRouteStarted();
        assertEquals(R.string.route_started, session.message());
        assertTrue(session.isGuiding());
        session.onRoutingCompleted();
        assertEquals(R.string.route_guiding, session.message());
        assertTrue(session.isGuiding());
        session.onDestinationArrived();
        assertEquals(R.string.route_arrived, session.message());
        assertFalse(session.isGuiding());
    }

    /** The cancel we send before each request is answered by onRouteCancelled: not shown. */
    @Test
    public void ownCancelBeforeRequest_isNotShown() {
        session.onRequested("대피 장소");
        assertFalse(session.onRouteCancelled());
        assertEquals(Phase.REQUESTED, session.phase());
        session.onRouteStarted();
        assertEquals(Phase.STARTED, session.phase());
    }

    /** Once the new route has begun, a cancellation is the driver's and is shown. */
    @Test
    public void cancelAfterStart_isShown() {
        session.onRequested("대피 장소");
        session.onRouteStarted();
        assertTrue(session.onRouteCancelled());
        assertEquals(R.string.route_cancelled, session.message());
        assertFalse(session.isGuiding());
    }

    /** A second request starts over, again expecting its own cancel. */
    @Test
    public void secondRequest_expectsItsOwnCancelAgain() {
        session.onRequested("경찰서");
        session.onRouteCancelled();
        session.onRoutingCompleted();
        session.onRequested("소방서");
        assertFalse(session.onRouteCancelled());
        assertEquals("소방서", session.destination());
        assertEquals(Phase.REQUESTED, session.phase());
    }

    @Test
    public void errors_haveTheirOwnWords() {
        Set<Integer> messages = new HashSet<>();
        for (NaviErrorCode code : NaviErrorCode.values()) {
            session.onRequested("대피 장소");
            session.onError(code);
            assertEquals(Phase.FAILED, session.phase());
            assertEquals(code, session.error());
            assertEquals(RouteSession.errorMessage(code), session.message());
            messages.add(session.message());
        }
        assertEquals(NaviErrorCode.values().length, messages.size());
        assertEquals(R.string.route_error_too_close,
                RouteSession.errorMessage(NaviErrorCode.CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE));
    }

    @Test
    public void requestThatCannotBeSent() {
        session.onRequested("대피 장소");
        session.onRequestFailed();
        assertNull(session.error());
        assertEquals(R.string.route_request_failed, session.message());
    }

    @Test
    public void notConnected_orNoPlace() {
        session.onUnavailable();
        assertEquals(R.string.route_failed, session.message());
        session.onNoDestination();
        assertEquals(R.string.route_no_destination, session.message());
    }

    /** A route the driver set in the navigation app before ours changes nothing on our screen. */
    @Test
    public void eventsBeforeOurRequest_areIgnored() {
        session.onRouteStarted();
        session.onRoutingCompleted();
        assertFalse(session.onRouteCancelled());
        session.onError(NaviErrorCode.CAUSE_SEARCH_ROUTE);
        session.onDestinationArrived();
        assertEquals(Phase.IDLE, session.phase());
        assertEquals(0, session.message());
    }

    /** Something to cancel at "상황 종료" only while our route is requested or guided. */
    @Test
    public void active_whileRequestedOrGuided() {
        assertFalse(session.isActive());
        session.onRequested("대피 장소");
        assertTrue(session.isActive());
        session.onRoutingCompleted();
        assertTrue(session.isActive());
        session.onDestinationArrived();
        assertFalse(session.isActive());
        session.onRequested("경찰서");
        session.onError(NaviErrorCode.CAUSE_SEARCH_ROUTE);
        assertFalse(session.isActive());
    }

    @Test
    public void clear_forgetsTheRoute() {
        session.onRequested("대피 장소");
        session.onRoutingCompleted();
        session.clear();
        assertEquals(Phase.IDLE, session.phase());
        assertNull(session.destination());
        session.onRouteStarted();
        assertEquals(Phase.IDLE, session.phase());
    }
}
