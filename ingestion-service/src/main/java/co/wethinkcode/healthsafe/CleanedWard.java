package co.wethinkcode.healthsafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One cleaned ward record, plus the audit trail of everything the cleaner had
 * to fix, flag, or merge to produce it.
 */
public class CleanedWard {

    private final String wardId;
    private String wing;
    private String department;
    private Integer bedsAvailable;
    private final List<String> notes = new ArrayList<>();
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private final List<Integer> sourceRows = new ArrayList<>();

    public CleanedWard(String wardId) {
        this.wardId = wardId;
    }

    public String getWardId() {
        return wardId;
    }

    public String getWing() {
        return wing;
    }

    public String getDepartment() {
        return department;
    }

    public Integer getBedsAvailable() {
        return bedsAvailable;
    }

    public List<String> getNotes() {
        return notes;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public List<Integer> getSourceRows() {
        return sourceRows;
    }

    void setWing(String wing) {
        this.wing = wing;
    }

    void setDepartment(String department) {
        this.department = department;
    }

    void setBedsAvailable(Integer bedsAvailable) {
        this.bedsAvailable = bedsAvailable;
    }

    void addNote(String note) {
        notes.add(note);
    }

    void putAttribute(String column, Object value) {
        attributes.put(column, value);
    }

    void addSourceRow(int row) {
        sourceRows.add(row);
    }

    /**
     * Merges a later duplicate (same normalised ward id) into this record.
     * First-seen valid value wins per field; conflicting values and absorbed
     * notes are recorded so nothing disappears silently.
     */
    void mergeDuplicate(CleanedWard other, int otherRow) {
        sourceRows.addAll(other.sourceRows);
        notes.add("merged duplicate row " + otherRow);

        if (wing == null && other.wing != null) {
            wing = other.wing;
        } else if (wing != null && other.wing != null && !wing.equals(other.wing)) {
            notes.add("conflicting wing '" + other.wing + "' in duplicate row " + otherRow
                    + " — kept '" + wing + "'");
        }

        if (department == null && other.department != null) {
            department = other.department;
        } else if (department != null && other.department != null && !department.equals(other.department)) {
            notes.add("conflicting department '" + other.department + "' in duplicate row " + otherRow
                    + " — kept '" + department + "'");
        }

        if (bedsAvailable == null && other.bedsAvailable != null) {
            bedsAvailable = other.bedsAvailable;
        } else if (bedsAvailable != null && other.bedsAvailable != null
                && !bedsAvailable.equals(other.bedsAvailable)) {
            notes.add("conflicting bedsAvailable " + other.bedsAvailable + " in duplicate row " + otherRow
                    + " — kept " + bedsAvailable);
        }

        other.notes.forEach(note -> notes.add("row " + otherRow + ": " + note));
        other.attributes.forEach(attributes::putIfAbsent);
    }
}
