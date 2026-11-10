package io.github.oyuha12.hyundaimotors.demo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import io.github.oyuha12.hyundaimotors.R;

/**
 * Draws a {@link DemoRoute} on a plain street grid: the route, the destination, and the car's
 * current position and direction. Map tiles are not needed, so it works on any emulator.
 */
public final class RouteMapView extends View {
    private static final float GRID_METERS = 100f;

    private final Paint background = new Paint();
    private final Paint street = new Paint(Paint.ANTI_ALIAS_FLAG);
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

    public RouteMapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        background.setColor(ContextCompat.getColor(context, R.color.surface_variant));
        street.setColor(ContextCompat.getColor(context, R.color.surface));
        street.setStrokeWidth(10 * density);
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

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), background);
        if (route == null) return;

        // Fit the route with a margin, keeping meters square.
        float minX = 0, maxX = 0, minY = 0, maxY = 0;
        for (DemoRoute.Point p : route.points()) {
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
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

        DemoRoute.Point now = route.positionAt(progressMeters);
        drawCar(canvas, originX + now.x * scale, originY - now.y * scale, route.headingAt(progressMeters));
    }

    private void drawGrid(Canvas canvas, float scale, float originX, float originY) {
        float step = GRID_METERS * scale;
        if (step < 24 * density) step *= Math.ceil(24 * density / step);
        float startX = originX % step;
        for (float x = startX; x < getWidth(); x += step) canvas.drawLine(x, 0, x, getHeight(), street);
        float startY = originY % step;
        for (float y = startY; y < getHeight(); y += step) canvas.drawLine(0, y, getWidth(), y, street);
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
