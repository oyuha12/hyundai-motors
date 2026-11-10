package io.github.oyuha12.hyundaimotors.diagnosis;

public enum EmergencyType {
    UNINTENDED_ACCELERATION,
    BRAKE_FAILURE,
    ACCIDENT,
    FIRE,
    /** No sensor reports water, so flooding can only be chosen by the user. */
    FLOOD,
    /** Sudden tire pressure loss, including a wheel coming off. */
    TIRE_FAILURE,
    /** No hands-on-wheel or lane sensor is available, so this is chosen by the user. */
    DRIVER_HEALTH,
    /** Being stopped on a fast road, where following traffic can hit the car. */
    SECONDARY_ACCIDENT
}
