package co.wethinkcode.healthsafe;

/** A critical equipment failure detected on a ward, sent to the equipment queue. */
public record EquipmentFailureAlert(
        String wardId,
        String wing,
        String equipment,
        String detail,
        String detectedAt) {
}
