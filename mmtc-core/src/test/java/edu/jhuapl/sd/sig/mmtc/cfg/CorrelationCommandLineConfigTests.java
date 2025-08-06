package edu.jhuapl.sd.sig.mmtc.cfg;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationCommandLineConfigTests {
    private CorrelationCommandLineConfig config;

    @Test
    @DisplayName("CommandLineConfig.formDateTime Test 1")
    void testformDateTime1() {
        // args is needed for the constructor, but not used in this test.
        String args[] = new String[2];
        args[0] = "2019-183T02:14:00.0";
        args[1] = "2019-183T04:016:00.0";
        CorrelationCommandLineConfig config = new CorrelationCommandLineConfig(args);

        String datetimeStr1 = "2019-183T02:14:00.0";
        OffsetDateTime dateTime1 = config.formDateTime(datetimeStr1);
        assertEquals("2019-07-02T02:14Z", dateTime1.toString());

        String datetimeStr2 = "2019-183T02:14:00.123456";
        OffsetDateTime dateTime2 = config.formDateTime(datetimeStr2);
        assertEquals("2019-07-02T02:14:00.123456Z", dateTime2.toString());

        String datetimeStr3 = "2019-183T02:14:00";
        OffsetDateTime dateTime3 = config.formDateTime(datetimeStr3);
        assertEquals("2019-07-02T02:14Z", dateTime3.toString());

        String datetimeStr4 = "2019-183T02:14:00.123Z";
        OffsetDateTime dateTime4 = config.formDateTime(datetimeStr4);
        assertEquals("2019-07-02T02:14:00.123Z", dateTime4.toString());

        String datetimeStr5 = "2019-183T02:14:00.123";
        OffsetDateTime dateTime5 = config.formDateTime(datetimeStr5);
        assertEquals("2019-07-02T02:14:00.123Z", dateTime5.toString());

        String datetimeStr5a = "2019-183T02:14:00.123456789";
        OffsetDateTime dateTime5a = config.formDateTime(datetimeStr5a);
        assertEquals("2019-07-02T02:14:00.123456Z", dateTime5a.toString());

        String datetimeStr6 = "2019-07-02T02:14:00.123456";
        OffsetDateTime dateTime6 = config.formDateTime(datetimeStr6);
        assertEquals("2019-07-02T02:14:00.123456Z", dateTime6.toString());

        String datetimeStr7 = "2019-07-02T02:14:00";
        OffsetDateTime dateTime7 = config.formDateTime(datetimeStr7);
        assertEquals("2019-07-02T02:14Z", dateTime7.toString());

        String datetimeStr8 = "2019-07-02T02:14:00.1";
        OffsetDateTime dateTime8 = config.formDateTime(datetimeStr8);
        assertEquals("2019-07-02T02:14:00.100Z", dateTime8.toString());

        String datetimeStr9 = "2019-07-02T02:14:00.123Z";
        OffsetDateTime dateTime9 = config.formDateTime(datetimeStr9);
        assertEquals(datetimeStr9, dateTime9.toString());

        boolean DateTimeParseExceptionHandled1 = false;
        try {
            String datetimeStr10 = "2019-07--02T02:14:00.123Z";
            OffsetDateTime dateTime10 = config.formDateTime(datetimeStr10);
        } catch (DateTimeParseException ex) {
            DateTimeParseExceptionHandled1 = true;
        }
        assertTrue(DateTimeParseExceptionHandled1);

        boolean DateTimeParseExceptionHandled2 = false;
        try {
            String datetimeStr10 = "2019-13-02T02:14:00.123Z";
            OffsetDateTime dateTime10 = config.formDateTime(datetimeStr10);
        } catch (DateTimeParseException ex) {
            DateTimeParseExceptionHandled2 = true;
        }
        assertTrue(DateTimeParseExceptionHandled2);
    }
}
