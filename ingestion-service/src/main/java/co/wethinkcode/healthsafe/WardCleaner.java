package co.wethinkcode.healthsafe;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cleans the messy legacy export {@code wards-outdated.csv} into
 * {@link CleanedWard} records. A malformed row is never allowed to abort the
 * whole parse — it is rejected (or flagged via notes) and reported instead.
 */
public final class WardCleaner {

    public record IngestionResult(List<CleanedWard> wards, IngestionReport report) {
    }

    private enum ColumnRole { ID, WING, DEPARTMENT, BEDS, IGNORED }

    public IngestionResult clean(Reader reader) throws IOException {
        IngestionReport report = new IngestionReport();
        CSVReader csv = new CSVReader(reader);

        String[] header;
        try {
            header = csv.readNext();
        } catch (CsvValidationException e) {
            throw new IOException("unreadable CSV header", e);
        }
        if (header == null) {
            return new IngestionResult(List.of(), report);
        }

        List<String> columns = new ArrayList<>();
        List<ColumnRole> roles = new ArrayList<>();
        for (String name : header) {
            String column = normalizeHeaderName(name);
            columns.add(column);
            roles.add(roleOf(column));
        }
        if (!roles.contains(ColumnRole.ID)) {
            throw new IOException("unrecognised CSV header — no ward id column found: " + String.join(",", header));
        }

        List<CleanedWard> pending = new ArrayList<>();
        List<Integer> pendingRows = new ArrayList<>();
        String[] cells;
        try {
            while ((cells = csv.readNext()) != null) {
                report.incrementTotalDataRows();
                int row = report.getTotalDataRows();
                String raw = String.join(",", cells);
                if (isBlankRow(cells)) {
                    continue;
                }
                if (cells.length != columns.size()) {
                    report.reject(row, "expected " + columns.size() + " columns, found " + cells.length, raw);
                    continue;
                }
                CleanedWard ward = cleanRow(cells, roles, row, report);
                if (ward != null) {
                    pending.add(ward);
                    pendingRows.add(row);
                }
            }
        } catch (CsvValidationException e) {
            report.reject(report.getTotalDataRows() + 1, "unparseable CSV row: " + e.getMessage(), "");
        }

        List<CleanedWard> merged = mergeDuplicates(pending, pendingRows, report);
        report.setCleanedRecords(merged.size());
        return new IngestionResult(merged, report);
    }

    private CleanedWard cleanRow(String[] cells, List<ColumnRole> roles,
                                 int row, IngestionReport report) {
        String wardId = null;
        int idIndex = roles.indexOf(ColumnRole.ID);
        if (idIndex >= 0) {
            wardId = FieldNormalizers.normalizeId(cells[idIndex]);
        }
        if (wardId == null) {
            report.reject(row, "missing or placeholder ward id", String.join(",", cells));
            return null;
        }

        CleanedWard ward = new CleanedWard(wardId);
        ward.addSourceRow(row);

        for (int i = 0; i < cells.length; i++) {
            String raw = cells[i];
            switch (roles.get(i)) {
                case ID -> {
                    // already normalised above
                }
                case WING -> {
                    String wing = FieldNormalizers.normalizeWing(raw);
                    if (wing == null) {
                        ward.addNote("wing was missing — set to null");
                    }
                    ward.setWing(wing);
                }
                case DEPARTMENT -> {
                    String department = FieldNormalizers.normalizeDepartment(raw);
                    if (department == null) {
                        ward.addNote("department was missing — set to null");
                    } else if (FieldNormalizers.departmentWasRemapped(raw)) {
                        ward.addNote("department '" + FieldNormalizers.collapseWhitespace(raw)
                                + "' canonicalised to '" + department + "'");
                    }
                    ward.setDepartment(department);
                }
                case BEDS -> cleanBeds(ward, raw);
                case IGNORED -> {
                    // unrecognised column — not part of the ward model, so drop it
                }
            }
        }
        return ward;
    }

    private void cleanBeds(CleanedWard ward, String raw) {
        FieldNormalizers.BedsParse beds = FieldNormalizers.parseBeds(raw);
        ward.setBedsAvailable(beds.value());
        switch (beds.issue()) {
            case PLACEHOLDER -> ward.addNote("bedsAvailable was placeholder ('"
                    + beds.rawValue() + "') — set to null");
            case NON_NUMERIC -> ward.addNote("bedsAvailable was non-numeric ('"
                    + beds.rawValue() + "') — flagged for follow-up");
            case NEGATIVE -> ward.addNote("bedsAvailable was negative ('"
                    + beds.rawValue() + "') — flagged for follow-up");
            case UNREALISTIC -> ward.addNote("bedsAvailable was unrealistic ('" + beds.rawValue()
                    + "', max " + FieldNormalizers.MAX_REALISTIC_BEDS + ") — flagged for follow-up");
            case NONE -> {
            }
        }
    }

    /** Keeps the first occurrence of each ward id and folds later duplicates into it. */
    private List<CleanedWard> mergeDuplicates(List<CleanedWard> pending, List<Integer> rows,
                                              IngestionReport report) {
        Map<String, CleanedWard> byId = new LinkedHashMap<>();
        List<CleanedWard> merged = new ArrayList<>();
        for (int i = 0; i < pending.size(); i++) {
            CleanedWard ward = pending.get(i);
            CleanedWard existing = byId.get(ward.getWardId());
            if (existing == null) {
                byId.put(ward.getWardId(), ward);
                merged.add(ward);
            } else {
                existing.mergeDuplicate(ward, rows.get(i));
                report.incrementDuplicatesMerged();
            }
        }
        return merged;
    }

    private static String normalizeHeaderName(String name) {
        return FieldNormalizers.collapseWhitespace(name).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    private static ColumnRole roleOf(String column) {
        if (column.startsWith("ward") || column.equals("id") || column.endsWith("_id")) {
            return ColumnRole.ID;
        }
        if (column.contains("wing")) {
            return ColumnRole.WING;
        }
        if (column.contains("department") || column.contains("dept") || column.contains("specialt")) {
            return ColumnRole.DEPARTMENT;
        }
        if (column.contains("bed")) {
            return ColumnRole.BEDS;
        }
        return ColumnRole.IGNORED;
    }

    private static boolean isBlankRow(String[] cells) {
        for (String cell : cells) {
            if (!FieldNormalizers.collapseWhitespace(cell).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
