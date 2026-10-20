package co.wethinkcode.healthsafe;

import java.util.ArrayList;
import java.util.List;

/**
 * Audit summary of one cleaning pass: what came in, what survived, what was
 * merged, and what had to be rejected outright.
 */
public class IngestionReport {

    public record RejectedRow(int row, String reason, String raw) {
    }

    private int totalDataRows;
    private int cleanedRecords;
    private int duplicatesMerged;
    private final List<RejectedRow> rejectedRows = new ArrayList<>();

    void incrementTotalDataRows() {
        totalDataRows++;
    }

    void setCleanedRecords(int cleanedRecords) {
        this.cleanedRecords = cleanedRecords;
    }

    void incrementDuplicatesMerged() {
        duplicatesMerged++;
    }

    void reject(int row, String reason, String raw) {
        rejectedRows.add(new RejectedRow(row, reason, raw));
    }

    public int getTotalDataRows() {
        return totalDataRows;
    }

    public int getCleanedRecords() {
        return cleanedRecords;
    }

    public int getDuplicatesMerged() {
        return duplicatesMerged;
    }

    public List<RejectedRow> getRejectedRows() {
        return rejectedRows;
    }
}
