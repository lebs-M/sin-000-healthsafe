package co.wethinkcode.healthsafe;

/** A critical equipment failure alert received from the equipment queue. */
public record EquipmentFailureAlert(
        String wardId,
        String wing,
        String equipment,
        String detail,
        String detectedAt) {
}
