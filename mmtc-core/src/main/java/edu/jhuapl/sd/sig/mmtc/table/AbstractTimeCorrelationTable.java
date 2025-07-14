package edu.jhuapl.sd.sig.mmtc.table;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public abstract class AbstractTimeCorrelationTable {
    public static final String RUN_TIME = "Run Time";

    private File file;
    CSVParser parser;

    /**
     * Create the table file object from the specified path.
     *
     * @param path the path to the table
     */
    AbstractTimeCorrelationTable(Path path) {
        setPath(path);
    }

    protected void setPath(Path path) {
        file = path.toFile();
    }

    protected File getFile() { return this.file; }
    /**
     * Write a record out to the table.
     *
     * @param record the record to write
     * @throws MmtcException when the file cannot be written for
     * any reason
     */
    public void writeRecord(TableRecord record) throws MmtcException {
        boolean writeHeaders = false;

        if (!file.exists()) {
            writeHeaders = true;
        }

        try {
            CSVPrinter printer = new CSVPrinter(new FileWriter(file, true), CSVFormat.DEFAULT);

            if (writeHeaders) {
                printer.printRecord(getHeaders());
            }

            printer.printRecord(record.getValues());
            printer.close();
        }
        catch (IOException ex) {
            throw new MmtcException("Unable to write record to table", ex);
        }
    }

    /**
     * Retrieve the last record in the table
     *
     * @return the record as a map of column name to value, as strings
     * @throws MmtcException if the parser object cannot be created
     */
    public Map<String, String> readLastRecord() throws MmtcException {
        // Load the table to set up the parser object
        resetParser();

        Iterator<CSVRecord> iter = parser.iterator();
        CSVRecord rec = null;

        // Iterate over the entire table to get the last record
        // Not optimal but appears to be the only way
        while (iter.hasNext()) {
            rec = iter.next();
        }

        if (rec != null) {
            return rec.toMap();
        }
        else {
            return Collections.emptyMap();
        }
    }

    /**
     * Replaces the last record in the table with the specified record.
     *
     * @param updatedRecord The new/updated/replacement record. The table's existing last record will be replaced by this record.
     * @throws MmtcException if MMTC fails to replace the record.
     */
    public void replaceLastRecord(Map<String, String> updatedRecord) throws MmtcException {
        List<CSVRecord> records = new ArrayList<>();

        // Read all records from CSV file into a list in memory
        resetParser();

        Iterator<CSVRecord> iter = parser.iterator();
        CSVRecord rec = null;
        while (iter.hasNext()) {
            rec = iter.next();
            if (rec != null) {
                records.add(rec);
            }
        }
        try {
            parser.close();
        }
        catch (IOException ex) {
            throw new MmtcException("Can't replace last CSV record: error reading existing records.", ex);
        }

        // Remove the last record from the list
        if (records.size() == 0) {
            throw new MmtcException("Can't replace last CSV record: CSV table is empty.");
        }
        records.remove(records.size() - 1);
        
        // Rewrite CSV file
        String errorMessage = "Can't replace last CSV record: error opening CSV file to write header row.";
        try (CSVPrinter printer = new CSVPrinter(new FileWriter(file, false), CSVFormat.DEFAULT)) {
            // Write headers into file, overwriting existing content
            errorMessage = "Can't replace last CSV record: error rewriting header row.";
            printer.printRecord(getHeaders());
        }
        catch (IOException ex) {
            throw new MmtcException(errorMessage, ex);
        }

        errorMessage = "Can't replace last CSV record: error opening CSV file to write records.";
        try (CSVPrinter printer = new CSVPrinter(new FileWriter(file, true), CSVFormat.DEFAULT)) {
            // Append records into file
            errorMessage = "Can't replace last CSV record: error rewriting existing records.";
            for (CSVRecord record : records) {
                printer.printRecord(record);
            }

            // Append updated/replacement last record into file
            errorMessage = "Can't replace last CSV record: error writing updated last record.";
            TableRecord record = new TableRecord(getHeaders());
            record.setValues(updatedRecord);
            printer.printRecord(record.getValues());
        }
        catch (IOException ex) {
            throw new MmtcException(errorMessage, ex);
        }
    }

    /**
     * Removes all lines after the line number specified from the relevant CSV file
     * @param newLastLine is the last line number that will remain after the method finishes
     * @return the number of lines that were removed
     * @throws MmtcException if the file is empty, missing, restricted/inaccessible, or is shorter than expected
     */
    public int truncateRecords(int newLastLine) throws MmtcException {
        List<CSVRecord> records = new ArrayList<>();

        // Read all records from CSV file into a list in memory
        resetParser();
        Iterator<CSVRecord> iter = parser.iterator();
        CSVRecord rec = null;
        while (iter.hasNext()) {
            rec = iter.next();
            if (rec != null) {
                records.add(rec);
            }
        }
        try {
            parser.close();
        }
        catch (IOException ex) {
            throw new MmtcException("Can't truncate CSV file: error reading existing records.", ex);
        }

        int initialLastLine = records.size() + 1;
        // Remove the requisite number of records
        if (records.isEmpty()) {
            throw new MmtcException("Can't truncate CSV file: CSV table is empty.");
        }
        if (newLastLine >= initialLastLine) {
            throw new MmtcException(String.format("Can't truncate CSV file: the new last line would be %d but the file only has %d lines!",
                    newLastLine,
                    initialLastLine));
        }
        // Record index = line # - 2 ; Keep newLastLine-2 but remove the one after that (lastLine-newLastLine) times
        // since subsequent list elements shift left after deletion
        for (int i = newLastLine-1; i < records.size();) {
            records.remove(i);
        }

        // Rewrite CSV file
        String errorMessage = "Can't truncate CSV file: error opening CSV file to write header row.";
        try (CSVPrinter printer = new CSVPrinter(new FileWriter(file, false), CSVFormat.DEFAULT)) {
            // Write headers into file, overwriting existing content
            errorMessage = "Can't truncate CSV file: error rewriting header row.";
            printer.printRecord(getHeaders());
        }
        catch (IOException ex) {
            throw new MmtcException(errorMessage, ex);
        }

        errorMessage = "Can't truncate CSV file: error opening CSV file to write records.";
        try (CSVPrinter printer = new CSVPrinter(new FileWriter(file, true), CSVFormat.DEFAULT)) {
            // Append records into file
            errorMessage = "Can't truncate CSV file: error rewriting existing records.";
            for (CSVRecord record : records) {
                printer.printRecord(record);
            }
        }
        catch (IOException ex) {
            throw new MmtcException(errorMessage, ex);
        }
        return (initialLastLine - (records.size()+1));
    }

    /**
     * Writes a list of TableRecords to a CSV file, replacing the existing contents (if any)
     * @param records the List of TableRecords that will each populate one line of the finished CSV
     * @throws MmtcException if the file is restricted/inaccessible or if any other IOExceptions are encountered
     */
    public void writeToTableFromTableRecords(List<TableRecord> records) throws MmtcException {
        try (CSVPrinter printer = new CSVPrinter(new FileWriter(file, false), CSVFormat.DEFAULT)) {
            // Write headers into file, overwriting existing content
            printer.printRecord(getHeaders());
        }
        catch (IOException ex) {
            throw new MmtcException("Failed to write header row when updating "+file.getName(), ex);
        }

        try (CSVPrinter printer = new CSVPrinter(new FileWriter(file, true), CSVFormat.DEFAULT)) {
            // Append records into file
            for (TableRecord record : records) {
                printer.printRecord(record.getValues());
            }
        }
        catch (IOException ex) {
            throw new MmtcException("Failed to write TableRecords when updating "+file.getName(), ex);
        }
    }

    /**
     * Load a CSV file by creating a parser object.
     * Creates a new parser object, ready to iterate from the beginning of the CSV.
     *
     * Assumptions:
     *  - the file contains headers as the first row
     *  - the file is encoding in UTF-8
     *
     * @throws MmtcException when the table is unable to be read
     */
    void resetParser() throws MmtcException {
        try {
            parser = CSVParser.parse(
                    file,
                    StandardCharsets.UTF_8,
                    CSVFormat.DEFAULT.withFirstRecordAsHeader().withTrim()
            );
        } catch (IOException ex) {
            throw new MmtcException("Unable to read table: " + file.getAbsolutePath(), ex);
        }
    }

    /**
     * Get the list of column headers for this table.
     *
     * @return the headers as a list of strings
     */
    abstract List<String> getHeaders();

    /**
     * Returns true if the table already exists.
     * @return true if table exists, false otherwise
     */
    public boolean exists() {
        return file.exists();
    }

    /**
     * Takes an existing table and returns the number of the last non-null line
     * @return the last line with content as an integer or 1 if the table is empty/nonexistent
     */
    public int getLastLineNumber() {
        int lines = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            while (reader.readLine() != null) lines++;
        } catch (IOException e) {
            // Usually indicates a missing file
            return 1;
        }
        if (lines==0) { return 1; } // To protect header row
        return lines;
    }

    public Path getPath() {
        return file.toPath();
    };
}
