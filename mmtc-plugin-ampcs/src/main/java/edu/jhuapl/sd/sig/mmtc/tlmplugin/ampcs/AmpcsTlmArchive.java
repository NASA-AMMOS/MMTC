package edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.tlm.TimekeepingPacketIterator;
import edu.jhuapl.sd.sig.mmtc.tlm.TimekeepingPacketParser;
import edu.jhuapl.sd.sig.mmtc.tlm.TimekeepingRecord;
import edu.jhuapl.sd.sig.mmtc.util.CollectionUtil;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.net.URI;

/**
 * <P>The AmpcsTlmArchive class implements the AmpcsTelemetrySource abstract class. This class provides the funtions that enable
 * the MMTC to query telemetry from the AMMOS AMPCS data store. This class is provided for missions that use AMPCS
 * as their core command and telemetry system and is the default provided.</P>
 *
 * <P>The functions in this class use the AMPCS chill_get_packets and chill_get_chanvals interfaces to access the
 * telemetry store. These are run as commands and their output is captured for processing. The TK packets are
 * obtained using the chill_get_packets command which writes the raw packet to a temporary file. This application
 * then reads that file and parses it in accordance with the packet description XML file as defined in the tk_packet.xsd
 * schema file. When using this class, the mission must provide sets of TK packets with consecutive
 * VCFC values. It assumes that it can associate a supplemental frame packet with its target frame
 * packet by looking back the specified and fixed number of packets (usually 1). If one cannot assume
 * consecutive packets, this class cannot be used. Look at the AmpcsTlmWithFrames class instead.</P>
 *
 * <P>When chill_get_packets is called, the binary TK packet is written to a local, temporary file and parsed by this application
 * as specified in the mission's packet definition .xml file that implements the tk_packet.xsd schema.
 * The chill_get_packets command also returns metadata in text CSV form which this application captures.
 * This metadata contains the crucial ERT as well as the associated supplemental fram VCID, VCFC,
 * and ground station information that can be used for filter checks. Therefore, this class
 * contains functions that extract the data from the bodies of the TK packets and parse the associated
 * AMPCS metadata for essential information.</P>
 */
public class AmpcsTlmArchive extends AmpcsTelemetrySource {
    public AmpcsTlmArchive() {
        super();
    }

    @Override
    public void applyConfiguration(TimeCorrelationAppConfig config) throws MmtcException {
        super.applyConfiguration(config);

        Set<String> enabledFilters = config.getFilters().keySet();
        if (enabledFilters.contains(TimeCorrelationAppConfig.CONSEC_MC_FRAME_FILTER)) {
            String errorString = "When using the AmpcsTlmArchive telemetry source, the " +
                    TimeCorrelationAppConfig.CONSEC_MC_FRAME_FILTER +
                    " filter is not applicable and must be disabled by setting the configuration option " +
                    "filter.<filter name>.enabled to false.";
            throw new MmtcException(errorString);
        }
    }

    @Override
    public String getName() {
        return "AmpcsTlmArchive";
    }

    /**
     * Read each record from the table and attempt to parse the SCLK, ERT, and
     * other data. If the ERT is within the specified time range, add it to the
     * list of samples to return.
     *
     * @param start the start time of the range, in ERT, for querying telemetry
     * @param stop the stop time of the range, in ERT, for querying telemetry
     * @return the list of frame samples within the time range
     * @throws MmtcException when unable to query or parse telemetry
     */
    public List<FrameSample> getSamplesInRange(OffsetDateTime start, OffsetDateTime stop) throws MmtcException {
        if (!connectedToAmpcs) {
            throw new MmtcException("Session ID not provided. Call connect(sessionID).");
        }

        final List<FrameSample> samples = new ArrayList<>();

        final int packetHeaderFineSclkModulus = ampcsConfig.getPacketHeaderFineSclkModulus();

        if (! (packetHeaderFineSclkModulus > 0) ) {
            throw new MmtcException("AmpcsTlmArchive requires a positive packet header fine SCLK modulus to be set");
        }

        try {
            // Convert the start and stop times to string format and remove the trailing 'Z' that results.
            String beginTime = start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME).split("\\+")[0];
            beginTime = beginTime.replace("Z", "");

            String endTime   = stop.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME).split("\\+")[0];
            endTime = endTime.replace("Z", "");

            // Get the APID of the TK packet from configuration parameters.
            String TKPKTAPID = ampcsConfig.getTKPacketApid();

            // Get the number of samples in a frame set.
            int samplesPerSet = config.getSamplesPerSet();

            // Get all TK packets in the sampling interval.
            ArrayList<String> packets = new ArrayList();

            // Create a temporary raw packet file name.
            String packetOutputFilename = getUniqueFilename( "tkpkt");
            logger.trace("Raw TK packet data will be written to " + packetOutputFilename + ".");

            // Run chill_get_* to query for all of the time correlation packets that are within the selected contact interval.
            // Query by APID and ERT.
            String cmd = chillGdsPath+"/bin/chill_get_packets -m";
            if (sessionId != null) {
                cmd += " -K " + sessionId;
            }
            cmd += " --packetApid " + TKPKTAPID + " --timeType ERT " +
                    "--beginTime " + beginTime + " --endTime " + endTime + " --report --filename " + packetOutputFilename;
            if (connectionParms != null) {
                cmd += " " + connectionParms;
            }
            if (config.isVcidFilterEnabled()) {
                cmd += " --vcid " + StringUtils.join(CollectionUtil.supersetOf(config.getVcidFilterValidVcidGroups()), ",");
            }

            CSVParser pktMetadata = runSubprocessCsv(cmd);

            FrameSample sample;
            FrameSample supplementalSample;
            int packetNum = 0;
            int pktLenFromMetadata;

            // Get the packet metadata field names.
            final String SCET        = ampcsConfig.getTkPacketScetFieldName();
            final String ERT         = ampcsConfig.getTkPacketErtFieldName();
            final String DSSID       = ampcsConfig.getTkPacketDssIdFieldName();
            final String VCID        = ampcsConfig.getTkPacketVcidFieldName();
            final String VCFC        = ampcsConfig.getTkPacketVcfcFieldName();
            final String PACKET_SCLK = ampcsConfig.getTkPacketSclkFieldName();
            final String PKTLEN      = ampcsConfig.getTkPacketLengthFieldName();

            // Get the information on the TK packet binary file.
            URI pktDescriptionSource;
            URI binaryPktFile;
            try {
                pktDescriptionSource = new URI(ampcsConfig.getTkPacketDescriptionFile());
                binaryPktFile        = new URI("file://" + packetOutputFilename);
            } catch (URISyntaxException e) {
                throw new MmtcException("Unable to open packet description file " + ampcsConfig.getTkPacketDescriptionFile());
            }

            TimekeepingPacketParser parser;
            try {
                parser = new TimekeepingPacketParser(pktDescriptionSource);
            } catch (Exception e) {
                throw new MmtcException("Unable to read or parse packet definition file " + pktDescriptionSource, e);

            }

            // chill_get_packets gives us a sequence of frames/packets, and each one can be a target frame and/or a supplemental frame.
            // For example, if supplementalSampleOffset=2 and we receive 6 frames from chill_get_packets, then:
            //   * packet 1 is the target frame of packet 3
            //   * packet 2 is the target frame of packet 4
            //   * packet 3 is the supplemental frame of packet 1 *and* the target frame of packet 5
            //   * packet 4 is the supplemental frame of packet 2 *and* the target frame of packet 6
            //   * packet 5 is the supplemental frame of packet 3
            //   * packet 6 is the supplemental frame of packet 4
            //
            // chill_get_packets gives two different forms of output: CSV text containing metadata about each frame, and a binary file
            // containing all the raw packets. For each *target* frame, we need to populate a FrameSample object with information about
            // the target frame itself as well as information about the corresponding supplemental frame. chill_get_packets' CSV output
            // contains information that is relevant to both target frames and supplemental frames, whereas fields in its binary packet
            // output are only relevant to supplemental frames.
            //
            // Naturally, we use a CSV parser to iterate over the CSV rows, and we use a packet parser to iterate over the individual
            // binary packets. However, since we need to read CSV rows for all frames, whether they're target or supplemental frames,
            // but we only need to read binary packets for supplemental frames, the iterators end up needing different numbers of
            // iterations. To make the code more readable, we put each iterator in its own loop rather than using a single loop and
            // carefully juggling the iterator counts/indices.

            TimekeepingPacketIterator packetIterator = parser.parsePackets(binaryPktFile);

            // Iterate over the binary packets, copying field values into the correct supplemental frame.
            // First, skip supplementalSampleOffset packets. They aren't supplemental frames for any packets in the current set.
            for (int m = 0; m < config.getSupplementalSampleOffset() && packetIterator.hasNext(); m++) {
                packetIterator.next();
            }

            // The next packet is the supplemental frame for packet #0. In other words, packet #0 is the target frame of
            // packetIterator's next packet. We iterate through the remaining packets from there.
            while (packetIterator.hasNext()) {
                TimekeepingRecord pktrec = packetIterator.next();
                sample = new FrameSample();

                sample.setTkSclkCoarse(pktrec.getSclkCoarse());
                sample.setTkSclkFine(pktrec.getSclkFine());
                sample.setTkVcid(pktrec.getTargetFrameVcid());
                sample.setTkVcfc(pktrec.getTargetFrameVcfc());
                sample.setTkRfEncoding(pktrec.getEncodingMethod());

                if (packetsHaveInvalidFlag()) {
                    sample.setTkValid(!pktrec.getInvalidFlag());
                }

                if (packetsHaveDownlinkDataRate()) {
                    sample.setTkDataRateBps(pktrec.getDownlinkDataRate());
                }

                if (config.containsKey("telemetry.source.plugin.ampcs.frameSizeBits")) {
                    sample.setFrameSizeBits(ampcsConfig.getFrameSizeBits());
                    // TODO: MMTC-302 - Try to determine frame size directly first instead of solely relying on configured default
                }

                samples.add(sample);
            }

            // Iterate over the CSV rows, copying metadata values from each row into the correct frame(s).
            for (CSVRecord csvRecord : pktMetadata) {
                logger.trace("Packet Metadata: " + csvRecord.toString());

                String ertStr = csvRecord.get(ERT);

                sample = null;
                supplementalSample = null;
                // If the current packet is a target frame, retrieve the corresponding FrameSample object.
                if (packetNum < samples.size()) {
                    sample = samples.get(packetNum);
                }
                // If the current packet is a supplemental frame, retrieve the corresponding FrameSample object.
                // The current packet is the supplemental frame for the packet that is
                // <supplementalSampleOffset> behind, if that exists.
                if (packetNum - config.getSupplementalSampleOffset() >= 0) {
                    supplementalSample = samples.get(packetNum - config.getSupplementalSampleOffset());
                }

                int pathId;
                if (TimeConvert.isNumeric(csvRecord.get(DSSID))) {
                    pathId = Integer.parseInt(csvRecord.get(DSSID));
                }
                else {
                    logger.error("Ground Station Path ID of " + csvRecord.get(DSSID) +
                            " in metadata for TK packet of ERT " + ertStr + " is invalid. Setting to -1.");
                    pathId = -1;
                }

                int vcid;
                if (TimeConvert.isNumeric(csvRecord.get(VCID))) {
                    vcid = Integer.parseInt(csvRecord.get(VCID));
                }
                else {
                    logger.error("VCID of " + csvRecord.get(VCID) +
                            " in metadata for TK packet of ERT " + ertStr + " is invalid. Setting to -1.");
                    vcid = -1;
                }

                int vcfc;
                if (TimeConvert.isNumeric(csvRecord.get(VCFC))) {
                    vcfc = Integer.parseInt(csvRecord.get(VCFC));
                }
                else {
                    logger.error("VCFC of " + csvRecord.get(VCFC) +
                            " in metadata for TK packet of ERT " + ertStr + " is invalid. Setting to -1.");
                    vcfc = -1;
                }

                if (supplementalSample != null) {
                    supplementalSample.setSuppVcid(vcid);
                    supplementalSample.setSuppVcfc(vcfc);
                    supplementalSample.setSuppErtStr(ertStr);
                }

                if (sample != null) {
                    sample.setScet(csvRecord.get(SCET));
                    sample.setErtStr(ertStr);
                    sample.setPathId(pathId);
                    sample.setVcid(vcid);
                    sample.setVcfc(vcfc);

                    double sample_sclk = Double.parseDouble(csvRecord.get(PACKET_SCLK));
                    int sample_sclk_coarse = (int)sample_sclk;
                    sample.setSclkCoarse(sample_sclk_coarse);

                    // Convert the decimal fraction of SCLK to the original SCLK fine time integer.
                    int sample_sclk_fine = (int)((sample_sclk - sample_sclk_coarse) * packetHeaderFineSclkModulus);
                    sample.setSclkFine(sample_sclk_fine);

                    pktLenFromMetadata = Integer.parseInt(csvRecord.get(PKTLEN));
                    if (pktLenFromMetadata != ampcsConfig.tkPacketSize()) {
                        logger.error("TK packet size from AMPCS is " + pktLenFromMetadata +
                                " bytes. TK packet size indicated in the telemetry.source.plugin.ampcs.tkpacket.tkPacketSizeBytes configuration parameter is " +
                                ampcsConfig.tkPacketSize());
                    }
                }

                packetNum++;
            } // end for pktMetadata

        } catch(IOException e) {
            String msg = "Unable to retrieve TK packets.";
            logger.error(msg, e);
            throw new MmtcException(msg, e);
        }

        return samples;
    }
}
