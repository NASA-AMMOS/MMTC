package edu.jhuapl.sd.sig.mmtc.tlm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.xml.bind.JAXBException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.xml.sax.SAXException;

/**
 * These tests verify that the TimekeepingPacketParser works correctly when it
 * is instantiated without input data and is only later initialized with input.
 *
 * All nontrivial test cases verify that packets are parsed correctly, then
 * reinitialize the parser with new data and verify that those packets are
 * parsed correctly as well.
 */
@TestMethodOrder(OrderAnnotation.class)
public class TimekeepingPacketPostInitializeTests {
    private static TimekeepingPacketParser parser;
    private static byte[] packet1 = new byte[] { (byte) 0xcf, (byte) 0x4, (byte) 0x6d, (byte) 0x14, (byte) 0x00,
            (byte) 0x16, // CCSDS primary header
            (byte) 0x98, (byte) 0xa1, (byte) 0x51, (byte) 0x72, (byte) 0x67, (byte) 0x6e, // secondary header
            (byte) 0x92, (byte) 0xec, (byte) 0x22, (byte) 0x3f, // BIT 0 timestamp seconds
            (byte) 0xc3, (byte) 0x9e, // BIT 0 timestamp subseconds
            (byte) 0x86, (byte) 0xf3, // spare (15b), SCLK/MET missed flag (1b)
            (byte) 0x36, // reserved (2b), frame VCID (6b)
            (byte) 0xb7, (byte) 0xcb,  // frame VCFC
            (byte) 0x68, (byte) 0xff, // encoding mode
            (byte) 0x47, (byte) 0xe8, (byte) 0x6c, (byte) 0x40 // downlink rate
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

    private static void packet1Tests(Iterator<TimekeepingRecord> packets) {
        assertEquals(packets.hasNext(), true);
        TimekeepingRecord record = packets.next();
        assertEquals(0x92ec223f, record.getSclkCoarse(), "SclkCoarse failed");
        assertEquals(0xc39e86, record.getSclkFine(), "SclkFine failed");
        assertEquals(true, record.getInvalidFlag(), "InvalidFlag failed");
        assertEquals(0x36, record.getTargetFrameVcid(), "TargetFrameVCID failed");
        assertEquals(0xb7cb68, record.getTargetFrameVcfc(), "TargetFrameVCFC failed");
        assertEquals(0xff, record.getEncodingMethod(), "EncodingMethod failed");
        assertEquals(119000.5, record.getDownlinkDataRate().floatValue(), "DownlinkDataRate failed");
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

    @BeforeAll
    public static void instantiateParser()
            throws MalformedURLException, JAXBException, SAXException, URISyntaxException {
        //NOTE TimekeepingPacketParser is supposed to be reusable.
        //We test that by instantiating it only once and sharing it among tests.
        parser = new TimekeepingPacketParser(TimekeepingPacketPostInitializeTests.class.getResource("/TkPacketTests/PacketDefs/generic_tk_pkt_float_downlink.xml").toURI());
    }

    /**
     * Verifies that the TimekeepingPacketParser throws if the user attempts to
     * call it without initializing input data.
     */
    @Test
    @Order(1)
    public void parseUninitialized() {
        assert(parser != null);
        assertThrows(IllegalStateException.class, () -> parser.iterator());
    }

    /**
     * Verifies that the TimekeepingPacketParser throws if the user initializes
     * it with null and then attempts to call it.
     */
    @Test
    public void parseNull() {
        assert(parser != null);
        assertThrows(IllegalStateException.class, () -> parser.parsePackets((byte[])null));
        //and parser should be able to reinitialize without breaking
        assertThrows(IllegalStateException.class, () -> parser.parsePackets((byte[])null));
    }

    /**
     * Verifies that the TimekeepingPacketParser handles an empty array.
     */
    @Test
    public void parseEmptyArray() {
        assert(parser != null);
        Iterator<TimekeepingRecord> packets = parser.parsePackets(new byte[] {});
        assertEquals(false, packets.hasNext());
        assertThrows(NoSuchElementException.class, () -> { packets.next(); });
        Iterator<TimekeepingRecord> packetsAgain = parser.parsePackets(new byte[] {});
        assertEquals(false, packetsAgain.hasNext());
        assertThrows(NoSuchElementException.class, () -> { packetsAgain.next(); });
    }

    /**
     * Verifies that the TimekeepingPacketParser handles a packet in a byte
     * array.
     */
    @Test
    public void parseArray() {
        assert(parser != null);
        Iterator<TimekeepingRecord> packets = parser.parsePackets(packet1);
        packet1Tests(packets);
        packets = parser.parsePackets(packet1);
        packet1Tests(packets);
    }

    /**
     * Verifies that the TimekeepingPacketParser handles a packet in a file.
     */
    @Test
    public void parseFile() throws URISyntaxException, IOException {
        assert(parser != null);
        Iterator<TimekeepingRecord> packets = parser.parsePackets(getClass().getResource("/TkPacketTests/tkpacket_floatDownlink.dat").toURI());
        packet1Tests(packets);
        packets = parser.parsePackets(getClass().getResource("/TkPacketTests/tkpacket_floatDownlink.dat").toURI());
        packet1Tests(packets);
    }

    /**
     * Verifies that the TimekeepingPacketParser handles multiple packets in a
     * byte array.
     */
    @Test
    public void parseMultipacketArray() {
        assert(parser != null);
        Iterator<TimekeepingRecord> packets = parser.parsePackets(packet2);
        packet2Tests(packets);
        packets = parser.parsePackets(packet2);
        packet2Tests(packets);
    }

    /**
     * Verifies that the TimekeepingPacketParser handles multiple packets in a
     * file.
     */
    @Test
    public void parseMultipacketFile() throws URISyntaxException, IOException {
        assert(parser != null);
        Iterator<TimekeepingRecord> packets = parser.parsePackets(getClass().getResource("/TkPacketTests/tkpacket_2_floatDownlinks.dat").toURI());
        packet2Tests(packets);
        packets = parser.parsePackets(getClass().getResource("/TkPacketTests/tkpacket_2_floatDownlinks.dat").toURI());
        packet2Tests(packets);
    }
}