package io.github.oyuha12.hyundaimotors;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import io.github.oyuha12.hyundaimotors.facility.Facility;
import io.github.oyuha12.hyundaimotors.facility.MiniMapScale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A mini map drawn by the app, like RouteMapView: the car in the middle, north up, and each
 * place by its direction and distance on two distance rings. No map data or external map.
 */
public final class ResponseMapView extends View {
    private final Paint background = new Paint();
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ray = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint marker = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint car = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint carOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float density;
    private final int iconTint;

    private List<Facility> places = Collections.emptyList();

    public ResponseMapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        float labelSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 20, getResources().getDisplayMetrics());
        Typeface font = ResourcesCompat.getFont(context, R.font.app_font);

        background.setColor(color(R.color.surface_variant));
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2 * density);
        ring.setColor(color(R.color.on_surface_variant));
        ray.setStyle(Paint.Style.STROKE);
        ray.setStrokeWidth(3 * density);
        ray.setColor(color(R.color.outline));
        ringText.setColor(color(R.color.on_surface_variant));
        ringText.setTextSize(labelSize);
        ringText.setTypeface(font);
        labelText.setColor(color(R.color.on_surface));
        labelText.setTextSize(labelSize);
        labelText.setTypeface(Typeface.create(font, Typeface.BOLD));
        labelText.setTextAlign(Paint.Align.CENTER);
        labelBackground.setColor(color(R.color.surface));
        car.setColor(color(R.color.primary));
        carOutline.setStyle(Paint.Style.STROKE);
        carOutline.setStrokeWidth(4 * density);
        carOutline.setColor(color(R.color.on_primary));
        iconTint = color(R.color.on_primary);
    }

    /** The places to show, measured from the car. Places that are not set are left out. */
    public void setPlaces(List<Facility> places) {
        this.places = new ArrayList<>(places);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        canvas.drawRect(0, 0, width, height, background);
        float centerX = width / 2;
        float centerY = height / 2;
        // Room around the outer ring for markers and their labels.
        float radius = Math.min(width, height) / 2 - 40 * density;
        if (radius <= 0) return;

        int farthest = 0;
        for (Facility place : places) farthest = Math.max(farthest, place.distanceMeters);
        int ringMeters = MiniMapScale.ringMeters(farthest);
        double pixelsPerMeter = radius / ringMeters;

        canvas.drawCircle(centerX, centerY, radius, ring);
        canvas.drawCircle(centerX, centerY, radius / 2, ring);
        ringText.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(Directions.distance(getContext(), ringMeters),
                centerX + 8 * density, centerY - radius - 6 * density, ringText);
        canvas.drawText(Directions.distance(getContext(), ringMeters / 2),
                centerX + 8 * density, centerY - radius / 2 - 6 * density, ringText);
        canvas.drawText(getContext().getString(R.string.status_map_north),
                12 * density, 12 * density - ringText.ascent(), ringText);

        float[][] positions = new float[places.size()][];
        for (int i = 0; i < places.size(); i++) {
            Facility place = places.get(i);
            float[] offset = MiniMapScale.offset(
                    place.bearingDegrees, place.distanceMeters, pixelsPerMeter);
            positions[i] = new float[] {centerX + offset[0], centerY + offset[1]};
            canvas.drawLine(centerX, centerY, positions[i][0], positions[i][1], ray);
        }

        canvas.drawCircle(centerX, centerY, 14 * density, car);
        canvas.drawCircle(centerX, centerY, 14 * density, carOutline);
        drawLabel(canvas, getContext().getString(places.isEmpty()
                ? R.string.place_unset
                : R.string.status_map_me), centerX, centerY + 24 * density);

        for (int i = 0; i < places.size(); i++) {
            Facility place = places.get(i);
            float x = positions[i][0];
            float y = positions[i][1];
            drawMarker(canvas, place, x, y);
            String label = getContext().getString(R.string.facility_detail,
                    getContext().getString(FacilityUi.label(place.type)),
                    Directions.distance(getContext(), place.distanceMeters));
            drawLabel(canvas, label, x, y + 28 * density);
        }
    }

    private void drawMarker(Canvas canvas, Facility place, float x, float y) {
        float size = 24 * density;
        marker.setColor(color(markerColor(place.type)));
        canvas.drawCircle(x, y, size, marker);
        Drawable icon = AppCompatResources.getDrawable(getContext(), FacilityUi.icon(place.type));
        if (icon == null) return;
        icon = icon.mutate();
        icon.setTint(iconTint);
        int half = Math.round(15 * density);
        icon.setBounds(Math.round(x) - half, Math.round(y) - half,
                Math.round(x) + half, Math.round(y) + half);
        icon.draw(canvas);
    }

    /** A label on a light pill below a point, kept inside the view. */
    private void drawLabel(Canvas canvas, String text, float x, float top) {
        float padding = 8 * density;
        float textWidth = labelText.measureText(text);
        float textHeight = labelText.descent() - labelText.ascent();
        float halfWidth = textWidth / 2 + padding;
        float left = Math.max(0, Math.min(getWidth() - 2 * halfWidth, x - halfWidth));
        float boxTop = Math.max(0, Math.min(getHeight() - textHeight - padding, top));
        box.set(left, boxTop, left + 2 * halfWidth, boxTop + textHeight + padding);
        canvas.drawRoundRect(box, box.height() / 2, box.height() / 2, labelBackground);
        canvas.drawText(text, box.centerX(), box.top + padding / 2 - labelText.ascent(), labelText);
    }

    /** 112 blue, 119 red, evacuation green: the Pleos informative colors. */
    @ColorRes
    static int markerColor(Facility.Type type) {
        switch (type) {
            case POLICE:
                return R.color.primary;
            case FIRE_STATION:
            case HOSPITAL:
                return R.color.negative;
            case EVACUATION_SITE:
            case SAFE_STOP:
                return R.color.success;
            default:
                return R.color.on_surface_variant;
        }
    }

    private int color(@ColorRes int id) {
        return ContextCompat.getColor(getContext(), id);
    }
}
