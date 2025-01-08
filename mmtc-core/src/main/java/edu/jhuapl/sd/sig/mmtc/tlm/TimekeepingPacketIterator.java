package edu.jhuapl.sd.sig.mmtc.tlm;

import java.math.BigDecimal;
import java.util.*;

import edu.jhuapl.sd.sig.mmtc.tlm.tkpacket.PacketDefinition;
import edu.jhuapl.sd.sig.mmtc.tlm.tkpacket.TelemetryPoint;

/**
 * Parses timekeeping packets from a byte array using a given timekeeping packet definition.
 *
 * NOTE: This class only parses and returns fields that are 32 bits or fewer unless
 * type is specified as DOUBLE_FLOAT in the packet definition.
 */
public class TimekeepingPacketIterator implements Iterator<TimekeepingRecord> {
    private List<TelemetryPoint> telemetryPoints;
    private int lastDefinedBitOffset = 0;
    private byte[] packets;
    private int currentOffset = 0;
    private boolean missedCcsdsPacketLength = false;

    public enum expectedType {
        UNSIGNED_INT,
        SINGLE_FLOAT,
        DOUBLE_FLOAT
    }

    /**
     * Constructor.
     *
     * @param packetDefinition The definition of the timekeeping packet fields
     * @param packets The byte array to be parsed; must contain zero or more consecutive
     * timekeeping packets and no extraneous bytes
     */
    public TimekeepingPacketIterator(PacketDefinition packetDefinition, byte[] packets) {
        telemetryPoints = packetDefinition.getTelemetryPoints();
        this.packets = packets;
        boolean hasCcsdsPacketLength = false;
        for (TelemetryPoint telemetryPoint : telemetryPoints) {
            if (telemetryPoint.getName().equals("CCSDSPacketLength")) {
                hasCcsdsPacketLength = true;
            }
            if (telemetryPoint.getOffset() + telemetryPoint.getLength() > lastDefinedBitOffset) {
                lastDefinedBitOffset = telemetryPoint.getOffset() + telemetryPoint.getLength();
            }
        }
        if (!hasCcsdsPacketLength) {
            telemetryPoints.add(new TelemetryPoint("CCSDSPacketLength", 32, 16, "UNSIGNED_INT")); // NOTE we assume CCSDS packet; packet length field is 2-byte field at byte offset 4 in primary header
        }
    }

    /**
     * Indicates whether there are remaining bytes to be parsed.
     *
     * NOTE: This method does not guarantee that next() will succeed. Next() may instead throw
     * UnsupportedOperationException.
     */
    @Override
    public boolean hasNext() {
        return !missedCcsdsPacketLength && currentOffset < packets.length;
    }

    /**
     * Returns the next packet parsed from the remaining bytes.
     *
     * @throws NoSuchElementException If there are no remaining bytes
     * @throws UnsupportedOperationException If the packet did not contain a valid length field
     */
    @Override
    public TimekeepingRecord next() throws NoSuchElementException {
        if (currentOffset >= packets.length) {
            throw new NoSuchElementException();
        }
        if (missedCcsdsPacketLength) {
            throw new UnsupportedOperationException("Failed to extract packet length from a previous packet, so can't find start of subsequent packets.");
        }

        int packetLength = 0;
        int sclkCoarse = 0;
        int sclkFine = 0;
        boolean invalidFlag = false;
        int targetFrameVcid = 0;
        int targetFrameVcfc = 0;
        int encodingMethod = 0;
        BigDecimal downlinkDataRate = BigDecimal.valueOf(0);

        missedCcsdsPacketLength = true;

        for (TelemetryPoint telemetryPoint : telemetryPoints) {
            expectedType tlmPointType;
            try {
                tlmPointType = expectedType.valueOf(telemetryPoint.getType().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new UnsupportedOperationException(String.format("No valid type specified for %s in packet definition. " +
                        "Type must be UNSIGNED_INT, SINGLE_FLOAT, or DOUBLE_FLOAT", telemetryPoint.getName()));
            }
            long value = 0;
            int pointOffset = telemetryPoint.getOffset();
            int offsetWithinByte = pointOffset % 8;
            int length = telemetryPoint.getLength();
            while (length > 0) {
                try {
                    if (length + offsetWithinByte > 8) {
                        int numBits = 8 - offsetWithinByte;
                        value = (value << 8) + (int) (packets[currentOffset + pointOffset / 8] & ((1 << numBits) - 1));
                        pointOffset += numBits;
                        offsetWithinByte = 0;
                        length -= numBits;
                    } else {
                        int numBits = 8 - offsetWithinByte - length;
                        value = (value << length) + (int) ((packets[currentOffset + pointOffset / 8] >> numBits) & ((1 << length) - 1));
                        length = 0;
                    }
                }
                catch (ArrayIndexOutOfBoundsException e) {
                    throw new UnsupportedOperationException("Expected more fields but encountered end of bytestream while parsing packet");
                }
            }
            switch (telemetryPoint.getName()) {
                case "CCSDSPacketLength":
                    missedCcsdsPacketLength = false;
                    packetLength = (int) (value + 1 + 6); //NOTE we assume CCSDS packet; field value is one less than packet length excluding 6-byte primary header
                    if (lastDefinedBitOffset > packetLength * 8) {
                        currentOffset += packetLength; //NOTE for this release, we'll still update currentOffset to allow subsequent calls to next().
                        //we'll come back and change this code if we get real-world evidence that encountering a
                        //packet that's too short to support TK fields indicates that parsing is entirely broken and
                        //all subsequent packets should be discarded
                        throw new UnsupportedOperationException(String.format(
                                "Packet at offset 0x%x is %d bytes long, but the timekeeping packet definition contains one or more fields that extend beyond that.",
                                currentOffset, packetLength));
                    }
                    break;
                case "SclkCoarse":
                    sclkCoarse = (int) value;
                    break;
                case "SclkFine":
                    sclkFine = (int) value;
                    break;
                case "InvalidFlag":
                    invalidFlag = value > 0;
                    break;
                case "TargetFrameVcid":
                    targetFrameVcid = (int) value;
                    break;
                case "TargetFrameVcfc":
                    targetFrameVcfc = (int) value;
                    break;
                case "EncodingMethod":
                    encodingMethod = (int) value;
                    break;

                case "DownlinkDataRate":
                    switch (tlmPointType) {
                        case UNSIGNED_INT:
                            downlinkDataRate = BigDecimal.valueOf(value);
                            break;
                        case SINGLE_FLOAT:
                            downlinkDataRate = BigDecimal.valueOf(Float.intBitsToFloat((int) value));
                            break;
                        case DOUBLE_FLOAT:
                            downlinkDataRate = BigDecimal.valueOf(Double.longBitsToDouble(value));
                            break;
                    }
                    break;
            }
        }

        if (missedCcsdsPacketLength) {
            throw new UnsupportedOperationException("Failed to extract packet length while parsing packet; length field is missing from TK packet definition");
        }
        currentOffset += packetLength;

        return new TimekeepingRecord(sclkCoarse, sclkFine, invalidFlag, targetFrameVcid, targetFrameVcfc, encodingMethod,
                downlinkDataRate);
    }
}