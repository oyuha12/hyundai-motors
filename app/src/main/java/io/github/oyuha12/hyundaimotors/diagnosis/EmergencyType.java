package io.github.oyuha12.hyundaimotors.diagnosis;

public enum EmergencyType {
    UNINTENDED_ACCELERATION,
    ACCIDENT,
    FIRE,
    /** No sensor reports water, so flooding can only be chosen by the user. */
    FLOOD
}
