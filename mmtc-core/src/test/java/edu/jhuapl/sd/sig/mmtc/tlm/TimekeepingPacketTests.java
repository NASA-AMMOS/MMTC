package edu.jhuapl.sd.sig.mmtc.tlm;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.bind.JAXBException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * These tests verify that the TimekeepingPacketParser works correctly when it
 * is instantiated with input data rather than waiting until later to
 * initialize the input. All nontrivial test cases then verify that the parser
 * continues to work correctly after it is reinitialized with new data.
 */
public class TimekeepingPacketTests {
    private static TimekeepingPacketParser nullParser;
    private static TimekeepingPacketParser emptyParser;
    private static TimekeepingPacketParser arrayParser;
    private static TimekeepingPacketParser fileParser;
    private static TimekeepingPacketParser multipacketArrayParser;
    private static TimekeepingPacketParser multipacketArrayWithBadLengthParser;
    private static TimekeepingPacketParser multipacketFileParser;
    private static TimekeepingPacketParser floatDownlinkPacketParser;
    private static TimekeepingPacketParser doubleDownlinkPacketParser;
    private static byte[] packet1 = new byte[] { (byte) 0xcf, (byte) 0x4, (byte) 0x6d, (byte) 0x14, (byte) 0x00,
            (byte) 0x16, // CCSDS primary header
            (byte) 0x98, (byte) 0xa1, (byte) 0x51, (byte) 0x72, (byte) 0x67, (byte) 0x6e, // secondary header
            (byte) 0x92, (byte) 0xec, (byte) 0x22, (byte) 0x3f, // BIT 0 timestamp seconds
            (byte) 0xc3, (byte) 0x9e, // BIT 0 timestamp subseconds
            (byte) 0x86, (byte) 0xf3, // spare (15b), SCLK/MET missed flag (1b)
            (byte) 0x36, // reserved (2b), frame VCID (6b)
            (byte) 0xb7, (byte) 0xcb,  // frame VCFC
            (byte) 0x68, (byte) 0xff, // encoding mode
            (byte) 0x00, (byte) 0x01, (byte) 0x86, (byte) 0xa0 // downlink rate
    };
    private static byte[] packet2 = new byte[] { // Two packets with float downlinks -- Identical to tkpacket_2_floatDownlinks.dat
            //first packet
            (byte)0xcf, (byte)0x45, (byte)0x6d, (byte)0x14, (byte)0x00, (byte)0x16, //CCSDS primary header
            (byte)0x98, (byte)0xa1, (byte)0x51, (byte)0x72, (byte)0x67, (byte)0x6e, //secondary header
            (byte)0x92, (byte)0xec, (byte)0x22, (byte)0x3f, //BIT 0 timestamp seconds
            (byte)0xc3, (byte)0x9e, //BIT 0 timestamp subseconds
            (byte)0x86, (byte)0xf3, //spare (15b), SCLK/MET missed flag (1b)
            (byte)0x36, //reserved (2b), frame VCID (6b)
            (byte)0xb7, (byte)0xcb, (byte)0x68, //frame VCFC
            (byte)0xff, //encoding mode
            (byte) 0x47, (byte) 0xe8, (byte) 0x6c, (byte) 0x40, //downlink rate
            //second packet
            (byte)0x1e, (byte)0xe4, (byte)0x7a, (byte)0xd4, (byte)0x00, (byte)0x16, //CCSDS primary header
            (byte)0x8d, (byte)0x91, (byte)0xe5, (byte)0x2e, (byte)0xfa, (byte)0xff, //secondary header
            (byte)0x71, (byte)0xb3, (byte)0xcd, (byte)0x52, //BIT 0 timestamp seconds
            (byte)0x07, (byte)0x31, //BIT 0 timestamp subseconds
            (byte)0x28, (byte)0xb6, //spare (15b), SCLK/MET missed flag (1b)
            (byte)0x47, //reserved (2b), frame VCID (6b)
            (byte)0x88, (byte)0x5b, (byte)0x69, //frame VCFC
            (byte)0xa7, //encoding mode
            (byte)0x47,  (byte)0xe8, (byte)0x6c, (byte)0x40 //downlink rate
    };
    private static byte[] packet3 = new byte[] {
            //first packet
            (byte)0xcf, (byte)0x45, (byte)0x6d, (byte)0x14, (byte)0x00, (byte)0x15, //CCSDS primary header with length value that's too short for TK packet definition
            (byte)0x98, (byte)0xa1, (byte)0x51, (byte)0x72, (byte)0x67, (byte)0x6e, //secondary header
            (byte)0x92, (byte)0xec, (byte)0x22, (byte)0x3f, //BIT 0 timestamp seconds
            (byte)0xc3, (byte)0x9e, //BIT 0 timestamp subseconds
            (byte)0x86, (byte)0xf3, //spare (15b), SCLK/MET missed flag (1b)
            (byte)0x36, //reserved (2b), frame VCID (6b)
            (byte)0xb7, (byte)0xcb, (byte)0x68, //frame VCFC
            (byte)0xff, //encoding mode
            (byte) 0x47, (byte) 0xe8, (byte) 0x83, //artificially shortened downlink rate field
            //second packet
            (byte)0x1e, (byte)0xe4, (byte)0x7a, (byte)0xd4, (byte)0x00, (byte)0x16, //CCSDS primary header
            (byte)0x8d, (byte)0x91, (byte)0xe5, (byte)0x2e, (byte)0xfa, (byte)0xff, //secondary header
            (byte)0x71, (byte)0xb3, (byte)0xcd, (byte)0x52, //BIT 0 timestamp seconds
            (byte)0x07, (byte)0x31, //BIT 0 timestamp subseconds
            (byte)0x28, (byte)0xb6, //spare (15b), SCLK/MET missed flag (1b)
            (byte)0x47, //reserved (2b), frame VCID (6b)
            (byte)0x88, (byte)0x5b, (byte)0x69, //frame VCFC
            (byte)0xa7, //encoding mode
            (byte)0x48,  (byte)0x3b, (byte)0xcc, (byte)0xec //downlink rate
    };

    private static byte[] packet4 = new byte[] { // Identical to packet 1 but with 64-bit double precision downlink rate
            (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78, (byte) 0x9a,
            (byte) 0xbc, // CCSDS primary header
            (byte) 0xde, (byte) 0xf0, (byte) 0x24, (byte) 0x68, (byte) 0xac, (byte) 0xe0, // secondary header
            (byte) 0x36, (byte) 0x9c, (byte) 0xf2, (byte) 0x6a, // BIT 0 timestamp seconds
            (byte) 0xe2, (byte) 0x7c, // BIT 0 timestamp subseconds
            (byte) 0x17, (byte) 0xd3, // spare (15b), SCLK/MET missed flag (1b)
            (byte) 0x9f, // reserved (2b), frame VCID (6b)
            (byte) 0x5c, (byte) 0x3b, (byte) 0x2a, // frame VCFC
            (byte) 0x2b, // encoding mode
            (byte) 0x40, (byte) 0xfd, (byte) 0x10, (byte) 0x79, (byte) 0xe0, (byte) 0x0d, (byte) 0x1b, (byte) 0x71 // downlink rate
    };

    private static byte[] packet5 = new byte[] { // Identical to packet 3 but with uint downlink rate
            //first packet
            (byte)0xcf, (byte)0x45, (byte)0x6d, (byte)0x14, (byte)0x00, (byte)0x15, //CCSDS primary header with length value that's too short for TK packet definition
            (byte)0x98, (byte)0xa1, (byte)0x51, (byte)0x72, (byte)0x67, (byte)0x6e, //secondary header
            (byte)0x92, (byte)0xec, (byte)0x22, (byte)0x3f, //BIT 0 timestamp seconds
            (byte)0xc3, (byte)0x9e, //BIT 0 timestamp subseconds
            (byte)0x86, (byte)0xf3, //spare (15b), SCLK/MET missed flag (1b)
            (byte)0x36, //reserved (2b), frame VCID (6b)
            (byte)0xb7, (byte)0xcb, (byte)0x68, //frame VCFC
            (byte)0xff, //encoding mode
            (byte) 0x00, (byte) 0x01, (byte) 0x7e, //artificially shortened downlink rate field
            //second packet
            (byte)0x1e, (byte)0xe4, (byte)0x7a, (byte)0xd4, (byte)0x00, (byte)0x16, //CCSDS primary header
            (byte)0x8d, (byte)0x91, (byte)0xe5, (byte)0x2e, (byte)0xfa, (byte)0xff, //secondary header
            (byte)0x71, (byte)0xb3, (byte)0xcd, (byte)0x52, //BIT 0 timestamp seconds
            (byte)0x07, (byte)0x31, //BIT 0 timestamp subseconds
            (byte)0x28, (byte)0xb6, //spare (15b), SCLK/MET missed flag (1b)
            (byte)0x47, //reserved (2b), frame VCID (6b)
            (byte)0x88, (byte)0x5b, (byte)0x69, //frame VCFC
            (byte)0xa7, //encoding mode
            (byte)0x00,  (byte)0x01, (byte)0x7e, (byte)0xd0 //downlink rate
    };
    private static void packet1Tests(Iterator<TimekeepingRecord> packets) {
        assertEquals(packets.hasNext(), true);
        TimekeepingRecord record = packets.next();
        assertEquals(0x92ec223f, record.getSclkCoarse(), "SclkCoarse failed");
        assertEquals(0xc39e86, record.getSclkFine(), "SclkFine failed");
        assertEquals(true, record.getInvalidFlag(), "InvalidFlag failed");
        assertEquals(0x36, record.getTargetFrameVcid(), "TargetFrameVCID failed");
        assertEquals(0xb7cb68, record.getTargetFrameVcfc(), "TargetFrameVCFC failed");
        assertEquals(0xff, record.getEncodingMethod(), "EncodingMethod failed");
        assertEquals(100000, record.getDownlinkDataRate().floatValue(), "DownlinkDataRate failed");
        assertEquals(packets.hasNext(), false);
        assertThrows(NoSuchElementException.class, () -> { packets.next(); });
    }

    private static void packet2Tests(Iterator<TimekeepingRecord> packets) {
        assertEquals(packets.hasNext(), true);
        TimekeepingRecord record = packets.next();
        assertEquals(0x92ec223f, record.getSclkCoarse(), "SclkCoarse failed");
        assertEquals(0xc39e86, record.getSclkFine(), "SclkFine failed");
        assertEquals(true, record.getInvalidFlag(), "InvalidFlag failed");
        assertEquals(0x36, record.getTargetFrameVcid(), "TargetFrameVCID failed");
        assertEquals(0xb7cb68, record.getTargetFrameVcfc(), "TargetFrameVCFC failed");
        assertEquals(0xff, record.getEncodingMethod(), "EncodingMethod failed");
        assertEquals(119000.5, record.getDownlinkDataRate().floatValue(), "DownlinkDataRate failed");
        assertEquals(packets.hasNext(), true);
        record = packets.next();
        assertEquals(0x71b3cd52, record.getSclkCoarse(), "SclkCoarse failed");
        assertEquals(0x073128, record.getSclkFine(), "SclkFine failed");
        assertEquals(false, record.getInvalidFlag(), "InvalidFlag failed");
        assertEquals(0x47, record.getTargetFrameVcid(), "TargetFrameVCID failed");
        assertEquals(0x885b69, record.getTargetFrameVcfc(), "TargetFrameVCFC failed");
        assertEquals(0xa7, record.getEncodingMethod(), "EncodingMethod failed");
        assertEquals(119000.5, record.getDownlinkDataRate().floatValue(), "DownlinkDataRate failed"); // assert equals to 0x8013501b
        assertEquals(packets.hasNext(), false);
        assertThrows(NoSuchElementException.class, () -> { packets.next(); });
    }

    private static void packet3Tests(Iterator<TimekeepingRecord> packets) {
        assertEquals(packets.hasNext(), true);
        assertThrows(UnsupportedOperationException.class, () -> { packets.next(); });
        assertEquals(packets.hasNext(), true);
        TimekeepingRecord record = packets.next();
        assertEquals(0x71b3cd52, record.getSclkCoarse(), "SclkCoarse failed");
        assertEquals(0x073128, record.getSclkFine(), "SclkFine failed");
        assertEquals(false, record.getInvalidFlag(), "InvalidFlag failed");
        assertEquals(0x47, record.getTargetFrameVcid(), "TargetFrameVCID failed");
        assertEquals(0x885b69, record.getTargetFrameVcfc(), "TargetFrameVCFC failed");
        assertEquals(0xa7, record.getEncodingMethod(), "EncodingMethod failed");
        assertEquals(192307.6875, record.getDownlinkDataRate().floatValue(), "DownlinkDataRate failed");
        assertEquals(packets.hasNext(), false);
        assertThrows(NoSuchElementException.class, () -> { packets.next(); });
    }

    private static void packet6Tests(Iterator<TimekeepingRecord> packets) {
        assertEquals(packets.hasNext(), true);
        assertThrows(UnsupportedOperationException.class, packets::next);
        assertEquals(packets.hasNext(), true);
        TimekeepingRecord record = packets.next();
        assertEquals(0x71b3cd52, record.getSclkCoarse(), "SclkCoarse failed");
        assertEquals(0x073128, record.getSclkFine(), "SclkFine failed");
        assertEquals(false, record.getInvalidFlag(), "InvalidFlag failed");
        assertEquals(0x47, record.getTargetFrameVcid(), "TargetFrameVCID failed");
        assertEquals(0x885b69, record.getTargetFrameVcfc(), "TargetFrameVCFC failed");
        assertEquals(0xa7, record.getEncodingMethod(), "EncodingMethod failed");
        assertEquals(98000, record.getDownlinkDataRate().doubleValue(), "DownlinkDataRate failed");
        assertEquals(packets.hasNext(), false);
        assertThrows(NoSuchElementException.class, packets::next);
    }

    @BeforeAll
    public static void instantiateParser()
            throws JAXBException, SAXException, URISyntaxException, IOException {
        nullParser = new TimekeepingPacketParser(TimekeepingPacketTests.class.getResource("/TkPacketTests/PacketDefs/generic_tk_pkt_uint_downlink.xml").toURI(), (byte[])null);
        emptyParser = new TimekeepingPacketParser(TimekeepingPacketTests.class.getResource("/TkPacketTests/PacketDefs/generic_tk_pkt_uint_downlink.xml").toURI(), new byte[] {});
        arrayParser = new TimekeepingPacketParser(TimekeepingPacketTests.class.getResource("/TkPacketTests/PacketDefs/generic_tk_pkt_uint_downlink.xml").toURI(), packet1);
        fileParser = new TimekeepingPacketParser(
                TimekeepingPacketTests.class.getResource("/TkPacketTests/PacketDefs/generic_tk_pkt_uint_downlink.xml").toURI(),
                TimekeepingPacketTests.class.getResource("/TkPacketTests/tkpacket_uintDownlink.dat").toURI());
        multipacketArrayParser = new TimekeepingPacketParser(TimekeepingPacketTests.class.getResource("/TkPacketTests/PacketDefs/generic_tk_pkt_float_downlink.xml").toURI(), packet2);
        multipacketArrayWithBadLengthParser = new TimekeepingPacketParser(TimekeepingPacketTests.class.getResource("/TkPacketTests/PacketDefs/generic_tk_pkt_float_downlink.xml").toURI(), packet3);
        multipacketFileParser = new TimekeepingPacketParser(
                TimekeepingPacketTests.class.getResource("/TkPacketTests/PacketDefs/generic_tk_pkt_float_downlink.xml").toURI(),
                TimekeepingPacketTests.class.getResource("/TkPacketTests/tkpacket_2_floatDownlinks.dat").toURI());
        floatDownlinkPacketParser = new TimekeepingPacketParser(
                TimekeepingPacketTests.class.getResource("/TkPacketTests/PacketDefs/generic_tk_pkt_float_downlink.xml").toURI(),
                TimekeepingPacketTests.class.getResource("/TkPacketTests/tkpacket_floatDownlink.dat").toURI());
        doubleDownlinkPacketParser = new TimekeepingPacketParser(
                TimekeepingPacketTests.class.getResource("/TkPacketTests/PacketDefs/generic_tk_pkt_double_downlink.xml").toURI(), packet4);
    }


    /**
     * Verifies that the TimekeepingPacketParser throws if the user initializes
     * it with null and then attempts to call it.
     */
    @Test
    public void parseNull() {
        assertThrows(IllegalStateException.class, () -> nullParser.iterator());
        assertThrows(IllegalStateException.class, () -> nullParser.parsePackets((byte[])null));
    }

    /**
     * Verifies that the TimekeepingPacketParser handles an empty array.
     */
    @Test
    public void parseEmptyArray() {
        Iterator<TimekeepingRecord> packets = emptyParser.iterator();
        assertEquals(false, packets.hasNext());
        assertThrows(NoSuchElementException.class, () -> { packets.next(); });
        Iterator<TimekeepingRecord> packetsAgain = emptyParser.parsePackets(new byte[] {});
        assertEquals(false, packetsAgain.hasNext());
        assertThrows(NoSuchElementException.class, () -> { packetsAgain.next(); });
    }

    /**
     * Verifies that the TimekeepingPacketParser handles a packet in a byte
     * array.
     */
    @Test
    public void parseArray() throws IOException {
        assert(arrayParser != null);
        Iterator<TimekeepingRecord> packets = arrayParser.iterator();
        packet1Tests(packets);
        packets = arrayParser.parsePackets(packet1);
        packet1Tests(packets);
    }

    /**
     * Verifies that the TimekeepingPacketParser handles a packet in a file.
     *
     * @throws IOException
     */
    @Test
    public void parseFile() throws URISyntaxException, IOException {
        assert(arrayParser != null);
        Iterator<TimekeepingRecord> packets = fileParser.iterator();
        packet1Tests(packets);
        packets = fileParser.parsePackets(TimekeepingPacketTests.class.getResource("/TkPacketTests/tkpacket_uintDownlink.dat").toURI());
        packet1Tests(packets);
    }

    /**
     * Verifies that the TimekeepingPacketParser handles multiple packets in a
     * byte array.
     */
    @Test
    public void parseMultipacketArray() throws IOException {
        assert(arrayParser != null);
        Iterator<TimekeepingRecord> packets = multipacketArrayParser.iterator();
        packet2Tests(packets);
        packets = multipacketArrayParser.parsePackets(packet2);
        packet2Tests(packets);
    }

    /**
     * Verifies that the TimekeepingPacketIterator handles a packet with
     * insufficient length in a multipacket byte array.
     */
    @Test
    public void parseMultipacketArrayWithBadLength() {
        assert(multipacketArrayWithBadLengthParser != null);
        Iterator<TimekeepingRecord> packets = multipacketArrayWithBadLengthParser.iterator();
        packet3Tests(packets);
    }

    /**
     * Verifies that the TimekeepingPacketParser handles multiple packets in a file.
     *
     * @throws IOException
     */
    @Test
    public void parseMultipacketFile() throws URISyntaxException, IOException {
        assert(arrayParser != null);
        Iterator<TimekeepingRecord> packets = multipacketFileParser.iterator();
        packet2Tests(packets);
        packets = multipacketFileParser.parsePackets(TimekeepingPacketTests.class.getResource("/TkPacketTests/tkpacket_2_floatDownlinks.dat").toURI());
        packet2Tests(packets);
    }

    /**
     * Validates that TimekeepingPacketIterator correctly returns TimekeepingRecords with downlinkRate floats when such values
     * are declared in the packet definition
     */
    @Test
    public void testHandlingDownlinkBpsAsFloat() {
        Iterator<TimekeepingRecord> packets = floatDownlinkPacketParser.iterator();
        TimekeepingRecord record = packets.next();
        assertEquals(119000.5, record.getDownlinkDataRate().floatValue());
    }

    /**
     * Validates that TimekeepingPacketIterator correctly returns TimekeepingRecords with downlinkRate doubles when such values
     * are declared in the packet definition
     */
    @Test
    public void testHandlingDownlinkBpsAsDouble() {
        Iterator<TimekeepingRecord> packets = doubleDownlinkPacketParser.iterator();
        TimekeepingRecord record = packets.next();
        assertEquals(119047.6172, record.getDownlinkDataRate().doubleValue());
    }

    /**
     * Ensures that an exception is thrown when the packet definition file contains lengths that exceed 32 bits for
     * anything but downlinkDataRate as a double
     * @throws URISyntaxException
     * @throws MalformedURLException
     * @throws JAXBException
     * @throws SAXException
     */
    @Test
    public void testHandlingExcessivelyLongField() throws URISyntaxException, MalformedURLException, JAXBException, SAXException {
        AtomicReference<TimekeepingPacketParser> parser = null;
        assertThrows(IllegalStateException.class, () -> { parser.set(new TimekeepingPacketParser(
                TimekeepingPacketTests.class.getResource("/TkPacketTests/PacketDefs/generic_tk_pkt_incorrect_downlink_size.xml").toURI(), packet4)); });

    }

    @Test
    public void parseMultipacketArrayWithUints() throws URISyntaxException, MalformedURLException, JAXBException, SAXException {
        TimekeepingPacketParser parser = new TimekeepingPacketParser(
                TimekeepingPacketTests.class.getResource("/TkPacketTests/PacketDefs/generic_tk_pkt_uint_downlink.xml").toURI(), packet5);
        Iterator<TimekeepingRecord> packets = parser.iterator();
        packet6Tests(packets);
    }
}