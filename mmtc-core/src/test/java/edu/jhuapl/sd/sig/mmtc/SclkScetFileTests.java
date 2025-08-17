package edu.jhuapl.sd.sig.mmtc;

import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkScet;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkScetFile;
import edu.jhuapl.sd.sig.mmtc.products.model.TextProductException;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import spice.basic.KernelDatabase;
import spice.basic.SpiceErrorException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

public class SclkScetFileTests {
    @BeforeAll
    static void teardown() throws TimeConvertException {
        TestHelper.ensureSpiceIsLoadedAndUnloadAllKernels();
    }

    /**
     * Create a new SCLK/SCET file from an SCLK kernel. Uses 10-Jun-2019 kernel.
     * Using NH data.
     */
    @Test
    public void createNewSclkKScetFile_Test3() throws SpiceErrorException, TextProductException, TimeConvertException, IOException {
        System.loadLibrary("JNISpice");

        String sclkKernel = "src/test/resources/nh_kernels/sclk/new-horizons_1876.tsc";
        KernelDatabase.load("src/test/resources/nh_kernels/lsk/naif0012.tls");
        KernelDatabase.load(sclkKernel);

        String dir         = "src/test/resources/SclkScetTests";
        String filename    = "new-horizons_1876.scet";
        String newFilePath = dir + File.separator + filename;
        Path newFile       = Paths.get(newFilePath);

        Files.deleteIfExists(newFile);

        TimeCorrelationAppConfig config = Mockito.mock(TimeCorrelationAppConfig.class);
        when(config.getSclkScetOutputDir()).thenReturn(Paths.get(dir));
        when(config.getMissionName()).thenReturn("NEW_HORIZONS");
        when(config.getMissionId()).thenReturn(98);
        when(config.getSpacecraftName()).thenReturn("NEW_HORIZONS");
        when(config.getSpacecraftId()).thenReturn(98);
        when(config.getDataSetId()).thenReturn("SCLK_SCET");
        when(config.getProducerId()).thenReturn("MMTC");
        when(config.getSclkScetApplicableDurationDays()).thenReturn(0);
        when(config.getNaifSpacecraftId()).thenReturn(-98);
        when(config.getSclkScetLeapSecondRateMode()).thenReturn(TimeCorrelationAppConfig.SclkScetFileLeapSecondSclkRate.ONE);

        SclkScetFile scetfile = new SclkScetFile(config, filename, "11");

        SclkScet.setScetStrSecondsPrecision(3);
        scetfile.setProductCreationTime(OffsetDateTime.now(ZoneOffset.UTC));
        scetfile.setClockTickRate(50000);
        // scetfile.setEndSclkScetTime(TimeConvert.parseIsoDoyUtcStr(scetfile.getProductDateTimeIsoUtc()));
        scetfile.createNewSclkScetFile(sclkKernel);
    }

    @Test
    public void testIsNegativeLeapSecond() {
        SclkScet a = new SclkScet(
                "000345534716.0",
                TimeConvert.parseIsoDoyUtcStr("2016-366T23:59:58.419640"),
                 68.184,
                2.0000000000
        );

        SclkScet b = new SclkScet(
                "000345534717.0",
                TimeConvert.parseIsoDoyUtcStr("2017-001T00:00:00.419640"),
                67.184,
                1.0000000000
        );

        Assertions.assertTrue(SclkScetFile.isNegativeLeapSecondEntryPair(a, b));
    }
}
