package io.github.oyuha12.hyundaimotors.telemetry;

import ai.pleos.playground.location.fused.FusedLocation;
import ai.pleos.playground.location.fused.listener.FusedLocationListener;
import ai.pleos.playground.location.fused.model.LocationData;
import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.model.area.SeatArea;
import ai.pleos.playground.vehicle.api.model.telltale.TelltaleData;
import ai.pleos.playground.vehicle.api.model.telltale.Telltales;
import ai.pleos.playground.vehicle.api.model.area.WheelArea;
import ai.pleos.playground.vehicle.constant.area.SeatValue;
import ai.pleos.playground.vehicle.constant.area.WheelValue;
import ai.pleos.playground.vehicle.constant.control.OilLevelState;
import ai.pleos.playground.vehicle.constant.control.VehicleGear;
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState;
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType;
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener;
import ai.pleos.playground.vehicle.listener.GearSelectionListener;
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener;
import ai.pleos.playground.vehicle.listener.SeatAirbagDeployedListener;
import ai.pleos.playground.vehicle.listener.OilLevelStateListener;
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener;
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener;
import ai.pleos.playground.vehicle.listener.TirePressureListener;
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Real vehicle data. Speed comes from the AAOS Car API, as the Pleos SDK policy directs for
 * standard data. Brake, gear, airbag, cabin temperature and warning lights come from the
 * Vehicle SDK, location from FusedLocation SDK. Each input is optional: if one is unavailable,
 * the rest keep working.
 */
public final class PleosTelemetrySource implements TelemetrySource {
    private static final String TAG = "PleosTelemetry";
    private static final long PUBLISH_MILLIS = 250L;
    private static final float METERS_PER_SECOND_TO_KPH = 3.6f;
    private static final long VEHICLE_RETRY_MILLIS = 1000L;
    private static final int MAX_VEHICLE_ATTEMPTS = 5;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Runnable> pendingRegistrations = new ArrayList<>();
    private Listener listener;

    private Vehicle vehicle;
    private Car car;
    private CarPropertyManager propertyManager;
    private FusedLocation fusedLocation;
    private int vehicleAttempts;

    // Written on the main thread only, through runOnMain().
    @Nullable private Float speedKph;
    @Nullable private Boolean brakePressed;
    private Gear gear = Gear.UNKNOWN;
    @Nullable private Boolean airbagDeployed;
    @Nullable private Float cabinTemperatureC;
    private boolean overheatWarning;
    @Nullable private Double latitude;
    @Nullable private Double longitude;
    @Nullable private Float headingDegrees;
    private boolean brakeFluidLow;
    private float[] tirePressures = {Float.NaN, Float.NaN, Float.NaN, Float.NaN};
    @Nullable private Float steeringAngleDegrees;

    private final Runnable publish = new Runnable() {
        @Override
        public void run() {
            listener.onSnapshot(new VehicleSnapshot(
                    SystemClock.elapsedRealtime(),
                    speedKph,
                    brakePressed,
                    gear,
                    airbagDeployed,
                    cabinTemperatureC,
                    overheatWarning,
                    latitude,
                    longitude,
                    headingDegrees).withChassis(brakeFluidLow, tirePressures, steeringAngleDegrees));
            handler.postDelayed(this, PUBLISH_MILLIS);
        }
    };

    private final OilLevelStateListener brakeFluidListener = new OilLevelStateListener() {
        @Override
        public void onOilLevelUpdated(OilLevelState state) {
            boolean low = state == OilLevelState.LOW || state == OilLevelState.CRITICALLY_LOW;
            runOnMain(() -> brakeFluidLow = low);
        }

        @Override
        public void onFailed(Exception error) {
            Log.w(TAG, "Brake fluid level unavailable", error);
        }
    };

    private final SteeringWheelAngleListener steeringListener = new SteeringWheelAngleListener() {
        @Override
        public void onSteeringWheelAngleUpdated(Float degrees) {
            runOnMain(() -> steeringAngleDegrees = degrees);
        }

        @Override
        public void onFailed(Exception error) {
            Log.w(TAG, "Steering angle unavailable", error);
        }
    };

    private TirePressureListener tireListener(int wheel) {
        return new TirePressureListener() {
            @Override
            public void onTirePressureUpdated(Float pressure) {
                runOnMain(() -> {
                    float[] updated = tirePressures.clone();
                    updated[wheel] = pressure == null ? Float.NaN : pressure;
                    tirePressures = updated;
                });
            }

            @Override
            public void onFailed(Exception error) {
                Log.w(TAG, "Tire pressure unavailable", error);
            }
        };
    }

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

    private final SeatAirbagDeployedListener airbagListener = new SeatAirbagDeployedListener() {
        @Override
        public void onSeatAirbagDeployUpdated(Boolean deployed) {
            runOnMain(() -> airbagDeployed = deployed);
        }

        @Override
        public void onFailed(Exception error) {
            Log.w(TAG, "Airbag status unavailable", error);
        }
    };

    /** The SDK reports cabin temperature in Celsius by default. Confirm on a real vehicle. */
    private final HvacTemperatureListener cabinListener = new HvacTemperatureListener() {
        @Override
        public void onHvacTemperatureUpdated(Float celsius) {
            runOnMain(() -> cabinTemperatureC = celsius);
        }

        @Override
        public void onFailed(Exception error) {
            Log.w(TAG, "Cabin temperature unavailable", error);
        }
    };

    private final TelltaleInfoListener telltaleListener = new TelltaleInfoListener() {
        @Override
        public void onTelltaleInfoUpdated(Telltales telltales) {
            runOnMain(() -> overheatWarning = hasOverheatWarning(telltales));
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
                headingDegrees = location.getHeading();
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
            if (vehicle != null) vehicle.release();
        });
    }

    private void startVehicle() {
        quietly(() -> {
            vehicle = new Vehicle(context);
            vehicle.initialize();
        });
        if (vehicle == null) return;
        SeatArea driverSeat = new SeatArea(SeatValue.ROW_1_LEFT);
        pendingRegistrations.add(() -> vehicle.getBrake().registerBrakePedalStatus(brakeListener));
        pendingRegistrations.add(() -> vehicle.getGear().registerGearSelection(gearListener));
        pendingRegistrations.add(
                () -> vehicle.getSafety().registerSeatAirbagDeployed(driverSeat, airbagListener));
        pendingRegistrations.add(
                () -> vehicle.getHVAC().registerHvacTemperatureCurrent(driverSeat, cabinListener));
        pendingRegistrations.add(() -> vehicle.getSafety().registerTelltales(telltaleListener));
        pendingRegistrations.add(
                () -> vehicle.getSafety().registerBrakeFluidLevelState(brakeFluidListener));
        pendingRegistrations.add(
                () -> vehicle.getSteeringWheel().registerSteeringWheelAngle(steeringListener));
        WheelValue[] wheels = {
            WheelValue.LEFT_FRONT, WheelValue.RIGHT_FRONT, WheelValue.LEFT_REAR, WheelValue.RIGHT_REAR
        };
        for (int i = 0; i < wheels.length; i++) {
            WheelArea area = new WheelArea(wheels[i]);
            TirePressureListener listener = tireListener(i);
            pendingRegistrations.add(() -> vehicle.getTire().registerTirePressure(area, listener));
        }
        handler.postDelayed(this::registerVehicleListeners, VEHICLE_RETRY_MILLIS);
    }

    /** The service connects asynchronously, so registration may need another attempt. */
    private void registerVehicleListeners() {
        vehicleAttempts++;
        Iterator<Runnable> registrations = pendingRegistrations.iterator();
        while (registrations.hasNext()) {
            try {
                registrations.next().run();
                registrations.remove();
            } catch (SecurityException error) {
                // A refusal is final: this input stays unavailable, the rest keep working.
                Log.w(TAG, "Vehicle input not permitted: " + error.getMessage());
                registrations.remove();
            } catch (RuntimeException error) {
                Log.w(TAG, "Vehicle SDK not ready: " + error.getMessage());
            }
        }
        if (!pendingRegistrations.isEmpty() && vehicleAttempts < MAX_VEHICLE_ATTEMPTS) {
            handler.postDelayed(this::registerVehicleListeners, VEHICLE_RETRY_MILLIS);
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

    private static boolean hasOverheatWarning(@Nullable Telltales telltales) {
        if (telltales == null) return false;
        for (TelltaleData item : telltales.getTelltales()) {
            if (item.getType() == TelltaleType.ENGINE_TEMPERATURE
                    && item.getState() != TelltaleState.OFF) {
                return true;
            }
        }
        return false;
    }
}
