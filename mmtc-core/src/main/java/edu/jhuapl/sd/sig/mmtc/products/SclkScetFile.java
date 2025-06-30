package edu.jhuapl.sd.sig.mmtc.products;

import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * <P>The SclkScet class creates the SCLK/SCET file which is a primary time correlation product used
 * on many missions.
 * <P>See <I>Generic SCLK versus SCET Correlation File, SIS (MGSS DOC-000574, Rev. E)</I> for a full
 * description of this product.
 */
public class SclkScetFile extends TextProduct {

    /**
     * The time in UTC that the product was created.
     */
    String productCreationTime;

    /**
     * The top header record of an SCLK/SCET file marks the beginning of the header information.
     */
    private static final String SCLKSCET_HDR_TOP = "CCSD3ZS00001$$sclk$$NJPL3KS0L015$$scet$$";

    /**
     * The bottom top header record of an SCLK/SCET file marks the end of the header information.
     */
    private static final String SCLKSCET_HDR_END = "CCSD3RE00000$$scet$$NJPL3IS00613$$data$$";

    /**
     * The footer record of the SCLK/SCET file that indicates the end of the data.
     */
    private static final String SCLKSCET_FTR     = "CCSD3RE00000$$data$$CCSD3RE00000$$sclk$$";

    /**
     * The fields header label record of the SCLK/SCET file.
     */
    private static final String SCLKSCET_FLDS    = "*____SCLK0_______  ____SCET0____________ _DUT__ _SCLKRATE___";

    /* The number of ticks per second in the spacecraft clock (SCLK) fine time component. */
    private int clockTickRate = 0;

    /* The zero-based index of the last data record in the SCLK kernel (i.e., the last record containing a triplet. */
    private int endDataNum = -1;

    /* Specifies the SCET of the last data record in the new SCLK/SCET file. */
    private OffsetDateTime endSclkScetTime;

    private final TimeCorrelationAppConfig.SclkScetFileLeapSecondSclkRate leapSecondSclkRateMode;

    /**
     * Metadata parameters for the SCLK/SCET file header if not read-in from an existing SCLK/SCET file.
     */
    private String  missionName;
    private Integer missionId;
    private String  spacecraftName;
    private Integer spacecraftId;
    private String  dataSetId;
    private String  producerId;
    private String  productVersionId;
    private int applicableDurationDays;

    /* The NAIF spacecraft ID (usually the negative of the spacecraftID. */
    private final Integer naifScId;

    /**
     * Class constructor. This constructor is to be used when the SCLK/SCET file is to be created
     * from an SCLK kernel file, when the user wishes to override existing header parameters from
     * a source SCLK/SCET file, or when a brand new SCLK/SCET file is created without a source
     * product file.
     *
     * @param config             IN the TimeCorrelationAppConfig instance for this run
     * @param filename           IN the new SCLK/SCET filename
     * @param product_version_id IN the version of this product
     */
    public SclkScetFile(TimeCorrelationAppConfig config, String filename, String product_version_id) {
        super();
        setDir(config.getSclkScetOutputDir().toString());
        setName(filename);
        setHeaderData(
                config.getMissionName(),
                config.getMissionId(),
                config.getSpacecraftName(),
                config.getSpacecraftId(),
                config.getDataSetId(),
                config.getProducerId(),
                product_version_id,
                config.getSclkScetApplicableDurationDays()
        );

        this.naifScId = config.getNaifSpacecraftId();
        this.leapSecondSclkRateMode = config.getSclkScetLeapSecondRateMode();
    }

    /**
     * Sets the spacecraft clock (SCLK) tick rate. This is the number of ticks per second in
     * the SCLK fine time component. This information can be obtained from the SCLK kernel.
     *
     * @param clockTickRate IN the clock tick rate
     */
    public void setClockTickRate(Integer clockTickRate) {
        this.clockTickRate = clockTickRate;
    }

    /**
     * Sets the fields of the SCLK/SCET header.
     *
     * @param mission_name       IN the mission name
     * @param mission_id         IN the numeric mission ID
     * @param spacecraft_name    IN the spacecraft name
     * @param spacecraft_id      IN the numeric spacecraft ID
     * @param data_set_id        IN the data set ID
     * @param producer_id        IN the producer ID
     * @param product_version_id IN the version of this product
     */
    private void setHeaderData(String mission_name, int mission_id, String spacecraft_name, int spacecraft_id,
                              String data_set_id, String producer_id, String product_version_id, int applicableDurationDays) {

        this.missionId        = mission_id;
        this.missionName      = mission_name;
        this.spacecraftName   = spacecraft_name;
        this.spacecraftId     = spacecraft_id;
        this.dataSetId        = data_set_id;
        this.producerId       = producer_id;
        this.productVersionId = product_version_id;
        this.applicableDurationDays = applicableDurationDays;
    }

    /**
     * Overrides the default last data record time which is the SCET of the last added data record.
     * This is used to create SCLK/SCET files with leap seconds records going beyond the time of
     * the last added regular data record.
     * @param datetime IN the time to compute SCLK/SCET records to
     */
    public void setEndSclkScetTime(OffsetDateTime datetime) {
        endSclkScetTime = datetime;
    }

     /**
     * Creates the new SCLK kernel product. Implements the corresponding abstract method in the parent class.
     *
     * @throws TextProductException if the product cannot be created
     */
    public void createNewProduct() throws TextProductException, TimeConvertException {

        productCreationTime = getProductDateTimeIsoUtc();

        if (!sourceProductReadIn) {
            throw new TextProductException("Source product not read-in.");
        }

        /* Find the last data (quadruplet) record in the SCLK/SCET data. A data record should contain
         * contain 4 fields and the second (index 1) field should contain a "T" character as part
         * of the ISO time string format.
         */
        endDataNum = lastDataRecNum(sourceProductLines);

        if (endDataNum < 3) {
            throw new TextProductException("Cannot find last data record. Invalid SCLK/SCET data loaded.");
        }

        if (sourceProductLines.size() < 1) {
            throw new TextProductException("Valid source SCLK/SCET file data have not been loaded.");
        }

        /*
         * Create the new data product either from an SCLK kernel or from an existing SCLK/SCET file.
         */
        logger.debug("Creating SCLK/SCET file from: " + sourceFilespec);

        /* ******************************************************
         * Create the new product from an SCLK kernel.
         */
        List<SclkScet> sclkScetRecs = convertSclkKernelDataToScetData();

        /* Get the data start time and create the file header block. */
        OffsetDateTime startTime  = sclkScetRecs.get(0).getScet();
        if (endSclkScetTime == null) {
            endSclkScetTime = sclkScetRecs.get(sclkScetRecs.size() - 1).getScet();
        }
        List<String> hdr  = createSclkScetHeader(TimeConvert.timeToIsoUtcString(startTime, SclkScet.getScetStrSecondsPrecision()), TimeConvert.timeToIsoUtcString(endSclkScetTime));

        List<SclkScet> leapSecRecs = getLeapSecondSclkScetRecs(startTime);

        /* Add the leap second records to the end of the new SCLK/SCET records. */
        sclkScetRecs.addAll(leapSecRecs);

        /* Sort the SCLK/SCET records by SCET time. */
        sclkScetRecs.sort(Comparator.comparing(SclkScet::getScet));

        switch(leapSecondSclkRateMode) {
            case PRIOR_RATE: {
                // skip the first (epoch) record
                for (int i = 1; i < sclkScetRecs.size() - 1; i++) {
                    final SclkScet first = sclkScetRecs.get(i);
                    final SclkScet second = sclkScetRecs.get(i + 1);

                    if (! isLeapSecondEntryPair(first, second)) {
                        continue;
                    }

                    second.setSclkrate(sclkScetRecs.get(i - 1).getSclkrate());
                }
                break;
            }
            case ONE: break;
            default: throw new IllegalStateException("No such leap second SCLKRATE mode: " + leapSecondSclkRateMode);
        }

        newProductLines = new ArrayList<>(hdr);
        sclkScetRecs.forEach(r -> newProductLines.add(r.toString()));
        newProductLines.add(SCLKSCET_FTR);

    }

    public static boolean isLeapSecondEntryPair(SclkScet a, SclkScet b) {
        return isPositiveLeapSecondEntryPair(a, b) || isNegativeLeapSecondEntryPair(a, b);
    }

    public static boolean isPositiveLeapSecondEntryPair(SclkScet a, SclkScet b) {
        if (Double.parseDouble(b.getSclk()) - Double.parseDouble(a.getSclk()) != 1.0) {
            return false;
        }

        // SCET values should be within a second of each other, as the SCET is nearly held still (it's incremented by 1ms) while the SCLK advances a whole tick/second
        if (! (Duration.between(a.getScet(), b.getScet()).getSeconds() < 1 )) {
            return false;
        }

        if (b.getDut() - a.getDut() != 1.0) {
            return false;
        }

        if (a.getSclkrate() != 0.0010000000) {
            return false;
        }

        return true;
    }

    public static boolean isNegativeLeapSecondEntryPair(SclkScet a, SclkScet b) {
        if (Double.parseDouble(b.getSclk()) - Double.parseDouble(a.getSclk()) != 1.0) {
            return false;
        }

        // SCET values should be exactly two seconds apart
         if (Duration.between(a.getScet(), b.getScet()).getSeconds() != 2.0 ) {
            return false;
        }

        if (b.getDut() - a.getDut() != -1.0) {
            return false;
        }

        if (a.getSclkrate() != 2.0) {
            return false;
        }

        return true;
    }

    /**
     * Creates an SCLK/SCET file header block with required metadata and adds each record to the
     * new Product.
     *
     * @param firstCorrelationTimeUtc IN the first correlation record as a UTC ISO string
     * @param lastCorrelationTimeUtc  IN the data end time SCET as a UTC ISO string
     * @return a full header block
     */
    private List<String> createSclkScetHeader(String firstCorrelationTimeUtc, String lastCorrelationTimeUtc) {

        List<String> hdr = new ArrayList<>();

        final String applicableStopTimeUtc = TimeConvert.timeToIsoUtcString(
                TimeConvert.parseIsoDoyUtcStr(lastCorrelationTimeUtc).plus(applicableDurationDays, ChronoUnit.DAYS),
                SclkScet.getScetStrSecondsPrecision()
        );

        hdr.add(SCLKSCET_HDR_TOP);
        hdr.add("MISSION_NAME="          + missionName + ";");
        hdr.add("SPACECRAFT_NAME="       + spacecraftName + ";");
        hdr.add("DATA_SET_ID="           + dataSetId + ";");
        hdr.add("FILE_NAME="             + getName() + ";");
        hdr.add("PRODUCT_CREATION_TIME=" + productCreationTime + ";");
        hdr.add("PRODUCT_VERSION_ID="    + productVersionId + ";");
        hdr.add("PRODUCER_ID="           + producerId + ";");
        hdr.add("APPLICABLE_START_TIME=" + firstCorrelationTimeUtc + ";");
        hdr.add("APPLICABLE_STOP_TIME="  + applicableStopTimeUtc + ";");
        hdr.add("MISSION_ID="            + missionId.toString() + ";");
        hdr.add("SPACECRAFT_ID="         + spacecraftId.toString() + ";");
        hdr.add(SCLKSCET_HDR_END);
        hdr.add(SCLKSCET_FLDS);

        return hdr;
    }


    /**
     * Reads the input source SCLK Kernel data and converts the encSCLK to raw SCLK, the TDT
     * to UTC, computes the DUT, and adds the clock change rate. It also inserts leap seconds
     * records throughout the new prodect SCLK/SCET data as applicable.
     *
     * @return the entirety of the source SCLK kernel data converted to SCLK/SCET records
     * @throws TimeConvertException if a computation could not be performed
     */
    private List<SclkScet> convertSclkKernelDataToScetData() throws TimeConvertException {

        List<SclkScet> scetData = new ArrayList<>();

        Double sclkTicks;
        OffsetDateTime scetUtc;
        Double dutval;
        Double sclkrate;

        /* Extract the individual encSclk, TDT str, and change rate fields from the
         * SCLK kernel triplet. Convert to the corresponding SCET file fields.
         */
        for (int i = 0; i< sourceProductLines.size(); i++) {
            String sclkKernelRecord = sourceProductLines.get(i).trim();
            logger.trace("SclkScetFile.List(): sclkKernelRecord = " + sclkKernelRecord);

            if (isDataRecord(sclkKernelRecord)) {
                String[] fields = sclkKernelRecord.split("\\s+");

                /* Convert the encoded SCLK back to regular SCLK ticks. */
                Double encSclk = new Double(fields[0]);
                sclkTicks = TimeConvert.encSclkToSclk(naifScId, clockTickRate, encSclk);
                logger.trace("SclkScetFile.List(): str encSclk = " + fields[0] + ", Double encSclk = " + encSclk);

                /* Convert the TDT string to a UTC string. Remove the leading "@"
                 * character if it is there.
                 */
                String tdtStr;
                if (fields[1].startsWith("@")) {
                    tdtStr = fields[1].substring(1, fields[1].length());
                }
                else {
                    tdtStr = fields[1];
                }
                scetUtc = TimeConvert.parseIsoDoyUtcStr(TimeConvert.tdtStrToUtc(tdtStr, SclkScet.getScetStrSecondsPrecision()));
                logger.trace("SclkScetFile.List(): tdtStr = " + tdtStr + ", scetUtc = " + scetUtc);

                /* Compute the Delta Universal TIme (DUT) offset of UTC from TDT. */
                dutval = getDutBefore(scetUtc);

                /* The SCLK change rate is the same as the SCLK kernel clock change rate. */
                sclkrate = new Double(fields[2]);


                logger.trace("SclkScetFile.List(): sclkTicks = " + sclkTicks + ", scetUtc = " + scetUtc +
                        ", dutval = " + dutval + ", sclkrate = " + sclkrate);
                SclkScet scetRec = new SclkScet(sclkToSclkStr(sclkTicks), scetUtc, dutval, sclkrate);
                scetData.add(scetRec);
            }
        }

        return scetData;
    }


    /**
     * Computes the Delta Universal Time (DUT) offset of UTC from TDT before the given time
     *
     * @param time IN the UTC time
     * @return the DUT before the given time
     * @throws TimeConvertException if a computation could not be performed
     */
    public static Double getDutBefore(OffsetDateTime time) throws TimeConvertException {
        return TimeConvert.utcTdtOffset() + TimeConvert.getDeltaEtBefore(time);
    }

    /**
     * Computes the Delta Universal Time (DUT) offset of UTC from TDT as of the given time
     *
     * @param time IN the UTC time
     * @return the DUT as of the given time
     * @throws TimeConvertException if a computation could not be performed
     */
    public static Double getDutAsOf(OffsetDateTime time) throws TimeConvertException {
        return TimeConvert.utcTdtOffset() + TimeConvert.getDeltaEtAsOf(time);
    }

    /**
     * Determines if the record is an SCLK/SCET time correlation record containing a quadruplet
     * if an SCLK/SCET file or a triplet if an SCLK kernel, or if its supporting text. Implements
     * the corresponding abstract method in the parent class. An SCLK/SCET time correlation contains
     * four fields (SCLK, SCET, DUT, ClockChgRate) separated by whitespace. The SCET UTC string
     * always contains an ISO "T" character. An SCLK kernel time correlation contains three fields
     * (enc SCLK, TDT, ClockChgRate) separated by whitespace. The TDT string always has "@" as its
     * first character.
     *
     * @param record IN the record to evaluate
     * @return true if the record is a time correlation quadruplet, false otherwise
     */
    public boolean isDataRecord(String record) {
        boolean isdata = false;
        String[] fields = record.trim().split("\\s+");

        if (fields.length == 3) {
            isdata = fields[1].startsWith("@");
        }

        return isdata;
    }


    /**
     * Creates a set of SCLK/SCET records for each leap second added within the time
     * of interest.
     * @param startTime IN the starting of the time period of interest
     * @return the list of SCLK/SCET records for the leap seconds
     * @throws TimeConvertException if the leap second records could not be created
     */
    private List<SclkScet> getLeapSecondSclkScetRecs(OffsetDateTime startTime) throws TimeConvertException {
        final List<SclkScet> resultLeapSecRecs = new ArrayList<>();

        for (TimeConvert.LeapSecond inputLeapSecRec : TimeConvert.parseLeapSeconds()) {
            if (inputLeapSecRec.leapSecOccurrence.isBefore(startTime)) {
                continue;
            }

            final Double dutBeforeLeapSecond = getDutBefore(inputLeapSecRec.leapSecOccurrence);
            final Double dutAsOfLeapSecond = getDutAsOf(inputLeapSecRec.leapSecOccurrence);

            if (dutBeforeLeapSecond.equals(dutAsOfLeapSecond)) {
                throw new IllegalStateException(String.format("No difference in DUT before and as of leap second at %s", inputLeapSecRec.leapSecOccurrence));
            }

            if (dutBeforeLeapSecond < dutAsOfLeapSecond) {
                resultLeapSecRecs.addAll(getPositiveLeapSecRecordPair(inputLeapSecRec));
            } else {
                resultLeapSecRecs.addAll(getNegativeLeapSecRecordPair(inputLeapSecRec));
            }
        }

        return resultLeapSecRecs;
    }

    private List<SclkScet> getPositiveLeapSecRecordPair(TimeConvert.LeapSecond inputLeapSecRec) throws TimeConvertException {
        final List<SclkScet> resultLeapSecRecs = new ArrayList<>();

        /*
           The leap second 'date' is encoded in the leap second kernels as the beginning of the day 'after' which the inserted leap second (61st second, :60) was inserted.
           For example: the leap second that occurred at the end of December 31, 2016 is recorded in naif0012.tls as '@2017-JAN-1'.
           The first record of a pair of records within the SCLK-SCET file that encode a leap second should have an SCLK value that occurs one tick before the new day after the leap second has been applied, e.g. at the beginning of the 61st second of the day with the leap second.
           The second record of a record pair that encodes a leap second should have an SCLK value that relates an SCLK to a SCET, where the SCLK is one second later than it would have been were there not to have been a leap second.

           So, to get the SCLK value that is one second just before the leap second is applied (but after all prior leap seconds have been applied), subtract 1 coarse tick from the result of calling TimeConvert.utcToSclk(... lsItem.date) (i.e. SPICE),
           because it is taking into account the very leap second we're encoding.

           Also: this correlation run may have added a record before a leap second (in the case of a leap second occurring within 6 months of this run),
           or this correlation run may be updating a record before a leap second (when interpolation is on and a leap second record is between the prior interpolated record
           and the new one.) Said another way, it's possible that this new correlation run gives a more accurate estimate of the SCLK at which the leap second
           will occur or has occurred.  This means we must load the just-written SCLK kernel before converting the leap second UTC time to SCLK time.
         */

        // load the new SCLK kernel only for the following conversion
        Map<String, String> newSclkKernel = new HashMap<>();
        newSclkKernel.put(sourceFilespec, "sclk");

        final Double sclkPreLeapSecond;
        try {
            TimeConvert.loadSpiceKernels(newSclkKernel);
            sclkPreLeapSecond = TimeConvert.utcToSclk(naifScId, clockTickRate, TimeConvert.timeToIsoUtcString(inputLeapSecRec.leapSecOccurrence)) - 1.0;
        } finally {
            TimeConvert.unloadSpiceKernels(newSclkKernel);
        }

        final Double dutBeforeLeapSecond = getDutBefore(inputLeapSecRec.leapSecOccurrence);
        final Double dutAsOfLeapSecond = getDutAsOf(inputLeapSecRec.leapSecOccurrence);

        // Because of how AMPCS treats SCLK entries in SCLK-SCET files, it's best to align SCLK values on the coarse tick
        final Double sclkPreLeapSecondForwardAlignedToCoarseTick = Math.ceil(sclkPreLeapSecond);
        final Double fracSecToAddToScetToForwardAlign = sclkPreLeapSecondForwardAlignedToCoarseTick - sclkPreLeapSecond;

        // forward/positive leap second

        // Per the MGSS SCLK vs SCET SIS (DOC-000574, Rev. E) an increment of 0.001 is added to the zero
        // value clock change rate on leap seconds to make the clock change rate non-zero.
        resultLeapSecRecs.add(new SclkScet(
                // the coarse-aligned SCLK 'before' the (forward-adjusted) leap second
                sclkToSclkStr(sclkPreLeapSecondForwardAlignedToCoarseTick),

                // the (forward-adjusted) leap second, the adjustment being the movement forward in time to compensate for the SCLK coarse-tick alignment
                inputLeapSecRec.leapSecOccurrence
                        .plus(Duration.of((int) (fracSecToAddToScetToForwardAlign * TimeConvert.NS_PER_SECOND), ChronoUnit.NANOS)),

                // the DUT 'before' this leap second occurs
                dutBeforeLeapSecond,

                // a small but nonzero change rate
                0.00100000000
        ));

        /* Leap second is active immediately after the time it was added, so add 1 sec. SCLK0 and to DUT. */
        resultLeapSecRecs.add(new SclkScet(
                // the coarse-aligned SCLK 'after' the (adjusted) leap second
                sclkToSclkStr(sclkPreLeapSecondForwardAlignedToCoarseTick + 1.0),

                // the (adjusted) leap second, the adjustment being the movement forward in time to compensate for the SCLK coarse-tick alignment, plus .001 sec to provide a smoothing effect to the otherwise-frozen leap second
                inputLeapSecRec.leapSecOccurrence
                        .plus(Duration.of((int) (fracSecToAddToScetToForwardAlign * TimeConvert.NS_PER_SECOND), ChronoUnit.NANOS))
                        .plus(1, ChronoUnit.MILLIS),

                // the DUT 'after' this leap second occurs
                dutAsOfLeapSecond,

                // an identity sclkrate, which might be adjusted by the caller to the sclkrate from the prior correlation
                1.0
        ));

        return resultLeapSecRecs;
    }

    private List<SclkScet> getNegativeLeapSecRecordPair(TimeConvert.LeapSecond inputLeapSecRec) throws TimeConvertException {
        final List<SclkScet> resultLeapSecRecs = new ArrayList<>();

        // see comments in the above method (getPositiveLeapSecRecordPair) for more context about the calculations below

        // load the new SCLK kernel only for the following conversion
        Map<String, String> newSclkKernel = new HashMap<>();
        newSclkKernel.put(sourceFilespec, "sclk");

        final Double sclkPreLeapSecond;
        try {
            TimeConvert.loadSpiceKernels(newSclkKernel);
            sclkPreLeapSecond = TimeConvert.utcToSclk(naifScId, clockTickRate, TimeConvert.timeToIsoUtcString(inputLeapSecRec.leapSecOccurrence)) - 1.0;
        } finally {
            TimeConvert.unloadSpiceKernels(newSclkKernel);
        }

        final Double dutBeforeLeapSecond = getDutBefore(inputLeapSecRec.leapSecOccurrence);
        final Double dutAsOfLeapSecond = getDutAsOf(inputLeapSecRec.leapSecOccurrence);

        // Because of how AMPCS treats SCLK entries in SCLK-SCET files, it's best to align SCLK values on the coarse tick
        final Double sclkPreLeapSecondForwardAlignedToCoarseTick = Math.ceil(sclkPreLeapSecond);
        final Double fracSecToAddToScetToForwardAlign = sclkPreLeapSecondForwardAlignedToCoarseTick - sclkPreLeapSecond;

        resultLeapSecRecs.add(new SclkScet(
                // the coarse-aligned SCLK 'before' the (forward-adjusted) leap second
                sclkToSclkStr(sclkPreLeapSecondForwardAlignedToCoarseTick),

                // the (forward-adjusted) leap second, the adjustment being the movement forward in time to compensate for the SCLK coarse-tick alignment
                inputLeapSecRec.leapSecOccurrence
                        .plus(Duration.of((int) (fracSecToAddToScetToForwardAlign * TimeConvert.NS_PER_SECOND), ChronoUnit.NANOS))
                        .minus(2, ChronoUnit.SECONDS),

                // the DUT 'before' this leap second occurs
                dutBeforeLeapSecond,

                // the SCLKRATE is seconds per SCLK coarse tick, so for this we'll have two ground seconds for a single SCLK tick
                2.0
        ));

        /* Leap second is active immediately after the time it was added, so add 1 sec. SCLK0 and to DUT. */
        resultLeapSecRecs.add(new SclkScet(
                // the coarse-aligned SCLK 'after' the (adjusted) leap second
                sclkToSclkStr(sclkPreLeapSecondForwardAlignedToCoarseTick + 1.0),

                // the (adjusted) leap second, the adjustment being the movement forward in time to compensate for the SCLK coarse-tick alignment, plus .001 sec to provide a smoothing effect to the otherwise-frozen leap second
                inputLeapSecRec.leapSecOccurrence
                        .plus(Duration.of((int) (fracSecToAddToScetToForwardAlign * TimeConvert.NS_PER_SECOND), ChronoUnit.NANOS)),

                // the DUT 'after' this leap second occurs
                dutAsOfLeapSecond,

                // an identity sclkrate, which might be adjusted by the caller to the sclkrate from the prior correlation
                1.0
        ));

        return resultLeapSecRecs;
    }

    /*
     * Formats a double SCLK to the coarse.fine format the SCLK-SCET file expects
     */
    private String sclkToSclkStr(final Double coarseSclk) {
        // per SIS, the SCLK field should be 16 characters long in total
        // this field is not a decimal representation of SCLK; it is coarse.fine ticks
        // we could figure out how many ticks are needed to show it, and then left-pad, but given that MMTC will
        // always align the SCLK values to coarse ticks, follow the SIS examples and simply use three zeroes to the
        // right of the decimal

        final int numDigitsForFineTicks = 3;
        final int numDigitsForCoarseTicks = 16 - numDigitsForFineTicks - 1; // leaving one char for the '.' separator

        final Integer coarseSclkAsInt = (int) Math.floor(coarseSclk);
        if ((coarseSclk % 1) != 0) {
            throw new IllegalArgumentException(String.format("Given SCLK value contains a nonzero decimal tick and cannot be converted to an SCLK string for a SCLK-SCET file: %f", coarseSclk));
        }

        String coarseTicksStr = Integer.toString(coarseSclkAsInt);
        if (coarseTicksStr.length() > numDigitsForCoarseTicks) {
            throw new IllegalStateException(String.format("Not enough digits to write coarse ticks (%s); maximum is %d characters", coarseTicksStr, numDigitsForCoarseTicks));
        }

        final int fineSclk = 0;

        return String.format("%0" + numDigitsForCoarseTicks + "d", coarseSclkAsInt)
                + "."
                + String.format("%0" + numDigitsForFineTicks + "d", fineSclk);
    }

    /**
     * Creates the new SCLK/SCET from an existing file, updates it with a new time correlation record.
     *
     * @param originalFilespec IN the full file specification of the original source product file
     * @throws TextProductException if the SCLK/SCET file could not be created
     * @throws TimeConvertException if a computational error occurs
     */
    public void createNewSclkScetFile(String originalFilespec) throws TextProductException, TimeConvertException {
        setSourceFilespec(originalFilespec);
        createFile();
    }

}
