package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Defines an object used for loading a CSV file into memory containing a
 * mapping of SCLK partition number to the date and time each partition
 * occurred.
 * <p>
 * The mapping is keyed on partition number. Each partition number corresponds
 * to an OffsetDateTime object that is parsed using the predefined
 * ISO_OFFSET_DATE_TIME DateTimeFormatter.
 * <p>
 * When parsed from CSV, the mapping becomes stored in a TreeMap, which
 * naturally sorts on the key (partition number). This makes it straightforward
 * to retrieve the "current" partition, which is the map entry with the largest
 * key.
 * </p>
 */
public class SclkPartitionMap extends AbstractConfig {
    private static final String PARTITION_NUM = "Partition Number";
    private static final String DATE = "Date";

    private NavigableMap<Integer, OffsetDateTime> partitions;

    private static final Logger logger = LogManager.getLogger();

    /**
     * Class constructor
     * @param sclkPartionMapPath IN:the path to the SCLK partion map file
     */
    public SclkPartitionMap(Path sclkPartionMapPath) {
        super(sclkPartionMapPath);
    }

    /**
     * Load the contents of the SCLK partition map file into memory.
     *
     * @return true if successful, false otherwise
     */
    public boolean load() {
        try {
            logger.info("Loading SCLK Partition Map: " + this.path);

            CSVParser parser = CSVParser.parse(
                    this.path,
                    Charset.forName("utf-8"),
                    CSVFormat.DEFAULT.withFirstRecordAsHeader().withTrim()
            );

            partitions = new TreeMap<>();

            for (CSVRecord record : parser) {
                int partitionNum = Integer.parseInt(record.get(PARTITION_NUM));
                OffsetDateTime date = OffsetDateTime.parse(record.get(DATE), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                partitions.put(partitionNum, date);
            }

            // The partition file needs at least one entry
            if (partitions.isEmpty()) {
                logger.error("No entries found in SCLK partition file; at least one entry is required");
                return false;
            }
        }
        catch (IOException | NullPointerException | DateTimeParseException | NumberFormatException ex) {
            logger.error("Could not load SCLK partion map file: " + this.path + "." + ex);
            return false;
        }

        return true;
    }

    /**
     * Retrieve the date corresponding to the provided partition number.
     *
     * @param partition the SCLK partition number
     * @return the date and time corresponding to the provided partition
     * @throws MmtcException if the date of the given clock partition could not be obtained
     */
    OffsetDateTime getPartitionDate(int partition) throws MmtcException {
        try {
            return partitions.get(partition);
        }
        catch (NullPointerException ex) {
            throw new MmtcException("Partition " + partition + " not found", ex);
        }
    }

    /**
     * Retrieve the partition number that immediately precedes provided time.
     * <p>
     * Since the SCLK partition map is sorted based on key (partition number),
     * the entry set is searched sequentially for the latest partition that
     * occurred before the provided GRT.
     *
     * @param groundReceiptTime the ground receipt time
     * @return the partition number that precedes the provided time
     */
    int getSclkPartition(OffsetDateTime groundReceiptTime) {
        int partition = 1;

        for (Map.Entry<Integer, OffsetDateTime> entry : partitions.entrySet()) {
            if (entry.getValue().isBefore(groundReceiptTime)) {
                partition = entry.getKey();
            }
        }

        return partition;
    }
}
