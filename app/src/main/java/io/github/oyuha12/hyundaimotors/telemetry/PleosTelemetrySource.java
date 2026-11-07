package io.github.oyuha12.hyundaimotors.telemetry;

import ai.pleos.playground.location.fused.FusedLocation;
import ai.pleos.playground.location.fused.listener.FusedLocationListener;
import ai.pleos.playground.location.fused.model.LocationData;
import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.model.telltale.TelltaleData;
import ai.pleos.playground.vehicle.api.model.telltale.Telltales;
import ai.pleos.playground.vehicle.constant.control.VehicleGear;
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState;
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener;
import ai.pleos.playground.vehicle.listener.GearSelectionListener;
import ai.pleos.playground.vehicle.listener.PowerDownWarningIndicatorListener;
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener;
import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot.Gear;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot.Warning;
import java.util.EnumSet;
import java.util.List;

/**
 * Real vehicle data. Speed comes from the AAOS Car API, as the Pleos SDK policy directs for
 * standard data. Brake, gear and warning lights come from the Vehicle SDK, location from
 * FusedLocation SDK. Each input is optional: if one is unavailable, the rest keep working.
 */
public final class PleosTelemetrySource implements TelemetrySource {
    private static final String TAG = "PleosTelemetry";
    private static final long PUBLISH_MILLIS = 250L;
    private static final float METERS_PER_SECOND_TO_KPH = 3.6f;
    private static final long VEHICLE_RETRY_MILLIS = 1000L;
    private static final int MAX_VEHICLE_ATTEMPTS = 5;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Listener listener;

    private Vehicle vehicle;
    private Car car;
    private CarPropertyManager propertyManager;
    private FusedLocation fusedLocation;
    private int vehicleAttempts;
    private boolean brakeRegistered;
    private boolean gearRegistered;
    private boolean powerDownRegistered;
    private boolean telltalesRegistered;

    // Written on the main thread only, through runOnMain().
    private boolean powerDownWarning;
    @Nullable private Float speedKph;
    @Nullable private Boolean brakePressed;
    private Gear gear = Gear.UNKNOWN;
    private EnumSet<Warning> warnings = EnumSet.noneOf(Warning.class);
    @Nullable private Double latitude;
    @Nullable private Double longitude;

    private final Runnable publish = new Runnable() {
        @Override
        public void run() {
            EnumSet<Warning> active = EnumSet.copyOf(warnings);
            if (powerDownWarning) active.add(Warning.POWERTRAIN);
            listener.onSnapshot(new VehicleSnapshot(
                    SystemClock.elapsedRealtime(),
                    speedKph,
                    brakePressed,
                    gear,
                    active,
                    latitude,
                    longitude));
            handler.postDelayed(this, PUBLISH_MILLIS);
        }
    };

    private final BrakePedalStatusListener brakeListener = new BrakePedalStatusListener() {
        @Override
        public void onBrakePedalStatusUpdated(Boolean pressed) {
            runOnMain(() -> brakePressed = pressed);
        }

        @Override
        public void onFailed(Exception error) {
            Log.w(TAG, "Brake status unavailable", error);
        }
    };

    private final GearSelectionListener gearListener = new GearSelectionListener() {
        @Override
        public void onSelected(VehicleGear selected) {
            runOnMain(() -> gear = toGear(selected));
        }

        @Override
        public void onFailed(Exception error) {
            Log.w(TAG, "Gear status unavailable", error);
        }
    };

    private final PowerDownWarningIndicatorListener powerDownListener =
            new PowerDownWarningIndicatorListener() {
                @Override
                public void onPowerDownWarningIndicatorUpdated(Boolean active) {
                    runOnMain(() -> powerDownWarning = Boolean.TRUE.equals(active));
                }

                @Override
                public void onFailed(Exception error) {
                    Log.w(TAG, "Power warning unavailable", error);
                }
            };

    private final TelltaleInfoListener telltaleListener = new TelltaleInfoListener() {
        @Override
        public void onTelltaleInfoUpdated(Telltales telltales) {
            runOnMain(() -> warnings = toWarnings(telltales));
        }

        @Override
        public void onFailed(Exception error) {
            Log.w(TAG, "Warning lights unavailable", error);
        }
    };

    private final CarPropertyManager.CarPropertyEventCallback speedCallback =
            new CarPropertyManager.CarPropertyEventCallback() {
                @Override
                public void onChangeEvent(CarPropertyValue value) {
                    Object speed = value.getValue();
                    if (speed instanceof Float) {
                        float kph = Math.abs((Float) speed) * METERS_PER_SECOND_TO_KPH;
                        runOnMain(() -> speedKph = kph);
                    }
                }

                @Override
                public void onErrorEvent(int propertyId, int zone) {
                    Log.w(TAG, "Speed property error");
                }
            };

    private final FusedLocationListener locationListener = new FusedLocationListener() {
        @Override
        public void onChanged(@Nullable LocationData location) {
            if (location == null) return;
            runOnMain(() -> {
                latitude = location.getLatitude();
                longitude = location.getLongitude();
            });
        }

        @Override
        public void onError(Exception error) {
            Log.w(TAG, "Location error", error);
        }
    };

    public PleosTelemetrySource(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void start(Listener listener) {
        this.listener = listener;
        startVehicle();
        startSpeed();
        startLocation();
        handler.post(publish);
    }

    @Override
    public void stop() {
        handler.removeCallbacksAndMessages(null);
        quietly(() -> {
            if (propertyManager != null) propertyManager.unregisterCallback(speedCallback);
        });
        quietly(() -> {
            if (car != null) car.disconnect();
        });
        quietly(() -> {
            if (fusedLocation != null) {
                fusedLocation.unregisterFusedLocationCallback(locationListener);
                fusedLocation.release();
            }
        });
        quietly(() -> {
            if (vehicle != null) {
                vehicle.getBrake().unregisterBrakePedalStatus(brakeListener);
                vehicle.getGear().unregisterGearSelection(gearListener);
                vehicle.release();
            }
        });
    }

    private void startVehicle() {
        quietly(() -> {
            vehicle = new Vehicle(context);
            vehicle.initialize();
        });
        handler.postDelayed(this::registerVehicleListeners, VEHICLE_RETRY_MILLIS);
    }

    /** The service connects asynchronously, so registration may need another attempt. */
    private void registerVehicleListeners() {
        if (vehicle == null) return;
        vehicleAttempts++;
        boolean retry = false;
        if (!brakeRegistered) {
            brakeRegistered = tryRegister(
                    () -> vehicle.getBrake().registerBrakePedalStatus(brakeListener));
            retry = !brakeRegistered;
        }
        if (!gearRegistered) {
            gearRegistered = tryRegister(
                    () -> vehicle.getGear().registerGearSelection(gearListener));
            retry |= !gearRegistered;
        }
        if (!powerDownRegistered) {
            powerDownRegistered = tryRegister(
                    () -> vehicle.getSafety().registerPowerDownWarningIndicator(powerDownListener));
            retry |= !powerDownRegistered;
        }
        if (!telltalesRegistered) {
            telltalesRegistered = tryRegister(
                    () -> vehicle.getSafety().registerTelltales(telltaleListener));
            retry |= !telltalesRegistered;
        }
        if (retry && vehicleAttempts < MAX_VEHICLE_ATTEMPTS) {
            handler.postDelayed(this::registerVehicleListeners, VEHICLE_RETRY_MILLIS);
        }
    }

    /** A permission refusal is final: the input stays unavailable and the rest keep working. */
    private boolean tryRegister(Runnable registration) {
        try {
            registration.run();
            return true;
        } catch (SecurityException error) {
            Log.w(TAG, "Vehicle input not permitted: " + error.getMessage());
            return true;
        } catch (RuntimeException error) {
            Log.w(TAG, "Vehicle SDK not ready: " + error.getMessage());
            return false;
        }
    }

    private void startSpeed() {
        quietly(() -> {
            car = Car.createCar(context);
            propertyManager = (CarPropertyManager) car.getCarManager(Car.PROPERTY_SERVICE);
            propertyManager.registerCallback(
                    speedCallback,
                    VehiclePropertyIds.PERF_VEHICLE_SPEED,
                    CarPropertyManager.SENSOR_RATE_UI);
        });
    }

    private void startLocation() {
        quietly(() -> {
            fusedLocation = new FusedLocation(context);
            fusedLocation.initialize();
            if (fusedLocation.isSupported()) {
                fusedLocation.registerFusedLocationCallback(locationListener);
            }
        });
    }

    private void runOnMain(Runnable update) {
        handler.post(update);
    }

    private static void quietly(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError error) {
            Log.w(TAG, "Vehicle input unavailable", error);
        }
    }

    private static Gear toGear(@Nullable VehicleGear gear) {
        if (gear == null) return Gear.UNKNOWN;
        switch (gear) {
            case GEAR_PARK:
                return Gear.PARK;
            case GEAR_REVERSE:
                return Gear.REVERSE;
            case GEAR_NEUTRAL:
                return Gear.NEUTRAL;
            case GEAR_UNKNOWN:
                return Gear.UNKNOWN;
            default:
                return Gear.DRIVE;
        }
    }

    private static EnumSet<Warning> toWarnings(@Nullable Telltales telltales) {
        EnumSet<Warning> result = EnumSet.noneOf(Warning.class);
        if (telltales == null) return result;
        List<TelltaleData> items = telltales.getTelltales();
        for (TelltaleData item : items) {
            if (item.getState() == TelltaleState.OFF) continue;
            switch (item.getType()) {
                case ENGINE_TEMPERATURE:
                    result.add(Warning.OVERHEAT);
                    break;
                case ENGINE:
                case ENGINE_OIL_PRESSURE:
                case EV_SYSTEM:
                case HEV_SYSTEM:
                case FCEV_SYSTEM:
                case EREV_SYSTEM:
                case H2_LEAK:
                case POWER_DOWN:
                    result.add(Warning.POWERTRAIN);
                    break;
                case ABS:
                case ESC:
                case MDPS:
                case AIRBAG:
                    result.add(Warning.SAFETY_SYSTEM);
                    break;
                default:
                    break;
            }
        }
        return result;
    }
}
