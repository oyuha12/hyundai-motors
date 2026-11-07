package io.github.oyuha12.hyundaimotors.diagnosis;

public enum EmergencyType {
    COLLISION_SUSPECTED(true),
    UNINTENDED_ACCELERATION(true),
    BRAKE_FAILURE(true),
    OVERHEAT(false),
    POWERTRAIN_FAULT(false),
    SAFETY_SYSTEM_FAULT(false);

    public final boolean critical;

    EmergencyType(boolean critical) {
        this.critical = critical;
    }
}
