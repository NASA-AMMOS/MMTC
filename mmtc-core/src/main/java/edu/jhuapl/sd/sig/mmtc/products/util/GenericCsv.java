package edu.jhuapl.sd.sig.mmtc.products.util;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class GenericCsv {
    private final Path path;
    private final List<String> headers;
    private final List<Map<String, String>> rows;

    public GenericCsv(Path csvPath) throws MmtcException {
        this.path = csvPath;
        try {
            CSVParser parser = CSVParser.parse(
                    csvPath,
                    StandardCharsets.UTF_8,
                    CSVFormat.DEFAULT.withFirstRecordAsHeader().withTrim()
            );
            this.headers = new ArrayList<>(parser.getHeaderNames());
            this.rows = new ArrayList<>();

            for (CSVRecord record : parser) {
                Map<String, String> row = new HashMap<>();
                for (String header : this.headers) {
                    row.put(header, record.get(header));
                }
                rows.add(row);
            }

            parser.close();
        } catch (IOException e) {
            throw new MmtcException(e);
        }
    }

    public List<String> getHeaders() {
        return Collections.unmodifiableList(this.headers);
    }

    public List<Map<String, String>> getRows() {
        return this.rows
                .stream()
                .map(Collections::unmodifiableMap)
                .collect(Collectors.toList());
    }

    public boolean hasColumn(String name) {
        return headers.contains(name);
    }

    public void renameColumn(String oldName, String newName) throws MmtcException {
        verifyColExists(oldName);
        verifyColDoesNotExist(newName);

        int colIndex = headers.indexOf(oldName);
        headers.add(colIndex, newName);
        headers.remove(oldName);

        rows.forEach(r -> {
            String colVal = r.remove(oldName);
            r.put(newName, colVal);
        });
    }

    public List<String> readValuesForColumn(String colName) throws MmtcException {
        verifyColExists(colName);

        return rows.stream()
                .map(r -> r.get(colName))
                .collect(Collectors.toList());
    }

    private void verifyColExists(String colName) throws MmtcException {
        if (! hasColumn(colName)) {
            throw new MmtcException("No such column: " + colName);
        }
    }

    private void verifyColDoesNotExist(String colName) throws MmtcException {
        if (hasColumn(colName)) {
            throw new MmtcException("Column exists: " + colName);
        }
    }

    public void addColumnAtIndexWithValues(String colName, int colIndex, List<String> vals) throws MmtcException {
        verifyColDoesNotExist(colName);
        verifyRowAndValCount(rows.size(), vals.size());

        this.headers.add(colIndex, colName);
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).put(colName, vals.get(i));
        }
    }

    private void verifyRowAndValCount(int rowSize, int valSize) throws MmtcException {
        if (rowSize != valSize) {
            throw new MmtcException(String.format("Was provided %d values for %d rows", valSize, rowSize));
        }
    }

    public void write() throws MmtcException {
        try {
            CSVPrinter printer = new CSVPrinter(new FileWriter(path.toFile(), false), CSVFormat.DEFAULT);
            printer.printRecord(headers);

            for (Map<String, String> row : rows) {
                printer.printRecord(headers.stream().map(row::get).collect(Collectors.toList()));
            }

            printer.close();
        } catch (IOException e) {
            throw new MmtcException("Unable to write CSV", e);
        }
    }

    public void updateColValWhereEqualToOldValue(String colName, String oldValue, String newValue) throws MmtcException {
        verifyColExists(colName);

        for (Map<String, String> row : rows) {
            if (row.get(colName).equals(oldValue)) {
                row.put(colName, newValue);
            }
        }
    }

    public int getNumRows() {
        return rows.size();
    }

    public void updateLastRowWithColVal(String colName, String newVal) throws MmtcException {
        verifyColExists(colName);
        if (! rows.isEmpty()) {
            rows.get(rows.size() - 1).put(colName, newVal);
        }
    }

    public void removeColumn(String colName) throws MmtcException {
        verifyColExists(colName);
        headers.remove(colName);

        for (Map<String, String> row : rows) {
            row.remove(colName);
        }
    }
}
