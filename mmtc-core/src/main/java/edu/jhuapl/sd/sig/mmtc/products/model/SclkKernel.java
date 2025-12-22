package edu.jhuapl.sd.sig.mmtc.products.model;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.List;

/**
 * The SclkKernel class creates the SCLK kernel file and its contents. The SCLK Kernel is the primary product of MMTC.
 */
public class SclkKernel extends TextProduct {
    public static final String FILE_SUFFIX = ".tsc";

    /* The number of fields in an SCLK kernel triplet time correlation record. */
    public static final int NUM_FIELDS_IN_TRIPLET = 3;

    /* Indices for the fields in a triplet record. */
    public static final int TRIPLET_ENCSCLK_FIELD_INDEX = 0;
    public static final int TRIPLET_TDTG_FIELD_INDEX = 1;
    public static final int TRIPLET_CLKCHGRATE_FIELD_INDEX = 2;

    private Optional<CorrelationTriplet> newTriplet = Optional.empty();

    /* New interpolated clock change rate to overwrite the predicted rate in the existing SCLK kernel record. */
    private Double updatedClockChgRate;

    /* Indicates if an interpolated clock change rate is to replace the rate in the existing SCLK kernel record. */
    private boolean newClkChgRateSet = false;

    /* The zero-based index of the last data record in the SCLK kernel (i.e., the last record containing a triplet. */
    private int endDataNum = -1;

    private Optional<CorrelationTriplet> smoothingTriplet = Optional.empty();

    /**
     * Class constructor.
     *
     * @param filespec the full file specification of the SCLK kernel
     */
    public SclkKernel(String filespec) {
        super();
        File f = new File(filespec);
        setDir(f.getParent());
        setName(f.getName());
        sourceFilespec = filespec;
    }


    /**
     * Class constructor.
     *
     * @param dir      IN the directory to which the new SCLK kernel is to be written
     * @param filename IN the new SCLK kernel filename
     */
    public SclkKernel(String dir, String filename) {
        super();
        setDir(dir);
        setName(filename);
        sourceFilespec = dir + File.separator + filename;
    }


    /**
     * Copy constructor.
     *
     * @param kernel the SclkKernel object to copy
     */
    public SclkKernel(SclkKernel kernel) {
        sourceFilespec      = kernel.sourceFilespec;
        filename            = "newSclkKernel";
        dirname             = kernel.dirname;
    }


    /**
     * Sets the values of the new time correlation record.
     *
     * @param encSclk      IN SCLK in encoded SCLK form
     * @param tdtStr       IN TDT in calendar string
     * @param clockChgRate IN clock change rate
     */
    public void setNewTriplet(Double encSclk, String tdtStr, Double clockChgRate) {
        this.newTriplet = Optional.of(new CorrelationTriplet(encSclk, tdtStr, clockChgRate));
    }

    /**
     * Sets the values of the new time correlation record.
     *
     * @param newTriplet   newTriplet
     */
    public void setNewTriplet(CorrelationTriplet newTriplet) {
        this.newTriplet = Optional.of(newTriplet);
    }

    public boolean hasSmoothingRecordSet() {
        return smoothingTriplet.isPresent();
    }

    public static class CorrelationTriplet {
        public final double encSclk;
        public final String tdtStr;
        public final double clkChgRate;

        public CorrelationTriplet(double encSclk, String tdtStr, double clkChgRate) {
            this.encSclk = encSclk;
            this.tdtStr = tdtStr;
            this.clkChgRate = clkChgRate;
        }
    }

    /**
     * Overwrites the clock change rate field in the last time correlation record in the product with
     * the supplied value.
     *
     * @param clockChgRate the new clock change rate
     */
    public void setReplacementClockChgRate(Double clockChgRate) {
        updatedClockChgRate = clockChgRate;
        newClkChgRateSet    = true;
    }


    /**
     * Creates the new SCLK kernel product. Implements the corresponding abstract method in the parent class.
     *
     * @throws TextProductException if the product cannot be created
     */
    public void createNewProduct() throws TextProductException {

        /* Find the last data (triplet) record in the SCLK kernel data. A data record should contain
         * 3 fields and the second (index 1) field should contain a "@" character.
         */
        endDataNum = lastDataRecNum(sourceProductLines);

        if (endDataNum < 3) {
            throw new TextProductException("Cannot find last data record. Invalid SCLK kernel data loaded.");
        }

        if (sourceProductLines.size() < 1) {
            throw new TextProductException("Valid source SCLK Kernel data have not been loaded.");
        }

        if (! (newTriplet.isPresent() && sourceProductReadIn)) {
            throw new TextProductException("Cannot create SCLK kernel. New record values have not been set.");
        }

        /* Create the new SCLK kernel from the Old. */
        newProductLines = new ArrayList<>(sourceProductLines);

        /* Replace the FILENAME field. */
        String newname = "FILENAME = " + "\"" + getName() + "\"";
        replaceFieldValue("FILENAME =", newname, "=");

        /* Replace the CREATION_DATE field. */
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
        OffsetDateTime productCreationTime = getProductCreationTime();
        String newDate = "CREATION_DATE = " + "\"" + productCreationTime.format(formatter) + "\"";
        replaceFieldValue("CREATION_DATE =", newDate, "=");

        final String previousFinalRecordForFormattingReference = sourceProductLines.get(endDataNum);

        // Replace the clock change rate of the last record in the product with the new rate, if selected
        if (newClkChgRateSet) {
            String updatedTriplet = replaceChgRate();
            newProductLines.remove(endDataNum);
            newProductLines.add(endDataNum, updatedTriplet);
        }

        // Append the smoothing record, if selected
        if (smoothingTriplet.isPresent()) {
            newProductLines.add(endDataNum + 1, assembleNewTripletRecord(smoothingTriplet.get(), previousFinalRecordForFormattingReference));
            endDataNum += 1;
        }

        // Form a new time correlation record from the triplet values and append it to the SCLK kernel data
        newProductLines.add(endDataNum + 1, assembleNewTripletRecord(newTriplet.get(), previousFinalRecordForFormattingReference));
    }


    /**
     * Replaces the existing value of the clock change rate component of the
     * time correlation record triplet with a new value. It preserves the
     * existing record format and spacing.
     *
     * @return the updated time correlation record
     * @throws TextProductException if the time correlation record is invalid or cannot be updated.
     */
    private String replaceChgRate() throws TextProductException {
        String record          = sourceProductLines.get(endDataNum);
        String[] tripletFields = parseRecord(record, NUM_FIELDS_IN_TRIPLET);

        /* Replace the existing clock change rate value with the new one. */
        String newChgRateStr = formatChgRateStr(updatedClockChgRate);

        /* Replace the clock change rate. It is the third field of the SCLK kernel record. */
        String record1 = record.replaceFirst(tripletFields[TRIPLET_CLKCHGRATE_FIELD_INDEX], newChgRateStr);

        return record1;
    }

    /**
     * Formats a clock change rate into a string form of 11 decimal places.
     *
     * @param clkChgRate the clock change rate in numeric form
     * @return the clock change rate as a formatted string
     */
    private static String formatChgRateStr(Double clkChgRate) {
        DecimalFormat clkChgRateFormat = new DecimalFormat("0.00000000000");
        clkChgRateFormat.setRoundingMode(RoundingMode.HALF_UP);
        String chgRateStr = clkChgRateFormat.format(clkChgRate);
        if (!chgRateStr.contains(".")) {
            chgRateStr = chgRateStr + ".00000000000";
        }

        return chgRateStr;
    }

    /**
     * Gets the selected value of the last record in the previous SCLK kernel
     * based on the index ENCSCLK, TDTG, or CLKCHGRATE.
     *
     * @param index IN:the index of the kernel record
     * @return TDT(G) value of last record
     * @throws TextProductException if the TDT(G) value could not be obtained
     */
    public String getLastRecValue(int index) throws TextProductException {
        String record  = sourceProductLines.get(endDataNum);

        // Remove any parentheses that might be in the record.
        String recstr  = record.replace("(", "");
        recstr         = recstr.replace(")", "");
        String[] tripletFields = parseRecord(recstr, NUM_FIELDS_IN_TRIPLET);

        return tripletFields[index];
    }

    /**
     * Retrieves a data record from the current SCLK kernel that is the specified number of hours
     * previous to the supplied time in TDT.
     *
     * @param fromTdt       IN the time to look back from in TDT
     * @param lookBackHours IN the number of hours to look back
     * @return the parsed record that is the number of hours back
     * @throws TextProductException if the prior record could not be found
     */
    public String[] getPriorRec(Double fromTdt, Double lookBackHours, Collection<String> smoothingRecordTdtStringsToIgnore) throws TextProductException {
        try {
            final double minLookbackSeconds = lookBackHours * 3600.;

            for (int i = endDataNum; i > 0; i--) {
                String record = sourceProductLines.get(i);

                if (! isDataRecord(record)) {
                    throw new TextProductException("Look back time invalid for the specified SCLK kernel.");
                }

                final String[] tripletFields = parseRecord(record, NUM_FIELDS_IN_TRIPLET);
                final String tdtStr = tripletFields[TRIPLET_TDTG_FIELD_INDEX].substring(1);
                final double tdtSec = TimeConvert.tdtCalStrToTdt(tdtStr);

                if (smoothingRecordTdtStringsToIgnore.contains(tdtStr)) {
                    logger.trace(String.format("getPriorRec: skipping record at TDT %s due to it being a smoothing record", tdtStr));
                    continue;
                }

                if ((fromTdt - tdtSec) < minLookbackSeconds) {
                    logger.trace(String.format("getPriorRec: skipping record at TDT %s due to not meeting lookback minimum", tdtStr));
                    continue;
                }

                return tripletFields;
            }
        } catch (TimeConvertException e) {
            throw new TextProductException("Unable to convert TDT string to numeric TDT seconds.", e);
        }

        throw new TextProductException("Look back time invalid for the specified SCLK kernel.");
    }

    /**
     * Retrieves a data record from the current SCLK kernel that is the specified number of hours
     * previous to the supplied time in TDT.
     *
     * @param fromTdt       IN the time to look back from in TDT
     * @param minLookbackHours IN the min number of hours to look back
     * @param maxLookbackHours IN the max number of hours to look back
     * @return the parsed record that is the number of hours back
     * @throws TextProductException if there are problems reading the prior record
     */
    public List<String[]> getPriorRecs(Double fromTdt, Double minLookbackHours, Double maxLookbackHours, Collection<String> smoothingRecordTdtStringsToIgnore) throws TextProductException {
        final double minLookbackSeconds = minLookbackHours * 3600.;
        final double maxLookbackSeconds = maxLookbackHours * 3600.;

        List<String[]> results = new ArrayList<>();

        try {
            for (int i = endDataNum; i > 0; i--) {
                String record = sourceProductLines.get(i);

                if (! isDataRecord(record)) {
                    continue;
                }

                final String[] recTripletFields = parseRecord(record, NUM_FIELDS_IN_TRIPLET);
                final String recTdtStr = recTripletFields[TRIPLET_TDTG_FIELD_INDEX].substring(1);
                final double recTdtSec = TimeConvert.tdtCalStrToTdt(recTdtStr);

                if (smoothingRecordTdtStringsToIgnore.contains(recTdtStr)) {
                    logger.debug(String.format("getPriorRec: skipping record at TDT %s due to it being a smoothing record", recTdtStr));
                    continue;
                }

                final double recDeltaTdt = fromTdt - recTdtSec;
                if ((recDeltaTdt < minLookbackSeconds) || recDeltaTdt > maxLookbackSeconds) {
                    logger.trace(String.format("getPriorRec: skipping record at TDT %s due to not meeting lookback constraints", recTdtStr));
                    continue;
                }

                results.add(recTripletFields);
            }
        } catch (TimeConvertException e) {
            throw new TextProductException("Unable to convert TDT string to numeric TDT seconds.", e);
        }

        return results;
    }

     /**
     * Creates a new time correlation record from the encoded SCLK, TDT, and clock change rate triplet
     * in the format that matches that of the existing, previous record in the SCLK kernel data.
     *
     * @return the new SCLK kernel time correlation record
     * @throws TextProductException if the kernel record could not be assembled
     */
    private static String assembleNewTripletRecord(CorrelationTriplet triplet, String sourceTripletAsStringForFormatting) throws TextProductException {

        /* Use the last existing record in the SCLK kernel data as the template to create the new one.
           This assures consistency of format and spacing. */
        String[] tripletFields = parseRecord(sourceTripletAsStringForFormatting, NUM_FIELDS_IN_TRIPLET);

        /* New encoded SCLK value. */
        DecimalFormat encSclkFormat = new DecimalFormat("#.#");
        encSclkFormat.setRoundingMode(RoundingMode.HALF_UP);
        String encSclkStr = encSclkFormat.format(triplet.encSclk);

        /* Format the encoded SCLK field such that the new triplet entry left-aligns with the previous entry. */
        String record0;
        if (encSclkStr.length() > tripletFields[TRIPLET_ENCSCLK_FIELD_INDEX].length()) {
            String paddedEncSclk = StringUtils.leftPad(tripletFields[TRIPLET_ENCSCLK_FIELD_INDEX],encSclkStr.length(), ' ');
            record0 = sourceTripletAsStringForFormatting.replaceFirst(paddedEncSclk, encSclkStr);
        } else {
            record0 = sourceTripletAsStringForFormatting.replaceFirst(tripletFields[TRIPLET_ENCSCLK_FIELD_INDEX], encSclkStr);
        }

        /* New TDT value in calendar string form. */
        String record1 = record0.replaceFirst(tripletFields[TRIPLET_TDTG_FIELD_INDEX], "@" + triplet.tdtStr);

        /* New clock change rate value. */
        String chgRateStr = formatChgRateStr(triplet.clkChgRate);
        String record2    = record1.replaceFirst(tripletFields[TRIPLET_CLKCHGRATE_FIELD_INDEX], chgRateStr);

        return record2;
    }


    /**
     * Determines if a string contains a numeric value.
     * @param val IN the string to evaluate
     * @return true if the value is numeric, false otherwise
     */
    public static boolean isNumVal(String val) {
        boolean isnum = false;
        if (val != null) {
            try {
                Double.parseDouble(val);
                isnum = true;
            } catch (NumberFormatException ex) {
                isnum = false;
            }
        }
        return isnum;
    }



    /**
     * Determines if the record is an SCLK kernel time correlation record containing a triplet
     * or if its supporting text. Implements the corresponding abstract method in the parent class.
     * An SCLK kernel time correlation contains three fields (enc SCLK, TDT, ClockChgRate) separated
     * by whitespace. The TDT value may be represented either as a calendar string, which always
     * has "@" as its first character, or a double precision real number.
     *
     * @param record IN the record to evaluate
     * @return true if the record is a time correlation triplet, false otherwise
     */
    public boolean isDataRecord(String record) {
        boolean isdata = false;
        String recstr = record.replace("(","");
        recstr = recstr.replace(")", "");

        String[] fields = recstr.trim().split("\\s+");
        if (fields.length == (TRIPLET_ENCSCLK_FIELD_INDEX + TRIPLET_TDTG_FIELD_INDEX + TRIPLET_CLKCHGRATE_FIELD_INDEX)) {
            isdata = fields[1].startsWith("@") ||
                    (isNumVal(fields[0]) && isNumVal(fields[1]) && isNumVal(fields[2]));
        }

        return isdata;
    }


    /**
     * Creates the new SCLK kernel from an existing one, updates it with a new time correlation record.
     *
     * @param sourceSclkFilespec IN the full file specificaction of the original source SCLK kernel file
     * @throws TextProductException if the SCLK kernel could not be created
     * @throws TimeConvertException if a computational error occurred
     */
    public void createNewSclkKernel(String sourceSclkFilespec) throws TextProductException, TimeConvertException {
        setSourceFilespec(sourceSclkFilespec);
        createFile();
    }

    public String[] getLastXRecords(int numRecords) {
        if(numRecords < 1) { return new String[0]; }
        int lastRecordIndex = lastDataRecNum(newProductLines);

        String[] records = new String[numRecords];
        for(int i=lastRecordIndex-(numRecords-1), j=0;i < lastRecordIndex+1; i++, j++) {
            records[j] = newProductLines.get(i);
        }
        return records;
    }

    public List<String[]> getParsedRecords() throws TextProductException {
        List<String[]> dataRecords = new ArrayList<>();

        for (int i = 0; i < sourceProductLines.size(); i++) {
            String sclkKernelRecord = sourceProductLines.get(i).trim();
            if (isDataRecord(sclkKernelRecord)) {
                String[] parsedVals = parseRecord(sclkKernelRecord, 3);
                if (parsedVals[TRIPLET_TDTG_FIELD_INDEX].startsWith("@")) {
                    parsedVals[TRIPLET_TDTG_FIELD_INDEX] = parsedVals[TRIPLET_TDTG_FIELD_INDEX].replaceFirst("@", "");
                }
                dataRecords.add(parsedVals);
            }
        }

        return dataRecords;
    }

    @Override
    public void readSourceProduct() throws IOException, TextProductException {
        super.readSourceProduct();
        this.endDataNum = lastDataRecNum(sourceProductLines);
        if (this.endDataNum < 1) {
            throw new TextProductException("Invalid input SCLK Kernel. No time correlation records found.");
        }
    }

    public String getVersionString(final String sclkBaseName, final String separator) {
        return Paths.get(getPath()).getFileName().toString().replace(sclkBaseName + separator, "").replace(FILE_SUFFIX, "");
    }

    public boolean hasNewClkChgRateSet() {
        return newClkChgRateSet;
    }


    /**
     * Generates a new SCLK Kernel but doesn't yet write it to file. Helper method for writeNewProduct
     * @param ctx The current TimeCorrelationContext
     * @throws TimeConvertException
     */
    public static void calculateNewProduct(TimeCorrelationContext ctx) throws TimeConvertException, TextProductException {
        final SclkKernel newSclkKernel = new SclkKernel(ctx.currentSclkKernel.get());

        newSclkKernel.setProductCreationTime(ctx.appRunTime);
        newSclkKernel.setDir(ctx.config.getSclkKernelOutputDir().toString());
        newSclkKernel.setName(ctx.config.getSclkKernelBasename() + ctx.config.getSclkKernelSeparator() + ctx.newSclkVersionString.get() + ".tsc");

        final CorrelationTriplet newPredictedTriplet = new CorrelationTriplet(
                ctx.correlation.target.get().getTargetSampleEncSclk(),
                TimeConvert.tdtToTdtCalStr(ctx.correlation.target.get().getTargetSampleTdtG()),
                ctx.correlation.predicted_clock_change_rate.get()
        );
        newSclkKernel.setNewTriplet(newPredictedTriplet);

        ctx.correlation.newPredictedTriplet.set(newPredictedTriplet);

        if (ctx.correlation.interpolated_clock_change_rate.isSet()) {
            newSclkKernel.setReplacementClockChgRate(ctx.correlation.interpolated_clock_change_rate.get());
            // ctx.correlation.updatedInterpolatedTriplet.set(newSclkKernel.getUpdatedPriorCorrelationRecord());
        }

        if (ctx.correlation.newSmoothingTriplet.isSet()) {
            newSclkKernel.setSmoothingTriplet(ctx.correlation.newSmoothingTriplet.get());
        }

        ctx.newSclkKernel.set(newSclkKernel);
    }

    /**
     * Writes a new SCLK Kernel
     * @param ctx the current time correlation context from which to pull information for the output product
     *
     * @throws MmtcException if the SCLK Kernel cannot be written
     * @return the ProductWriteResult describing the newly-written product
     */
    public static ProductWriteResult writeNewProduct(TimeCorrelationContext ctx) throws MmtcException {
        return writeNewProduct(ctx, null);
    }

    /**
     * Writes a new SCLK Kernel
     * @param ctx the current time correlation context from which to pull information for the output product
     *
     * @throws MmtcException if the SCLK Kernel cannot be written
     * @return the ProductWriteResult describing the newly-written product
     */
    public static ProductWriteResult writeNewProduct(TimeCorrelationContext ctx, Path overridingPath) throws MmtcException {
        try {
            calculateNewProduct(ctx);

            final Path path;
            if (overridingPath == null) {
                path = Paths.get(ctx.newSclkKernel.get().getPath());
            } else {
                path = overridingPath;
            }

            // If this is a dry run, write the new kernel to the temp output path for subsequent deletion. If not,
            // write it to the usual location.
            switch(ctx.config.getDryRunConfig().mode) {
                case NOT_DRY_RUN: {
                    ctx.newSclkKernel.get().createFile();
                    break;
                }
                case DRY_RUN_RETAIN_NO_PRODUCTS: {
                    ctx.newSclkKernel.get().createFile("/tmp");
                    break;
                }
                case DRY_RUN_GENERATE_SEPARATE_SCLK_ONLY: {
                    ctx.newSclkKernel.get().createFile(path);
                    break;
                }
            }

            ctx.newSclkKernelPath.set(path);

            return new ProductWriteResult(
                    path,
                    ctx.newSclkVersionString.get()
            );
        } catch (TextProductException | TimeConvertException ex) {
            throw new MmtcException("Unable to write SCLK kernel", ex);
        }
    }

    private void setSmoothingTriplet(CorrelationTriplet smoothingTriplet) {
        this.smoothingTriplet = Optional.of(smoothingTriplet);
    }
}
