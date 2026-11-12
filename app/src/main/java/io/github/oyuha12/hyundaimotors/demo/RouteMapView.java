package io.github.oyuha12.hyundaimotors.demo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
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

        for (Pin pin : pins) drawPin(canvas, pin, originX + pin.x * scale, originY - pin.y * scale);

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

    /** A map pin: a colored head with the place's icon, pointing at the place. */
    private void drawPin(Canvas canvas, Pin pin, float x, float y) {
        float radius = 16 * density;
        float headY = y - radius - 10 * density;
        pinPaint.setColor(ContextCompat.getColor(getContext(), pin.color));
        Path pointer = new Path();
        pointer.moveTo(x - radius * 0.6f, headY + radius * 0.6f);
        pointer.lineTo(x, y);
        pointer.lineTo(x + radius * 0.6f, headY + radius * 0.6f);
        pointer.close();
        canvas.drawPath(pointer, pinPaint);
        canvas.drawCircle(x, headY, radius, pinPaint);
        Drawable icon = AppCompatResources.getDrawable(getContext(), pin.icon);
        if (icon == null) return;
        icon = icon.mutate();
        icon.setTint(pinIconTint);
        int half = Math.round(10 * density);
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
}
