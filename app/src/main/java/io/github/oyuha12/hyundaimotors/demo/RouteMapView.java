package io.github.oyuha12.hyundaimotors.demo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import io.github.oyuha12.hyundaimotors.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Draws a {@link DemoRoute} on a made-up city grid: blocks between roads, wider main roads with
 * lane marks, the route, the destination, pins for nearby police and fire stations, and the
 * car's position and direction. Map tiles are not needed, so it works on any emulator.
 *
 * <p>Video mode draws the same city as a car navigation's driving view instead: see
 * {@link #setFirstPerson} and {@link FirstPersonCamera}.
 */
public final class RouteMapView extends View {
    private static final float GRID_METERS = 100f;
    /** Every fourth road is a main road: wider, with a dashed center line. */
    private static final int MAIN_ROAD_EVERY = 4;

    /** A place pinned on the map, in meters from the car: x to the east, y to the north. */
    public static final class Pin {
        final float x;
        final float y;
        @DrawableRes final int icon;
        @ColorRes final int color;

        public Pin(float eastMeters, float northMeters, @DrawableRes int icon, @ColorRes int color) {
            this.x = eastMeters;
            this.y = northMeters;
            this.icon = icon;
            this.color = color;
        }
    }

    private final Paint road = new Paint();
    private final Paint street = new Paint();
    private final Paint block = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint laneMark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cell = new RectF();
    private final int pinIconTint;
    private final Paint routeCasing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint routeLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint travelled = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint destination = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint destinationRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint car = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint carOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float density;

    // First-person view, video mode only. Sizes on the ground are meters.
    /** The view turns from one leg to the next over this many meters before and after a corner. */
    private static final float VIEW_TURN_METERS = 35f;
    /** The car arrow turns faster than the view, so it leads into the turn. */
    private static final float ARROW_TURN_METERS = 12f;
    private static final float SIDE_ROAD_METERS = 10f;
    private static final float MAIN_ROAD_METERS = 18f;
    /** Every leg of the route runs on a road of its own this wide. */
    private static final float ROUTE_ROAD_METERS = 16f;
    private static final float ROUTE_METERS = 8f;
    private static final float ROUTE_CASING_METERS = 12f;
    private static final float DASH_METERS = 8f;
    private static final float DASH_WIDTH_METERS = 0.8f;
    private final FirstPersonCamera camera = new FirstPersonCamera();
    private final Paint groundRoad = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint groundBlock = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint groundMark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint groundCasing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint groundRoute = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint groundTravelled = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint turnArrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sky = new Paint();
    private final Paint carShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint carFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint carEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int skyColor;
    private final Path ground = new Path();
    private final float[] corners = new float[2 * FirstPersonCamera.MAX_CORNERS];
    private final float[] screen = new float[2 * (FirstPersonCamera.MAX_CORNERS + 2)];
    private final float[] point = new float[2];
    private boolean firstPerson;

    @Nullable private DemoRoute route;
    private float progressMeters;
    private List<Pin> pins = Collections.emptyList();

    public RouteMapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        road.setColor(ContextCompat.getColor(context, R.color.surface));
        street.setStyle(Paint.Style.STROKE);
        street.setColor(ContextCompat.getColor(context, R.color.surface));
        block.setColor(ContextCompat.getColor(context, R.color.surface_variant));
        laneMark.setStyle(Paint.Style.STROKE);
        laneMark.setStrokeWidth(2 * density);
        laneMark.setColor(ContextCompat.getColor(context, R.color.on_surface_variant));
        laneMark.setAlpha(110);
        laneMark.setPathEffect(new DashPathEffect(new float[] {12 * density, 10 * density}, 0));
        pinIconTint = ContextCompat.getColor(context, R.color.on_primary);
        routeCasing.setStyle(Paint.Style.STROKE);
        routeCasing.setStrokeWidth(16 * density);
        routeCasing.setStrokeCap(Paint.Cap.ROUND);
        routeCasing.setStrokeJoin(Paint.Join.ROUND);
        routeCasing.setColor(ContextCompat.getColor(context, R.color.surface));
        routeLine.set(routeCasing);
        routeLine.setStrokeWidth(10 * density);
        routeLine.setColor(ContextCompat.getColor(context, R.color.primary));
        travelled.set(routeLine);
        travelled.setColor(ContextCompat.getColor(context, R.color.outline));
        destination.setColor(ContextCompat.getColor(context, R.color.negative));
        destinationRing.setColor(ContextCompat.getColor(context, R.color.on_emergency));
        car.setColor(ContextCompat.getColor(context, R.color.primary));
        carOutline.setColor(ContextCompat.getColor(context, R.color.on_primary));
        carOutline.setStyle(Paint.Style.STROKE);
        carOutline.setStrokeWidth(3 * density);
        carOutline.setStrokeJoin(Paint.Join.ROUND);

        groundRoad.setColor(ContextCompat.getColor(context, R.color.route_road));
        groundBlock.setColor(ContextCompat.getColor(context, R.color.route_block));
        groundMark.setColor(ContextCompat.getColor(context, R.color.route_lane_mark));
        groundCasing.setColor(ContextCompat.getColor(context, R.color.on_primary));
        groundRoute.setColor(ContextCompat.getColor(context, R.color.primary));
        groundTravelled.setColor(ContextCompat.getColor(context, R.color.route_travelled));
        turnArrow.setColor(ContextCompat.getColor(context, R.color.on_primary));
        skyColor = ContextCompat.getColor(context, R.color.route_sky);
        carShadow.setColor(ContextCompat.getColor(context, R.color.route_car_shadow));
        carFill.setColor(ContextCompat.getColor(context, R.color.on_primary));
        carEdge.setColor(ContextCompat.getColor(context, R.color.primary));
        carEdge.setStyle(Paint.Style.STROKE);
        carEdge.setStrokeWidth(5 * density);
        carEdge.setStrokeJoin(Paint.Join.ROUND);
    }

    /**
     * Video mode: a car navigation's driving view, turned so the direction of travel is up and
     * tilted toward the horizon, following the car. Off: the whole route from above, north up.
     */
    public void setFirstPerson(boolean on) {
        firstPerson = on;
        invalidate();
    }

    public void setRoute(DemoRoute route) {
        this.route = route;
        progressMeters = 0f;
        invalidate();
    }

    public void setProgress(float meters) {
        progressMeters = meters;
        invalidate();
    }

    /** Places to pin, such as police and fire stations near the route. */
    public void setPins(List<Pin> pins) {
        this.pins = new ArrayList<>(pins);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (firstPerson && route != null) {
            drawFirstPerson(canvas, route);
            return;
        }
        canvas.drawRect(0, 0, getWidth(), getHeight(), road);
        if (route == null) return;

        // Fit the route and the pins with a margin, keeping meters square.
        float minX = 0, maxX = 0, minY = 0, maxY = 0;
        for (DemoRoute.Point p : route.points()) {
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
        }
        for (Pin pin : pins) {
            minX = Math.min(minX, pin.x);
            maxX = Math.max(maxX, pin.x);
            minY = Math.min(minY, pin.y);
            maxY = Math.max(maxY, pin.y);
        }
        float margin = 48 * density;
        float spanX = Math.max(maxX - minX, 200f);
        float spanY = Math.max(maxY - minY, 200f);
        float scale = Math.min((getWidth() - 2 * margin) / spanX, (getHeight() - 2 * margin) / spanY);
        float centerX = (minX + maxX) / 2f;
        float centerY = (minY + maxY) / 2f;
        float originX = getWidth() / 2f - centerX * scale;
        float originY = getHeight() / 2f + centerY * scale; // North is up.

        drawGrid(canvas, scale, originX, originY);

        path.reset();
        boolean first = true;
        for (DemoRoute.Point p : route.points()) {
            float sx = originX + p.x * scale;
            float sy = originY - p.y * scale;
            if (first) path.moveTo(sx, sy); else path.lineTo(sx, sy);
            first = false;
        }
        canvas.drawPath(path, routeCasing);
        canvas.drawPath(path, routeLine);
        drawTravelled(canvas, scale, originX, originY);

        DemoRoute.Point end = route.positionAt(route.lengthMeters());
        float ex = originX + end.x * scale;
        float ey = originY - end.y * scale;
        canvas.drawCircle(ex, ey, 18 * density, destinationRing);
        canvas.drawCircle(ex, ey, 13 * density, destination);

        for (Pin pin : pins) {
            drawPin(canvas, pin.color, pin.icon, originX + pin.x * scale, originY - pin.y * scale, 1f);
        }

        DemoRoute.Point now = route.positionAt(progressMeters);
        drawCar(canvas, originX + now.x * scale, originY - now.y * scale, route.headingAt(progressMeters));
    }

    /**
     * City blocks on the road-colored background: narrow side roads, and every fourth road a
     * main road, wider with a dashed center line. Roads line up with the route's meters.
     */
    private void drawGrid(Canvas canvas, float scale, float originX, float originY) {
        float step = GRID_METERS * scale;
        if (step < 24 * density) step *= Math.ceil(24 * density / step);
        float sideHalf = Math.min(6 * density, step / 6);
        float mainHalf = Math.min(12 * density, step / 4);
        float corner = 6 * density;

        int firstColumn = (int) Math.floor(-originX / step) - 1;
        int lastColumn = (int) Math.ceil((getWidth() - originX) / step) + 1;
        int firstRow = (int) Math.floor((originY - getHeight()) / step) - 1;
        int lastRow = (int) Math.ceil(originY / step) + 1;
        for (int k = firstColumn; k < lastColumn; k++) {
            float left = originX + k * step + (isMainRoad(k) ? mainHalf : sideHalf);
            float right = originX + (k + 1) * step - (isMainRoad(k + 1) ? mainHalf : sideHalf);
            for (int m = firstRow; m < lastRow; m++) {
                // Row m runs north from y = m steps; screen y grows to the south.
                float bottom = originY - m * step - (isMainRoad(m) ? mainHalf : sideHalf);
                float top = originY - (m + 1) * step + (isMainRoad(m + 1) ? mainHalf : sideHalf);
                cell.set(left, top, right, bottom);
                canvas.drawRoundRect(cell, corner, corner, block);
            }
        }
        for (int k = firstColumn; k <= lastColumn; k++) {
            if (!isMainRoad(k)) continue;
            float x = originX + k * step;
            canvas.drawLine(x, 0, x, getHeight(), laneMark);
        }
        for (int m = firstRow; m <= lastRow; m++) {
            if (!isMainRoad(m)) continue;
            float y = originY - m * step;
            canvas.drawLine(0, y, getWidth(), y, laneMark);
        }

        // The route's corners need not fall on the grid, so each leg gets a street of its own
        // across the map: the route never runs through a block.
        street.setStrokeWidth(2 * mainHalf);
        List<DemoRoute.Point> points = route.points();
        for (int i = 1; i < points.size(); i++) {
            DemoRoute.Point a = points.get(i - 1), b = points.get(i);
            if (a.x == b.x) {
                float x = originX + a.x * scale;
                canvas.drawLine(x, 0, x, getHeight(), street);
            } else if (a.y == b.y) {
                float y = originY - a.y * scale;
                canvas.drawLine(0, y, getWidth(), y, street);
            }
        }
    }

    private static boolean isMainRoad(int index) {
        return Math.floorMod(index, MAIN_ROAD_EVERY) == 0;
    }

    /**
     * A map pin: a colored head with the place's icon, pointing at the place. {@code icon} 0
     * draws a white dot instead.
     */
    private void drawPin(Canvas canvas, @ColorRes int color, @DrawableRes int iconRes,
            float x, float y, float size) {
        float radius = 16 * density * size;
        float headY = y - radius - 10 * density * size;
        pinPaint.setColor(ContextCompat.getColor(getContext(), color));
        Path pointer = new Path();
        pointer.moveTo(x - radius * 0.6f, headY + radius * 0.6f);
        pointer.lineTo(x, y);
        pointer.lineTo(x + radius * 0.6f, headY + radius * 0.6f);
        pointer.close();
        canvas.drawPath(pointer, pinPaint);
        canvas.drawCircle(x, headY, radius, pinPaint);
        if (iconRes == 0) {
            pinPaint.setColor(pinIconTint);
            canvas.drawCircle(x, headY, radius * 0.4f, pinPaint);
            return;
        }
        Drawable icon = AppCompatResources.getDrawable(getContext(), iconRes);
        if (icon == null) return;
        icon = icon.mutate();
        icon.setTint(pinIconTint);
        int half = Math.round(10 * density * size);
        icon.setBounds(Math.round(x) - half, Math.round(headY) - half,
                Math.round(x) + half, Math.round(headY) + half);
        icon.draw(canvas);
    }

    private void drawTravelled(Canvas canvas, float scale, float originX, float originY) {
        if (progressMeters <= 0f) return;
        Path done = new Path();
        DemoRoute.Point start = route.points().get(0);
        done.moveTo(originX + start.x * scale, originY - start.y * scale);
        float walked = 0f;
        for (int i = 1; i < route.points().size(); i++) {
            DemoRoute.Point a = route.points().get(i - 1), b = route.points().get(i);
            float leg = (float) Math.hypot(b.x - a.x, b.y - a.y);
            if (walked + leg >= progressMeters) break;
            walked += leg;
            done.lineTo(originX + b.x * scale, originY - b.y * scale);
        }
        DemoRoute.Point now = route.positionAt(progressMeters);
        done.lineTo(originX + now.x * scale, originY - now.y * scale);
        canvas.drawPath(done, travelled);
    }

    private void drawCar(Canvas canvas, float x, float y, float headingDegrees) {
        float size = 22 * density;
        canvas.save();
        canvas.rotate(headingDegrees, x, y);
        Path arrow = new Path();
        arrow.moveTo(x, y - size);
        arrow.lineTo(x + size * 0.75f, y + size * 0.8f);
        arrow.lineTo(x, y + size * 0.35f);
        arrow.lineTo(x - size * 0.75f, y + size * 0.8f);
        arrow.close();
        canvas.drawPath(arrow, car);
        canvas.drawPath(arrow, carOutline);
        canvas.restore();
    }

    // ---- First-person view (video mode) --------------------------------------------------------

    private void drawFirstPerson(Canvas canvas, DemoRoute route) {
        float width = getWidth(), height = getHeight();
        camera.setView(width, height);
        DemoRoute.Point now = route.positionAt(progressMeters);
        float viewHeading = route.smoothHeadingAt(progressMeters, VIEW_TURN_METERS);
        camera.follow(now.x, now.y, viewHeading);

        canvas.drawRect(0, 0, width, height, groundRoad);
        drawBlocks(canvas, now);
        drawRouteRoads(canvas, route, now);
        drawGroundRoute(canvas, route);
        drawTurnArrow(canvas, route);
        drawSky(canvas, width);
        drawStandingMarks(canvas, route);

        // The car stays put near the bottom; the ground turns and slides under it.
        camera.project(now.x, now.y, point, 0);
        float arrowTurn = route.smoothHeadingAt(progressMeters, ARROW_TURN_METERS) - viewHeading;
        drawFirstPersonCar(canvas, point[0], point[1], arrowTurn);
    }

    /** City blocks, with every fourth road a main road with a dashed center line. */
    private void drawBlocks(Canvas canvas, DemoRoute.Point car) {
        float reach = FirstPersonCamera.FAR_METERS + GRID_METERS;
        int firstColumn = (int) Math.floor((car.x - reach) / GRID_METERS);
        int lastColumn = (int) Math.ceil((car.x + reach) / GRID_METERS);
        int firstRow = (int) Math.floor((car.y - reach) / GRID_METERS);
        int lastRow = (int) Math.ceil((car.y + reach) / GRID_METERS);

        ground.reset();
        for (int k = firstColumn; k < lastColumn; k++) {
            float left = k * GRID_METERS + roadHalf(k);
            float right = (k + 1) * GRID_METERS - roadHalf(k + 1);
            for (int m = firstRow; m < lastRow; m++) {
                if (!camera.mayBeVisible((k + 0.5f) * GRID_METERS, (m + 0.5f) * GRID_METERS, GRID_METERS)) {
                    continue;
                }
                float bottom = m * GRID_METERS + roadHalf(m);
                float top = (m + 1) * GRID_METERS - roadHalf(m + 1);
                addRect(left, bottom, right, top);
            }
        }
        canvas.drawPath(ground, groundBlock);

        ground.reset();
        float half = DASH_WIDTH_METERS / 2f;
        for (int k = firstColumn; k <= lastColumn; k++) {
            if (!isMainRoad(k)) continue;
            float x = k * GRID_METERS;
            for (float y = firstRow * GRID_METERS; y < lastRow * GRID_METERS; y += 2 * DASH_METERS) {
                if (camera.mayBeVisible(x, y, DASH_METERS)) addRect(x - half, y, x + half, y + DASH_METERS);
            }
        }
        for (int m = firstRow; m <= lastRow; m++) {
            if (!isMainRoad(m)) continue;
            float y = m * GRID_METERS;
            for (float x = firstColumn * GRID_METERS; x < lastColumn * GRID_METERS; x += 2 * DASH_METERS) {
                if (camera.mayBeVisible(x, y, DASH_METERS)) addRect(x, y - half, x + DASH_METERS, y + half);
            }
        }
        canvas.drawPath(ground, groundMark);
    }

    private static float roadHalf(int index) {
        return (isMainRoad(index) ? MAIN_ROAD_METERS : SIDE_ROAD_METERS) / 2f;
    }

    /** Each leg runs on a road of its own, so the route never crosses a block. */
    private void drawRouteRoads(Canvas canvas, DemoRoute route, DemoRoute.Point car) {
        float reach = FirstPersonCamera.FAR_METERS + GRID_METERS;
        float half = ROUTE_ROAD_METERS / 2f;
        ground.reset();
        List<DemoRoute.Point> points = route.points();
        for (int i = 1; i < points.size(); i++) {
            DemoRoute.Point a = points.get(i - 1), b = points.get(i);
            if (a.x == b.x) {
                addRect(a.x - half, car.y - reach, a.x + half, car.y + reach);
            } else if (a.y == b.y) {
                addRect(car.x - reach, a.y - half, car.x + reach, a.y + half);
            }
        }
        canvas.drawPath(ground, groundRoad);
    }

    /** The route on the road: white edges, gray behind the car, blue ahead. */
    private void drawGroundRoute(Canvas canvas, DemoRoute route) {
        float length = route.lengthMeters();
        drawRouteBand(canvas, route, 0f, length, ROUTE_CASING_METERS, groundCasing);
        if (progressMeters > 0f) {
            drawRouteBand(canvas, route, 0f, progressMeters, ROUTE_METERS, groundTravelled);
        }
        drawRouteBand(canvas, route, progressMeters, length, ROUTE_METERS, groundRoute);
    }

    /** The route from one distance to another as a band on the ground, square at the corners. */
    private void drawRouteBand(Canvas canvas, DemoRoute route, float from, float to, float widthMeters, Paint paint) {
        float half = widthMeters / 2f;
        ground.reset();
        List<DemoRoute.Point> points = route.points();
        float walked = 0f;
        for (int i = 1; i < points.size(); i++) {
            DemoRoute.Point a = points.get(i - 1), b = points.get(i);
            float leg = (float) Math.hypot(b.x - a.x, b.y - a.y);
            float start = Math.max(from, walked), end = Math.min(to, walked + leg);
            if (end > start && leg > 0f) {
                float dx = (b.x - a.x) / leg, dy = (b.y - a.y) / leg;
                // Corners overlap by half the width so the band has no notch there.
                float s = start - walked - (start > walked || i == 1 ? 0f : half);
                float e = end - walked + (end < walked + leg || i == points.size() - 1 ? 0f : half);
                addBand(a.x + dx * s, a.y + dy * s, a.x + dx * e, a.y + dy * e, half);
            }
            walked += leg;
        }
        canvas.drawPath(ground, paint);
    }

    /** A white arrow painted on the road through the next corner, like a navigation's turn arrow. */
    private void drawTurnArrow(Canvas canvas, DemoRoute route) {
        DemoRoute.Maneuver next = route.nextManeuver(progressMeters);
        if (next == null || next.atMeters - progressMeters > FirstPersonCamera.FAR_METERS) return;
        DemoRoute.Point corner = route.positionAt(next.atMeters);
        DemoRoute.Point before = route.positionAt(next.atMeters - 1f);
        DemoRoute.Point after = route.positionAt(next.atMeters + 1f);
        float inX = corner.x - before.x, inY = corner.y - before.y;
        float outX = after.x - corner.x, outY = after.y - corner.y;
        // Meters: the shaft's half width, its length before and after the corner, the head.
        float shaft = 2f, tail = 28f, reach = 14f, head = 10f, headHalf = 5.5f;
        ground.reset();
        addBand(corner.x - inX * tail, corner.y - inY * tail,
                corner.x + inX * shaft, corner.y + inY * shaft, shaft);
        addBand(corner.x, corner.y, corner.x + outX * reach, corner.y + outY * reach, shaft);
        float baseX = corner.x + outX * reach, baseY = corner.y + outY * reach;
        // Across the outgoing direction: (outY, -outX).
        corners[0] = baseX + outY * headHalf;
        corners[1] = baseY - outX * headHalf;
        corners[2] = baseX + outX * head;
        corners[3] = baseY + outY * head;
        corners[4] = baseX - outY * headHalf;
        corners[5] = baseY + outX * headHalf;
        addPolygon(3);
        canvas.drawPath(ground, turnArrow);
    }

    /**
     * Sky down to the far edge of the drawn ground, then fading into the ground, so the place
     * where the drawing stops does not show.
     */
    private void drawSky(Canvas canvas, float width) {
        float far = camera.screenYAhead(FirstPersonCamera.FAR_METERS);
        float fadeEnd = camera.screenYAhead(FirstPersonCamera.FAR_METERS / 2f);
        sky.setShader(new LinearGradient(0, far, 0, fadeEnd,
                skyColor, skyColor & 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, fadeEnd, sky);
    }

    /** The destination and the pins, standing up, smaller with distance, the farthest first. */
    private void drawStandingMarks(Canvas canvas, DemoRoute route) {
        List<float[]> marks = new ArrayList<>();
        DemoRoute.Point end = route.positionAt(route.lengthMeters());
        marks.add(new float[] {end.x, end.y, -1});
        for (int i = 0; i < pins.size(); i++) marks.add(new float[] {pins.get(i).x, pins.get(i).y, i});
        Collections.sort(marks, (a, b) -> Float.compare(camera.ahead(b[0], b[1]), camera.ahead(a[0], a[1])));
        float atCar = camera.pixelsPerMeter(0f);
        for (float[] mark : marks) {
            float forward = camera.ahead(mark[0], mark[1]);
            if (forward < 0f || forward > FirstPersonCamera.FAR_METERS) continue;
            camera.project(mark[0], mark[1], point, 0);
            if (point[0] < -48 * density || point[0] > getWidth() + 48 * density) continue;
            float size = Math.max(0.7f, Math.min(1.3f, 1.3f * camera.pixelsPerMeter(forward) / atCar));
            if (mark[2] < 0) {
                drawPin(canvas, R.color.negative, 0, point[0], point[1], size * 1.4f);
            } else {
                Pin pin = pins.get((int) mark[2]);
                drawPin(canvas, pin.color, pin.icon, point[0], point[1], size);
            }
        }
    }

    /** The car: a large white arrow with a blue edge and a shadow, pointing up the screen. */
    private void drawFirstPersonCar(Canvas canvas, float x, float y, float turnDegrees) {
        float size = 34 * density;
        canvas.drawOval(x - size, y - size * 0.15f, x + size, y + size * 1.05f, carShadow);
        canvas.save();
        canvas.rotate(turnDegrees, x, y);
        Path arrow = new Path();
        arrow.moveTo(x, y - size);
        arrow.lineTo(x + size * 0.75f, y + size * 0.8f);
        arrow.lineTo(x, y + size * 0.35f);
        arrow.lineTo(x - size * 0.75f, y + size * 0.8f);
        arrow.close();
        canvas.drawPath(arrow, carEdge);
        canvas.drawPath(arrow, carFill);
        canvas.restore();
    }

    /** Adds a ground rectangle, given by its west, south, east and north edges. */
    private void addRect(float west, float south, float east, float north) {
        corners[0] = west;
        corners[1] = south;
        corners[2] = east;
        corners[3] = south;
        corners[4] = east;
        corners[5] = north;
        corners[6] = west;
        corners[7] = north;
        addPolygon(4);
    }

    /** Adds a band of the given half width along the ground from (ax, ay) to (bx, by). */
    private void addBand(float ax, float ay, float bx, float by, float half) {
        float length = (float) Math.hypot(bx - ax, by - ay);
        if (length == 0f) return;
        float sideX = (by - ay) / length * half, sideY = -(bx - ax) / length * half;
        corners[0] = ax + sideX;
        corners[1] = ay + sideY;
        corners[2] = bx + sideX;
        corners[3] = by + sideY;
        corners[4] = bx - sideX;
        corners[5] = by - sideY;
        corners[6] = ax - sideX;
        corners[7] = ay - sideY;
        addPolygon(4);
    }

    /**
     * Adds the first {@code count} points of {@link #corners}, clipped and projected. Every
     * polygon goes in clockwise on screen, so overlapping ones fill as one shape and leave no
     * holes under the path's winding rule.
     */
    private void addPolygon(int count) {
        int n = camera.clipAndProject(corners, count, screen);
        if (n < 3) return;
        float area = 0f;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += screen[2 * i] * screen[2 * j + 1] - screen[2 * j] * screen[2 * i + 1];
        }
        boolean reverse = area < 0f;
        for (int k = 0; k < n; k++) {
            int i = reverse ? n - 1 - k : k;
            if (k == 0) ground.moveTo(screen[2 * i], screen[2 * i + 1]);
            else ground.lineTo(screen[2 * i], screen[2 * i + 1]);
        }
        ground.close();
    }
}
