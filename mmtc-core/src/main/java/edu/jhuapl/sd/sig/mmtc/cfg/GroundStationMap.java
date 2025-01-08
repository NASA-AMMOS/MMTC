package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;


/**
 * Defines an object used for loading a ground station map CSV file into
 * memory. The CSV file is comprised of three columns:
 *
 *  1. Path ID
 *  2. Station ID
 *  3. Station Name
 *
 *  Station names for MMTC are freeform, and are typically of the form: [Complex name]_[Antenna Size][Receiver type][Antenna number]
 *  Receiver type might be one of: B (Beam wave guide), M (Maser), HEF (High Efficiency)
 *
 * The file is loaded into memory, and accessors are used to retrieve values
 * of one column based on the provided value of another.
 */
class GroundStationMap extends AbstractConfig {
    private static final String PATH_ID = "Path ID";
    private static final String STATION_ID = "Station ID";
    private static final String STATION_NAME = "Station Name";

    private Map<Integer, Pair<String, String>> records;

    private static final Logger logger = LogManager.getLogger();

    /**
     * Class constructor
     * @param groundStationMapPath IN:the path to the ground stations map file
     */
    GroundStationMap(String groundStationMapPath) {
        super(groundStationMapPath);
    }

    /**
     * Load the contents of the ground station map file into memory.
     *
     * @return true if successful, false otherwise
     */
    public boolean load() {
        try {
            logger.info("Loading Ground Station Map: " + this.path);

            File file = new File(this.path);

            CSVParser parser = CSVParser.parse(file, Charset.forName("utf-8"),
                    CSVFormat.DEFAULT.withFirstRecordAsHeader().withTrim());

            records = new HashMap<>();

            for (CSVRecord record : parser) {
                int pathId = Integer.parseInt(record.get(PATH_ID));
                String stationId = record.get(STATION_ID);
                String stationName = record.get(STATION_NAME);
                records.put(pathId, new ImmutablePair<>(stationId, stationName));
            }
        }
        catch (IOException | NullPointerException ex) {
            logger.error("Could not open ground stations map file: " + this.path + "." + ex);
            return false;
        }

        return true;
    }

    /**
     * Retrieve the station ID corresponding to the provided path ID.
     *
     * @param pathId the path ID
     * @return the station ID mapped to the provided path ID
     * @throws MmtcException if the ground station ID could not be obtained
     */
    String getStationId(int pathId) throws MmtcException {
        if (records.containsKey(pathId)) {
            return records.get(pathId).getKey();
        } else {
            throw new MmtcException("Ground station Path ID " + pathId + " not found in ground stations map file.");
        }
    }

    /**
     * Retrieve the station name corresponding to the provided path ID.
     *
     * @param pathId the path ID
     * @return the station name mapped to the provided path ID
     * @throws MmtcException if the ground station name could not be obtained
     */
    String getStationName(int pathId) throws MmtcException {
        try {
            return records.get(pathId).getValue();
        }
        catch (NullPointerException ex) {
            throw new MmtcException("Path ID " + pathId + " not found");
        }
    }

    /**
     * Retrieve the station name corresponding to the provided station ID.
     *
     * @param stationId the station ID
     * @return the station name mapped to the provided station ID
     * @throws MmtcException when the provided station ID is not valid
     */
    String getStationName(String stationId) throws MmtcException {
        for (Map.Entry<Integer, Pair<String, String>> record : records.entrySet()) {
            Pair<String, String> stationInfo = record.getValue();
            if (stationInfo.getKey().equals(stationId)) {
                return stationInfo.getValue();
            }
        }

        throw new MmtcException("Station ID " + stationId + " not found");
    }

    /**
     * Retrieve the station ID corresponding to the provided station name.
     *
     * @param stationName the name of the station
     * @return the station ID mapped to the provided station name
     * @throws MmtcException when the provided station name is not valid
     */
    String getStationId(String stationName) throws MmtcException {
        for (Map.Entry<Integer, Pair<String, String>> record : records.entrySet()) {
            Pair<String, String> stationInfo = record.getValue();
            if (stationInfo.getValue().equals(stationName)) {
                return stationInfo.getKey();
            }
        }

        throw new MmtcException("Station Name " + stationName + " not found");
    }
}
