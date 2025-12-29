package edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfigWithTlmSource;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.tlm.TimekeepingPacketIterator;
import edu.jhuapl.sd.sig.mmtc.tlm.TimekeepingPacketParser;
import edu.jhuapl.sd.sig.mmtc.tlm.TimekeepingRecord;
import edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache.OffsetDateTimeRange;
import edu.jhuapl.sd.sig.mmtc.util.Environment;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <P>The AmpcsTlmWithFrames class implements the AmpcsTelemetrySource abstract class. This class provides the funtions that enable
 * the MMTC to query telemetry from the AMMOS AMPCS data store. This class is provided for missions that use AMPCS
 * as their core command and telemetry system and is the default provided. Whereas AmpcsTlmArchive retrieves target
 * sample ERTs from packet metadata, this class assumes that target ERTs are only available in frame metadata.</P>
 *
 <P>The functions in this class use the AMPCS chill_get_packets, chill_get_frames, and chill_get_chanvals interfaces to access the
 * telemetry store. These are run as commands and their output is captured for processing. The TK packets are
 * obtained using the chill_get_packets command which writes the raw packet to a temporary file. This application
 * then reads that file and parses it in accordance with the packet description XML file as defined in the tk_packet.xsd
 * schema file. This class does not require consecutive TK packets.</P>
 *
 * <P>When chill_get_packets is called, the binary TK packet is written to a local, temporary file and parsed by this application
 * as specified in the mission's packet definition .xml file that implements the tk_packet.xsd schema.
 * This is the supplemental frame packet (i.e., the one containing the time correlation data). It then extracts
 * the included reference or last frame VCID and VCFC values. It then calls chill_get_frames with these
 * VCID and VCFC values to get the associated reference or target frame. It assumes that the frame
 * matching that VCID and VCFC which is nearest in time to the TK packet is the correct frame.
 * It then gets the essential metadata that includes the ERT from this frame data. It then associates the SCLK values
 * from the supplemental TK packet with the ERT from this frame to perform time correlation.
 * This metadata contains the crucial ERT as well as the associated supplemental fram VCID, VCFC,
 * and ground station information that can be used for filter checks. Therefore, this class
 * contains functions that extract the data from the bodies of the TK packets and parse the associated
 * target frame's AMPCS metadata for essential information.</P>
 */
public class AmpcsTlmWithFrames extends AmpcsTelemetrySource {
    public AmpcsTlmWithFrames() {
        super();
    }

    @Override
    public void applyConfiguration(MmtcConfigWithTlmSource config) throws MmtcException {
        super.applyConfiguration(config);

        if (config.getSamplesPerSet() != 1) {
            throw new MmtcException("When using the AmpcsTlmWithFrames telemetry source, configuration option telemetry.samplesPerSet must be 1.");
        }

        if (! packetsHaveDownlinkDataRate()) {
            throw new MmtcException("ERROR: When using the AmpcsTlmWithFrames telemetry source plugin, the TK packets must contain the downlink data rate. These do not.");
        }
    }

    @Override
    public void checkCorrelationConfiguration(TimeCorrelationRunConfig config) throws MmtcException {
        Set<String> enabledFilters = config.getFilters().keySet();
        if (
                enabledFilters.contains(TimeCorrelationRunConfig.ERT_FILTER) ||
                        enabledFilters.contains(TimeCorrelationRunConfig.SCLK_FILTER) ||
                        enabledFilters.contains(TimeCorrelationRunConfig.VCID_FILTER) ||
                        enabledFilters.contains(TimeCorrelationRunConfig.CONSEC_FRAMES_FILTER) ||
                        enabledFilters.contains(TimeCorrelationRunConfig.CONSEC_MC_FRAME_FILTER)
        ) {
            String errorString = "When using the AmpcsTlmWithFrames telemetry source, the " +
                    TimeCorrelationRunConfig.ERT_FILTER +  ", " +
                    TimeCorrelationRunConfig.SCLK_FILTER + ", " +
                    TimeCorrelationRunConfig.VCID_FILTER + ", " +
                    TimeCorrelationRunConfig.CONSEC_FRAMES_FILTER + ", and " +
                    TimeCorrelationRunConfig.CONSEC_MC_FRAME_FILTER +
                    " filters are not applicable and must be disabled by setting the configuration options " +
                    "filter.<filter name>.enabled to false.";
            throw new MmtcException(errorString);
        }
    }

    public List<FrameSample> getSamplesInRange(OffsetDateTime start, OffsetDateTime stop) throws MmtcException {
        if (ampcsConfig.isAmpcsTlmWithFramesBatchingEnabled()) {
            return getSamplesInRangeBatching(start, stop);
        } else {
            return getSamplesInRangeNonBatching(start, stop);
        }
    }

    /**
     * Retrieve samples whose ERTs are within the specified time range. Populate the samples with data extracted or
     * derived from the contents of timekeeping packets and from the AMPCS metadata of the packets and their frames.
     *
     * @param start the start time of the range, in ERT, for querying telemetry
     * @param stop the stop time of the range, in ERT, for querying telemetry
     * @return the list of frame samples within the time range
     * @throws MmtcException when unable to query or parse telemetry
     */
    public List<FrameSample> getSamplesInRangeBatching(OffsetDateTime start, OffsetDateTime stop) throws MmtcException {

        if (!connectedToAmpcs) {
            throw new MmtcException("Not connected; call connect().");
        }

        List<FrameSample> samples = new ArrayList<>();

        try {
            // Convert the start and stop times to string format and remove the trailing 'Z' that results.
            String beginTime = start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME).split("\\+")[0];
            beginTime = beginTime.replace("Z", "");

            String endTime   = stop.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME).split("\\+")[0];
            endTime = endTime.replace("Z", "");

            // Get the APID of the TK packet from configuration parameters.
            String TKPKTAPID = ampcsConfig.getTKPacketApid();

            // Create a temporary raw packet file name.
            String packetOutputFilename = getUniqueFilename("tkpkt");
            logger.trace("Raw TK packet data will be written to " + packetOutputFilename + ".");

            // Run chill_get_packets to query for all of the time correlation packets that are within the selected
            // contact interval. Query by APID and ERT.
            String cmd = chillGdsPath+"/bin/chill_get_packets -m";
            if (sessionId != null) {
                cmd += " -K " + sessionId;
            }
            cmd += " --packetApid " + TKPKTAPID + " --timeType ERT " +
                    "--beginTime " + beginTime + " --endTime " + endTime + " --report --filename " + packetOutputFilename;
            if (connectionParms != null) {
                cmd += " " + connectionParms;
            }
            CSVParser pktMetadata = runSubprocessCsv(cmd);

            // Get the packet metadata field names.
            final String ERT         = ampcsConfig.getTkPacketErtFieldName();
            final String DSSID      = ampcsConfig.getTkPacketDssIdFieldName();
            final String VCID        = ampcsConfig.getTkPacketVcidFieldName();
            final String VCFC        = ampcsConfig.getTkPacketVcfcFieldName();
            final String PKTLEN      = ampcsConfig.getTkPacketLengthFieldName();
            final String FRAME_ERT   = ampcsConfig.getFrameErtFieldName();
            final String FRAME_DSSID = ampcsConfig.getFrameDssIdFieldName();
            final String FRAME_VCID  = ampcsConfig.getFrameVcidFieldName();
            final String FRAME_VCFC  = ampcsConfig.getFrameVcfcFieldName();

            // Get the information on the TK packet binary file.
            Path pktDescriptionSource;
            URI binaryPktFile;
            TimekeepingPacketParser parser;
            try {
                pktDescriptionSource = ampcsConfig.getTkPacketDescriptionFilePath();
                binaryPktFile        = new URI("file://" + packetOutputFilename);
            } catch (URISyntaxException e) {
                throw new MmtcException("Unable to open packet description file " +
                        ampcsConfig.getTkPacketDescriptionFilePath());
            }
            try {
                parser = new TimekeepingPacketParser(pktDescriptionSource);
            } catch (Exception e) {
                throw new MmtcException("Unable to read or parse packet definition file " + pktDescriptionSource, e);
            }

            // Iterate over binary packets and metadata CSV rows
            // Iterator<CSVRecord> csvIterator = pktMetadata.iterator();
            List<CSVRecord> csvPacketMetadata = pktMetadata.getRecords();
            TimekeepingPacketIterator packetIterator = parser.parsePackets(binaryPktFile);

            if (packetsHaveInvalidFlag()) {
                logger.info("TK packets have an invalid flag.");
            }

            // do preliminary parsing to fill out batchQuery
            final ChillGetFramesBatchQuery batchQuery = new ChillGetFramesBatchQuery(ampcsConfig, Optional.ofNullable(sessionId), Optional.ofNullable(connectionParms));

            int csvRecordIdx = -1;

            while (packetIterator.hasNext() && csvRecordIdx < csvPacketMetadata.size()) {
                TimekeepingRecord pktRecord = packetIterator.next();
                csvRecordIdx += 1;
                CSVRecord csvRecord = csvPacketMetadata.get(csvRecordIdx);

                String ertStr = csvRecord.get(ERT);

                int vcid = pktRecord.getTargetFrameVcid();
                int vcfc = pktRecord.getTargetFrameVcfc();

                // Calculate the beginning of the search interval, which is the current sample's ERT minus the
                // separation value specified in configuration.
                OffsetDateTime endTimeOffset = TimeConvert.parseIsoDoyUtcStr(ertStr);
                OffsetDateTime beginTimeOffset = endTimeOffset.minusSeconds(ampcsConfig.getMaxTkpacketFrameSeparation());

                // Query chill_get_frames for frames whose VCID and VCFC match the current sample's VCID and VCFC and
                // whose ERTs are within the search interval (i.e. "close enough" to but not greater than the current
                // sample's ERT. (Also ask chill_get_frames to return the results sorted by ERT.)
                batchQuery.add(beginTimeOffset, endTimeOffset, vcid, vcfc);
            }

            // run batched chill_get_frames calls
            final ChillGetFramesBatchQuery.Results frameMetadataResults = batchQuery.execute(command -> {
                try {
                    return runSubprocessCsv(command);
                } catch (IOException e) {
                    // todo can i do better here to use this instance's runSubprocess from a static class?
                    throw new RuntimeException(e);
                }
            });

            // reset both CSV (metadata) and packet (data) iterators
            csvRecordIdx = -1;
            packetIterator.reset();

            // do final FrameSample assembly
            while (packetIterator.hasNext() && csvRecordIdx < csvPacketMetadata.size()) {
                TimekeepingRecord pktRecord = packetIterator.next();
                csvRecordIdx += 1;
                CSVRecord csvRecord = csvPacketMetadata.get(csvRecordIdx);
                FrameSample sample = new FrameSample();

                String ertStr = csvRecord.get(ERT);

                int csvVcid;
                if (TimeConvert.isNumeric(csvRecord.get(VCID))) {
                    csvVcid = Integer.parseInt(csvRecord.get(VCID));
                } else {
                    logger.error("VCID of " + csvRecord.get(VCID) +
                            " in metadata for TK packet of ERT " + ertStr + " is invalid. Setting to -1.");
                    csvVcid = -1;
                }

                int csvVcfc;
                if (TimeConvert.isNumeric(csvRecord.get(VCFC))) {
                    csvVcfc = Integer.parseInt(csvRecord.get(VCFC));
                } else {
                    logger.error("VCFC of " + csvRecord.get(VCFC) +
                            " in metadata for TK packet of ERT " + ertStr + " is invalid. Setting to -1.");
                    csvVcfc = -1;
                }

                sample.setSuppErtStr(ertStr);
                sample.setSuppVcid(csvVcid);
                sample.setSuppVcfc(csvVcfc);
                int pktLenFromMetadata = Integer.parseInt(csvRecord.get(PKTLEN));
                if (pktLenFromMetadata != ampcsConfig.tkPacketSize()) {
                    logger.error("TK packet size from AMPCS is " + pktLenFromMetadata +
                            " bytes. TK packet size indicated in the telemetry.tkpacket.tkPacketSizeBytes configuration parameter is " +
                            ampcsConfig.tkPacketSize());
                }

                int vcid = pktRecord.getTargetFrameVcid();
                int vcfc = pktRecord.getTargetFrameVcfc();
                sample.setTkSclkCoarse(pktRecord.getSclkCoarse());
                sample.setTkSclkFine(pktRecord.getSclkFine());
                sample.setTkVcid(vcid);
                sample.setTkVcfc(vcfc);
                sample.setTkRfEncoding(pktRecord.getEncodingMethod());

                if (packetsHaveInvalidFlag()) {
                    sample.setTkValid(!pktRecord.getInvalidFlag());
                }

                if (packetsHaveDownlinkDataRate()) {
                    // TODO: use value from frame csv's bitRate column?
                    sample.setTkDataRateBps(pktRecord.getDownlinkDataRate());
                }
                if (config.containsKey("telemetry.source.plugin.ampcs.frameSizeBits")) {
                    sample.setFrameSizeBits(ampcsConfig.getFrameSizeBits());
                    // TODO: MMTC-302 - Try to determine frame size directly first instead of solely relying on configured default
                }

                // Calculate the beginning of the search interval, which is the current sample's ERT minus the
                // separation value specified in configuration.
                OffsetDateTime endTimeOffset = TimeConvert.parseIsoDoyUtcStr(ertStr);
                OffsetDateTime beginTimeOffset = endTimeOffset.minusSeconds(ampcsConfig.getMaxTkpacketFrameSeparation());

                // Find the retrieved frame whose ERT is closest to but not greater than the current sample's ERT
                Optional<CSVRecord> maybeTargetFrameRecord = frameMetadataResults.findLatestFrameMatching(beginTimeOffset, endTimeOffset, vcid, vcfc);

                // If we didn't find a match, warn and move on to the next sample.
                if (! maybeTargetFrameRecord.isPresent()) {
                    logger.error("For TK packet of ERT " + ertStr + ", no frames found with the same VCID and VCFC and an earlier ERT. This packet will not be used.");
                    continue;
                }

                // Extract data from the frame to populate the sample.
                CSVRecord targetFrameRecord = maybeTargetFrameRecord.get();
                String frameErt = targetFrameRecord.get(FRAME_ERT);
                logger.info("Selected Target Frame ERT: " + frameErt);

                int framePathId;
                if (TimeConvert.isNumeric(targetFrameRecord.get(FRAME_DSSID))) {
                    framePathId = Integer.parseInt(targetFrameRecord.get(FRAME_DSSID));
                    if (TimeConvert.isNumeric(csvRecord.get(DSSID))) {
                        int packetPathId = Integer.parseInt(csvRecord.get(DSSID));
                        if (framePathId != packetPathId) {
                            logger.warn("For TK packet of ERT " + ertStr + ", Ground Station Path ID is different between " +
                                "packet metadata (" + packetPathId + ") and frame metadata (" + framePathId + ").");
                        }
                    } else {
                        logger.error("Ground Station Path ID of " + csvRecord.get(DSSID) +
                            " in metadata for TK packet of ERT " + ertStr + " is invalid.");
                    }
                } else {
                    logger.error("Ground Station Path ID of " + targetFrameRecord.get(FRAME_DSSID) +
                            " in metadata for frame of ERT " + frameErt + " is invalid. Setting to -1.");
                    framePathId = -1;
                }


                int frameVcid;
                if (TimeConvert.isNumeric(targetFrameRecord.get(FRAME_VCID))) {
                    frameVcid = Integer.parseInt(targetFrameRecord.get(FRAME_VCID));
                }
                else {
                    logger.error("VCID of " + targetFrameRecord.get(FRAME_VCID) +
                            " in metadata for frame of ERT " + frameErt + " is invalid. Setting to -1.");
                    frameVcid = -1;
                }

                int frameVcfc;
                if (TimeConvert.isNumeric(targetFrameRecord.get(FRAME_VCFC))) {
                    frameVcfc = Integer.parseInt(targetFrameRecord.get(FRAME_VCFC));
                }
                else {
                    logger.error("VCFC of " + targetFrameRecord.get(FRAME_VCFC) +
                            " in metadata for frame of ERT " + frameErt + " is invalid. Setting to -1.");
                    frameVcfc = -1;
                }

                sample.setErtStr(frameErt);
                sample.setPathId(framePathId);
                sample.setVcid(frameVcid);
                sample.setVcfc(frameVcfc);

                if (sample.getVcid() != sample.getTkVcid() || sample.getVcfc() != sample.getTkVcfc()) {
                    logger.error("VCID and VCFC from TK packet of ERT " + ertStr + " were used to query for target frame, but frame VCID and VCFC don't match. This packet will not be used.");
                    continue;
                }

                samples.add(sample);
            }

            if (packetIterator.hasNext() || (csvRecordIdx != csvPacketMetadata.size() - 1)) {
                // since packets and metadata come from the same single query, something is horribly
                // wrong if the number of packets isn't the same as the number of metadata rows
                throw new MmtcException("The number of binary packets and CSV metadata rows read from chill_get_packets did not match.");
            }

        } catch(IOException e) {
            String msg = "Unable to retrieve TK packets.";
            logger.error(msg, e);
            throw new MmtcException(msg, e);
        }

        return samples;
    }

    /**
     * Retrieve samples whose ERTs are within the specified time range. Populate the samples with data extracted or
     * derived from the contents of timekeeping packets and from the AMPCS metadata of the packets and their frames.
     *
     * @param start the start time of the range, in ERT, for querying telemetry
     * @param stop the stop time of the range, in ERT, for querying telemetry
     * @return the list of frame samples within the time range
     * @throws MmtcException when unable to query or parse telemetry
     */
    private List<FrameSample> getSamplesInRangeNonBatching(OffsetDateTime start, OffsetDateTime stop) throws MmtcException {

        if (!connectedToAmpcs) {
            throw new MmtcException("Not connected; call connect().");
        }

        List<FrameSample> samples = new ArrayList<>();

        try {
            // Convert the start and stop times to string format and remove the trailing 'Z' that results.
            String beginTime = start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME).split("\\+")[0];
            beginTime = beginTime.replace("Z", "");

            String endTime   = stop.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME).split("\\+")[0];
            endTime = endTime.replace("Z", "");

            // Get the APID of the TK packet from configuration parameters.
            String TKPKTAPID = ampcsConfig.getTKPacketApid();

            // Create a temporary raw packet file name.
            String packetOutputFilename = getUniqueFilename("tkpkt");
            logger.trace("Raw TK packet data will be written to " + packetOutputFilename + ".");

            // Run chill_get_packets to query for all of the time correlation packets that are within the selected
            // contact interval. Query by APID and ERT.
            String cmd = chillGdsPath+"/bin/chill_get_packets -m";
            if (sessionId != null) {
                cmd += " -K " + sessionId;
            }
            cmd += " --packetApid " + TKPKTAPID + " --timeType ERT " +
                    "--beginTime " + beginTime + " --endTime " + endTime + " --report --filename " + packetOutputFilename;
            if (connectionParms != null) {
                cmd += " " + connectionParms;
            }
            CSVParser pktMetadata = runSubprocessCsv(cmd);

            // Get the packet metadata field names.
            final String ERT         = ampcsConfig.getTkPacketErtFieldName();
            final String DSSID      = ampcsConfig.getTkPacketDssIdFieldName();
            final String VCID        = ampcsConfig.getTkPacketVcidFieldName();
            final String VCFC        = ampcsConfig.getTkPacketVcfcFieldName();
            final String PKTLEN      = ampcsConfig.getTkPacketLengthFieldName();
            final String FRAME_ERT   = ampcsConfig.getFrameErtFieldName();
            final String FRAME_DSSID = ampcsConfig.getFrameDssIdFieldName();
            final String FRAME_VCID  = ampcsConfig.getFrameVcidFieldName();
            final String FRAME_VCFC  = ampcsConfig.getFrameVcfcFieldName();

            // Get the information on the TK packet binary file.
            Path pktDescriptionSource;
            URI binaryPktFile;
            TimekeepingPacketParser parser;
            try {
                pktDescriptionSource = ampcsConfig.getTkPacketDescriptionFilePath();
                binaryPktFile        = new URI("file://" + packetOutputFilename);
            } catch (URISyntaxException e) {
                throw new MmtcException("Unable to open packet description file " +
                        ampcsConfig.getTkPacketDescriptionFilePath());
            }
            try {
                parser = new TimekeepingPacketParser(pktDescriptionSource);
            } catch (Exception e) {
                throw new MmtcException("Unable to read or parse packet definition file " + pktDescriptionSource, e);
            }

            // Iterate over binary packets and metadata CSV rows
            Iterator<CSVRecord> csvIterator = pktMetadata.iterator();
            TimekeepingPacketIterator packetIterator = parser.parsePackets(binaryPktFile);

            if (packetsHaveInvalidFlag()) {
                logger.info("TK packets have an invalid flag.");
            }

            while (packetIterator.hasNext() && csvIterator.hasNext()) {
                TimekeepingRecord pktRecord = packetIterator.next();
                CSVRecord csvRecord = csvIterator.next();
                FrameSample sample = new FrameSample();

                String ertStr = csvRecord.get(ERT);

                int csvVcid;
                if (TimeConvert.isNumeric(csvRecord.get(VCID))) {
                    csvVcid = Integer.parseInt(csvRecord.get(VCID));
                }
                else {
                    logger.error("VCID of " + csvRecord.get(VCID) +
                            " in metadata for TK packet of ERT " + ertStr + " is invalid. Setting to -1.");
                    csvVcid = -1;
                }

                int csvVcfc;
                if (TimeConvert.isNumeric(csvRecord.get(VCFC))) {
                    csvVcfc = Integer.parseInt(csvRecord.get(VCFC));
                }
                else {
                    logger.error("VCFC of " + csvRecord.get(VCFC) +
                            " in metadata for TK packet of ERT " + ertStr + " is invalid. Setting to -1.");
                    csvVcfc = -1;
                }

                sample.setSuppErtStr(ertStr);
                sample.setSuppVcid(csvVcid);
                sample.setSuppVcfc(csvVcfc);
                int pktLenFromMetadata = Integer.parseInt(csvRecord.get(PKTLEN));
                if (pktLenFromMetadata != ampcsConfig.tkPacketSize()) {
                    logger.error("TK packet size from AMPCS is " + pktLenFromMetadata +
                            " bytes. TK packet size indicated in the telemetry.tkpacket.tkPacketSizeBytes configuration parameter is " +
                            ampcsConfig.tkPacketSize());
                }

                int vcid = pktRecord.getTargetFrameVcid();
                int vcfc = pktRecord.getTargetFrameVcfc();
                sample.setTkSclkCoarse(pktRecord.getSclkCoarse());
                sample.setTkSclkFine(pktRecord.getSclkFine());
                sample.setTkVcid(vcid);
                sample.setTkVcfc(vcfc);
                sample.setTkRfEncoding(pktRecord.getEncodingMethod());

                if (packetsHaveInvalidFlag()) {
                    sample.setTkValid(!pktRecord.getInvalidFlag());
                }

                if (packetsHaveDownlinkDataRate()) {
                    // TODO: use value from frame csv's bitRate column?
                    sample.setTkDataRateBps(pktRecord.getDownlinkDataRate());
                }
                if (config.containsKey("telemetry.source.plugin.ampcs.frameSizeBits")) {
                    sample.setFrameSizeBits(ampcsConfig.getFrameSizeBits());
                    // TODO: MMTC-302 - Try to determine frame size directly first instead of solely relying on configured default
                }

                // Calculate the beginning of the search interval, which is the current sample's ERT minus the
                // separation value specified in configuration.

                OffsetDateTime endTimeOffset = TimeConvert.parseIsoDoyUtcStr(ertStr);
                OffsetDateTime beginTimeOffset = endTimeOffset.minusSeconds(ampcsConfig.getMaxTkpacketFrameSeparation());
                String frameBeginTime = TimeConvert.timeToIsoUtcString(beginTimeOffset);

                // Query chill_get_frames for frames whose VCID and VCFC match the current sample's VCID and VCFC and
                // whose ERTs are within the search interval (i.e. "close enough" to but not greater than the current
                // sample's ERT. (Also ask chill_get_frames to return the results sorted by ERT.)

                cmd = chillGdsPath+"/bin/chill_get_frames -m";
                if (sessionId != null) {
                    cmd += " -K " + sessionId;
                }
                cmd += " --timeType ERT " + "--beginTime " + frameBeginTime +
                        " --endTime " + ertStr + " --vcid " + vcid + " --vcfcs " + vcfc + " --orderBy ERT";
                if (connectionParms != null) {
                    cmd += " " + connectionParms;
                }
                CSVParser frameMetadata = runSubprocessCsv(cmd);

                // Find the retrieved frame whose ERT is closest to but not greater than the current sample's ERT.
                // Since we queried only for frames whose ERTs are not greater than the current sample's ERT *and* we
                // retrieved them in sorted order, this is as simple as taking the very last result frame.

                CSVRecord targetFrameRecord = null;
                String previousErt = null;
                boolean hasSameErt = false;
                for (CSVRecord frameRecord : frameMetadata) {
                    logger.trace("AmpcsTlmWithFrames frameRecord: " + frameRecord.toString());
                    hasSameErt = false;
                    targetFrameRecord = frameRecord;
                    String targetFrameErt = targetFrameRecord.get(FRAME_ERT);
                    if (targetFrameErt.equals(previousErt)) {
                        hasSameErt = true;
                    }
                    previousErt = targetFrameErt;
                }

                // If we didn't find a match, warn and move on to the next sample.

                if (targetFrameRecord == null) {
                    logger.error("For TK packet of ERT " + ertStr + ", no frames found with the same VCID and VCFC and an earlier ERT. This packet will not be used.");
                    continue;
                }

                // If we found multiple matches with the same best ERT, warn.

                if (hasSameErt) {
                    logger.warn("For TK packet of ERT " + ertStr + ", VCID " + vcid + ", and VCFC " + vcfc + ", multiple frames were found with the same closest ERT of " + previousErt + ".");
                }

                // Extract data from the frame to populate the sample.

                String frameErt = targetFrameRecord.get(FRAME_ERT);
                logger.info("Selected Target Frame ERT: " + frameErt);

                int framePathId;
                if (TimeConvert.isNumeric(targetFrameRecord.get(FRAME_DSSID))) {
                    framePathId = Integer.parseInt(targetFrameRecord.get(FRAME_DSSID));
                    if (TimeConvert.isNumeric(csvRecord.get(DSSID))) {
                        int packetPathId = Integer.parseInt(csvRecord.get(DSSID));
                        if (framePathId != packetPathId) {
                            logger.warn("For TK packet of ERT " + ertStr + ", Ground Station Path ID is different between " +
                                    "packet metadata (" + packetPathId + ") and frame metadata (" + framePathId + ").");
                        }
                    } else {
                        logger.error("Ground Station Path ID of " + csvRecord.get(DSSID) +
                                " in metadata for TK packet of ERT " + ertStr + " is invalid.");
                    }
                } else {
                    logger.error("Ground Station Path ID of " + targetFrameRecord.get(FRAME_DSSID) +
                            " in metadata for frame of ERT " + frameErt + " is invalid. Setting to -1.");
                    framePathId = -1;
                }


                int frameVcid;
                if (TimeConvert.isNumeric(targetFrameRecord.get(FRAME_VCID))) {
                    frameVcid = Integer.parseInt(targetFrameRecord.get(FRAME_VCID));
                }
                else {
                    logger.error("VCID of " + targetFrameRecord.get(FRAME_VCID) +
                            " in metadata for frame of ERT " + frameErt + " is invalid. Setting to -1.");
                    frameVcid = -1;
                }

                int frameVcfc;
                if (TimeConvert.isNumeric(targetFrameRecord.get(FRAME_VCFC))) {
                    frameVcfc = Integer.parseInt(targetFrameRecord.get(FRAME_VCFC));
                }
                else {
                    logger.error("VCFC of " + targetFrameRecord.get(FRAME_VCFC) +
                            " in metadata for frame of ERT " + frameErt + " is invalid. Setting to -1.");
                    frameVcfc = -1;
                }

                sample.setErtStr(frameErt);
                sample.setPathId(framePathId);
                sample.setVcid(frameVcid);
                sample.setVcfc(frameVcfc);

                if (sample.getVcid() != sample.getTkVcid() || sample.getVcfc() != sample.getTkVcfc()) {
                    logger.error("VCID and VCFC from TK packet of ERT " + ertStr + " were used to query for target frame, but frame VCID and VCFC don't match. This packet will not be used.");
                    continue;
                }

                samples.add(sample);
            }
            if (packetIterator.hasNext() || csvIterator.hasNext()) {
                // since packets and metadata come from the same single query, something is horribly
                // wrong if the number of packets isn't the same as the number of metadata rows
                throw new MmtcException("The number of binary packets and CSV metadata rows read from chill_get_packets did not match.");
            }

        } catch(IOException e) {
            String msg = "Unable to retrieve TK packets.";
            logger.error(msg, e);
            throw new MmtcException(msg, e);
        }

        return samples;
    }

    @Override
    public String getName() {
        return "AmpcsTlmWithFrames";
    }

    static {
        logger.info("Proactively loading class: " + Range.class.getCanonicalName());
    }

    private static class ChillGetFramesBatchQuery {

        private static class Results {
            private final List<CSVRecord> results;
            private final AmpcsTelemetrySourceConfig ampcsConfig;

            public Results(AmpcsTelemetrySourceConfig ampcsConfig) {
                this.results = new ArrayList<>();
                this.ampcsConfig = ampcsConfig;
            }

            public void addResults(List<CSVRecord> partialResults) {
                this.results.addAll(partialResults);
            }

            public Optional<CSVRecord> findLatestFrameMatching(OffsetDateTime beginErt, OffsetDateTime endErt, int vcid, int vcfc) {
                List<CSVRecord> matchingSortedRecords = this.results
                        .stream()
                        .filter(rec -> {
                            // the actual target frame must be within ERT bounds and have the expected VCID and VCFC
                            final OffsetDateTime frameErt = TimeConvert.parseIsoDoyUtcStr(rec.get(ampcsConfig.getFrameErtFieldName()));

                            final boolean isWithinBeginErtBound = frameErt.isEqual(beginErt) || frameErt.isAfter(beginErt);
                            final boolean isWithinEndErtBound = frameErt.isEqual(endErt) || frameErt.isBefore(endErt);
                            final boolean matchesVcidAndVcfc = (Integer.parseInt(rec.get(ampcsConfig.getFrameVcidFieldName())) == vcid)
                                    && (Integer.parseInt(rec.get(ampcsConfig.getFrameVcfcFieldName())) == vcfc);

                            return isWithinBeginErtBound && isWithinEndErtBound && matchesVcidAndVcfc;
                        })
                        .sorted((a,b) -> {
                            OffsetDateTime aErt = TimeConvert.parseIsoDoyUtcStr(a.get(ampcsConfig.getFrameErtFieldName()));
                            OffsetDateTime bErt = TimeConvert.parseIsoDoyUtcStr(b.get(ampcsConfig.getFrameErtFieldName()));
                            return bErt.compareTo(aErt);
                        })
                        .collect(Collectors.toList());

                Optional<CSVRecord> latestMatching = matchingSortedRecords.stream().findFirst();

                // log a warning if there were multiple matching frames that match the latest's frame's ERT
                if (latestMatching.isPresent()) {
                    final String latestMatchingErt = latestMatching.get().get(ampcsConfig.getFrameErtFieldName());

                    final long numMatchingErts = matchingSortedRecords.stream()
                            .map(rec -> rec.get(ampcsConfig.getFrameErtFieldName()))
                            .filter(ert -> ert.equals(latestMatchingErt))
                            .count();

                    if (numMatchingErts > 1) {
                        logger.warn(String.format(
                                "For TK packets within ERT %s - %s, VCID %d, and VCFC %d, multiple frames were found with the same closest ERT of %s.",
                                beginErt,
                                endErt,
                                vcid,
                                vcfc,
                                latestMatchingErt
                        ));
                    }
                }

                return latestMatching;
            }
        }

        private final AmpcsTelemetrySourceConfig ampcsConfig;
        private final String chillGdsPath;
        private final Optional<String> sessionId;
        private final Optional<String> connectionParams;

        private final Map<Integer, RangeSet<OffsetDateTime>> queryRangesByVcid;
        private final Map<Integer, Set<Integer>> vcfcsByVcid;

        public ChillGetFramesBatchQuery(AmpcsTelemetrySourceConfig config, Optional<String> sessionId, Optional<String> connectionParams) throws MmtcException {
            this.chillGdsPath = Environment.getEnvironmentVariable("CHILL_GDS");
            if (this.chillGdsPath == null) {
                throw new MmtcException("Environment variable $CHILL_GDS is not set.");
            }

            this.ampcsConfig = config;
            this.sessionId = sessionId;
            this.connectionParams = connectionParams;

            queryRangesByVcid = new HashMap<>();
            vcfcsByVcid = new HashMap<>();
        }

        public void add(OffsetDateTime startTime, OffsetDateTime endTime, Integer vcid, Integer vcfc) {
            if (! queryRangesByVcid.containsKey(vcid)) {
                queryRangesByVcid.put(vcid, TreeRangeSet.create());
            }

            if (! vcfcsByVcid.containsKey(vcid)) {
                vcfcsByVcid.put(vcid, new HashSet<>());
            }

            queryRangesByVcid.get(vcid).add(new OffsetDateTimeRange(startTime, endTime).toRange());
            vcfcsByVcid.get(vcid).add(vcfc);
        }

        public Results execute(Function<String, CSVParser> subprocessRunner) {
            final Results results = new Results(this.ampcsConfig);

            for (Integer vcid : queryRangesByVcid.keySet()) {
                List<OffsetDateTimeRange> rangesToRunOver = queryRangesByVcid.get(vcid).asRanges()
                        .stream()
                        .map(OffsetDateTimeRange::fromRange)
                        .sorted()
                        .collect(Collectors.toList());

                List<String> vcfcsToQueryFor = vcfcsByVcid.get(vcid).stream().sorted().map(i -> String.valueOf(i)).collect(Collectors.toList());

                for (OffsetDateTimeRange range : rangesToRunOver) {
                    String cmd = chillGdsPath + "/bin/chill_get_frames -m";
                    if (sessionId.isPresent()) {
                        cmd += " -K " + sessionId.get();
                    }
                    cmd += " --timeType ERT " + "--beginTime " + TimeConvert.timeToIsoUtcString(range.getStart()) +
                            " --endTime " + TimeConvert.timeToIsoUtcString(range.getStop()) + " --vcid " + vcid + " --vcfcs " + String.join(",", vcfcsToQueryFor) + " --orderBy ERT";
                    if (connectionParams.isPresent()) {
                        cmd += " " + connectionParams.get();
                    }

                    final CSVParser output = subprocessRunner.apply(cmd);
                    List<CSVRecord> outputRecs = output.getRecords();
                    for (CSVRecord r : outputRecs) {
                        logger.trace("AmpcsTlmWithFrames frameRecord: " + r.toString());
                    }
                    results.addResults(outputRecs);
                }
            }

            return results;
        }
    }
}
