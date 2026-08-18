package edu.jhuapl.sd.sig.mmtc.trending.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.RawValue;
import edu.jhuapl.sd.sig.mmtc.app.BuildInfo;
import edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.CorrelationTriplet;
import edu.jhuapl.sd.sig.mmtc.tlm.TlmUtils;
import edu.jhuapl.sd.sig.mmtc.trending.TrendingReportContext;
import edu.jhuapl.sd.sig.mmtc.trending.stats.ErrorTrendingMetrics;
import edu.jhuapl.sd.sig.mmtc.trending.stats.TrendingStats;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class TrendingReportModel {
    public final TrendingReportContext ctx;
    public final String generatedDateString;
    public final boolean includeCharts;

    public TrendingReportModel(TrendingReportContext ctx) {
        this(ctx, true);
    }

    public TrendingReportModel(TrendingReportContext ctx, boolean includeCharts) {
        this.ctx = ctx;
        this.generatedDateString = TimeConvert.timeToIsoUtcString(ctx.appRunTime, 0);
        this.includeCharts = includeCharts;
    }

    public List<TrendingStats> getReportingPeriods() {
        List<TrendingStats> periods = new ArrayList<>();
        if (ctx.statsPastNDays.isSet()) {
            periods.add(ctx.statsPastNDays.get());
        }
        if (ctx.statsSinceLastCorrelation.isSet()) {
            periods.add(ctx.statsSinceLastCorrelation.get());
        }
        return periods;
    }

    public TrendingReportContext getCtx() {
        return this.ctx;
    }

    public String getTitle() {
        return ctx.config.getMissionName() + " Timekeeping Trending Report (" + generatedDateString + ")";
    }

    public String getGeneratedBy() {
        BuildInfo buildInfo = new BuildInfo();
        return String.format(
                "MMTC %s (%s) on %s using SCLK kernel %s",
                buildInfo.version,
                buildInfo.commit,
                generatedDateString,
                ctx.currentSclkKernelDescription.get()
        );
    }

    public String getGeneratedDateString() {
        return generatedDateString;
    }

    public String getPicoCss() throws IOException {
        return loadResource("/templating/assets/pico.classless.green.css");
    }

    public String getEchartsJs() throws IOException {
        return loadResource("/templating/assets/echarts.min.js");
    }

    public String getUtilsJs() throws IOException {
        return loadResource("/templating/assets/utils.js");
    }

    public String getDateFnsJs() throws IOException {
        return loadResource("/templating/assets/date-fns.min.js");
    }

    private String loadResource(String resourcePath) throws IOException {
        return IOUtils.resourceToString(resourcePath, Charset.defaultCharset());
    }

    public List<Map<String, Object>> getTrendingPeriods() {
        return ctx.getTrendingPeriods().stream().map(this::getModelFor).collect(Collectors.toList());
    }

    private LinkedHashMap<String, Object> getModelFor(TrendingStats stats) {
        LinkedHashMap<String, Object> modelStats = new LinkedHashMap<>();
        modelStats.put("title", stats.title.get());
        modelStats.put("trendRangeStartScetUtc", formatDateTime(stats.trendOverTimeRangeScetUtc.get().getStart()));
        modelStats.put("trendRangeStopScetUtc", formatDateTime(stats.trendOverTimeRangeScetUtc.get().getStop()));

        modelStats.put(
                "errorTrendingMetrics",
                stats.getTrendingMetricTypes().stream().map(errorTrendingMetrics -> {
                    Map<String, Object> errorTrendingMetricsModel = new HashMap<>();
                    errorTrendingMetricsModel.put("title", errorTrendingMetrics.title.get());
                    errorTrendingMetricsModel.put("sufficientDataForTrendingExists", errorTrendingMetrics.sufficientDataForTrendingExists.get());

                    errorTrendingMetricsModel.put("showPlot", false);
                    if (errorTrendingMetrics.sufficientDataForTrendingExists.get()) {
                        errorTrendingMetricsModel.put("priorScetUtcForErrorCalc", formatDateTime(errorTrendingMetrics.priorScetUtcForErrorCalc.get(), 3));
                        errorTrendingMetricsModel.put("priorErrorMs", formatBigDecimal(errorTrendingMetrics.priorErrorMs.get()));
                        errorTrendingMetricsModel.put("telemetryTimeRangeDays", formatBigDecimal(TimeConvert.numDaysBetween(errorTrendingMetrics.priorScetUtcForErrorCalc.get(), errorTrendingMetrics.latestScetUtcForErrorCalc.get())));

                        if (errorTrendingMetrics.tlmForPlotting.isSet()) {
                            errorTrendingMetricsModel.put("showPlot", true);
                            errorTrendingMetricsModel.put("echartsJs", getEchartsJs(errorTrendingMetrics, ctx.currentSclkKernel.get().getTriplets()));
                        }

                        errorTrendingMetricsModel.put("latestScetUtcForErrorCalc", formatDateTime(errorTrendingMetrics.latestScetUtcForErrorCalc.get(), 3));
                        errorTrendingMetricsModel.put("latestErrorMs", formatBigDecimal(errorTrendingMetrics.latestErrorMs.get()));

                        errorTrendingMetricsModel.put("errorThresholdMs", formatBigDecimal(errorTrendingMetrics.errorThresholdMs.get()));
                        errorTrendingMetricsModel.put("valChangeRatePerDay", formatBigDecimal(errorTrendingMetrics.valChangeRateMsPerDay.get()));
                        errorTrendingMetricsModel.put("thresholdAlreadyViolated", errorTrendingMetrics.thresholdAlreadyViolated.get());
                        errorTrendingMetricsModel.put("estimatedScetWhenErrorThresholdWillBeReached", formatDateTime(errorTrendingMetrics.estimatedScetWhenErrorThresholdWillBeReached.get()));
                        errorTrendingMetricsModel.put("numDaysUntilErrorThresholdWillBeReached", formatBigDecimalAbsoluteValue(errorTrendingMetrics.numDaysUntilErrorThresholdWillBeReached.get()));

                        errorTrendingMetricsModel.put("errorThresholdMayHaveAlreadyBeenReached", false);
                        if (errorTrendingMetrics.numDaysUntilErrorThresholdWillBeReached.get().isPresent()) {
                            if (errorTrendingMetrics.numDaysUntilErrorThresholdWillBeReached.get().get().doubleValue() < 0) {
                                errorTrendingMetricsModel.put("errorThresholdMayHaveAlreadyBeenReached", true);
                            }
                        }
                    }

                    return errorTrendingMetricsModel;
                })
                .collect(Collectors.toList())
        );

        return modelStats;
    }

    private static String formatDateTime(Optional<OffsetDateTime> time) {
        return formatDateTime(time, 0);
    }

    private static String formatDateTime(OffsetDateTime time) {
        return formatDateTime(time, 0);
    }

    private static String formatDateTime(Optional<OffsetDateTime> time, int subsecPrecision) {
        return time
                .map(tval -> formatDateTime(tval, subsecPrecision))
                .orElse("N/A");
    }

    private static String formatDateTime(OffsetDateTime time, int subsecPrecision) {
        return TimeConvert.timeToIsoUtcString(time, subsecPrecision);
    }

    private static String formatBigDecimal(Optional<BigDecimal> val) {
        return val
                .map(TrendingReportModel::formatBigDecimal)
                .orElse("N/A");
    }

    private static String formatBigDecimalAbsoluteValue(Optional<BigDecimal> val) {
        return val
                .map(v -> formatBigDecimal(v.abs()))
                .orElse("N/A");
    }

    private static String formatBigDecimal(BigDecimal val) {
        // return String.format("%.2f", val.doubleValue());
        return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // writes at least three significant figures, with a precision of at least three digits after the decimal
    private static String formatErrorRate(Double val) {
        BigDecimal rounded = BigDecimal.valueOf(val).round(new MathContext(3, RoundingMode.HALF_UP));
        int scale = Math.max(3, rounded.scale());
        return rounded.setScale(scale).toPlainString();
    }

    private static Map<Object, Object> mapOf(Object... keysAndVals) {
        List<Object> kv = Arrays.asList(keysAndVals);
        if (kv.size() % 2 != 0) {
            throw new IllegalArgumentException("Mismatching keys and vals");
        }
        Map<Object, Object> res = new HashMap<>();

        for (int i = 0; i < kv.size(); i += 2) {
            res.put(kv.get(i), kv.get(i+1));
        }

        return res;
    }

    private static List<Object> listOf(Object... objs) {
        return Arrays.asList(objs);
    }

    private String getEchartsJs(ErrorTrendingMetrics errorTrendingMetrics, List<CorrelationTriplet> triplets) {
        List<String> jsLines = new ArrayList<>();

        final String chartJsName = errorTrendingMetrics.title.get()
                .replace(" ", "_")
                .replace("(", "_")
                .replace(")", "_") + "Chart";

        final String plotDivId = errorTrendingMetrics.title.get() + "-plot";

        jsLines.add("const " + chartJsName + " = echarts.init(");
        jsLines.add("document.getElementById('" + plotDivId + "'),");
        jsLines.add("null,");
        jsLines.add("{renderer: 'canvas'},");
        jsLines.add(");");

        final String chartOptionJsName = chartJsName + "option";

        jsLines.add("const " + chartOptionJsName + " = " + getEchartsOption(errorTrendingMetrics, triplets) + ";");

        final long beginTime = errorTrendingMetrics.tlmForPlotting.get().get(0).getTimeScetUtc().toInstant().toEpochMilli();
        final long endTime = errorTrendingMetrics.tlmForPlotting.get().get(errorTrendingMetrics.tlmForPlotting.get().size() - 1).getTimeScetUtc().toInstant().toEpochMilli();

        jsLines.add(chartOptionJsName + ".xAxis[0].min = " + beginTime + ";");
        jsLines.add(chartOptionJsName + ".xAxis[1].min = " + beginTime + ";");

        jsLines.add(chartOptionJsName + ".xAxis[0].max = " + endTime + ";");
        jsLines.add(chartOptionJsName + ".xAxis[1].max = " + endTime + ";");

        jsLines.add(chartOptionJsName + ".dataZoom[0].startValue = " + beginTime + ";");
        jsLines.add(chartOptionJsName + ".dataZoom[0].endValue = " + endTime + ";");

        jsLines.add(chartJsName + ".setOption("+chartOptionJsName+");");
        jsLines.add("window.addEventListener('resize', () => "+chartJsName+".resize());");

        return String.join("\n", jsLines);
    }

    private String getEchartsOption(ErrorTrendingMetrics errorTrendingMetrics, List<CorrelationTriplet> triplets) {

        Map<String, Object> opt = new LinkedHashMap<>();

        // opt.put("title", mapOf("text", errorTrendingMetrics.title.get()));

        opt.put("tooltip", mapOf(
                "show", true,
                "trigger", "axis",
                "formatter", new RawValue("echartstooltipFormatter")
        ));

        opt.put("legend", mapOf(
                "show", true,
                "top", "2%",
                "left", "center",
                "orient", "horizontal"
        ));

        opt.put("grid", listOf(
                mapOf(
                        "top", 80,
                        "left", 70,
                        "right", 20,
                        "height", "60%"
                ),
                mapOf(
                        "top", "80%",
                        "left", 70,
                        "right", 20,
                        "height", "5%"
                )
        ));

        opt.put("xAxis", listOf(
                mapOf(
                        "name", "SCET (UTC)",
                        "nameLocation", "middle",
                        "nameGap", 30,
                        "type", "time",
                        "interval", 1000 * 60 * 30, // 30 minutes
                        "gridIndex", 0,
                        "boundaryGap", false,
                        "axisLabel", mapOf(
                                "hideOverlap", true,
                                "rich", mapOf(
                                        "bold", mapOf(
                                                "fontWeight", "bold"
                                        )
                                ),
                                "formatter", new RawValue("xAxisFormatter")
                        )
                ),
                mapOf(
                        "type", "time",
                        "interval", 1000 * 60 * 30, // 30 minutes
                        "gridIndex", 1,
                        "boundaryGap", false,
                        "axisLabel", false
                )
        ));

        opt.put("yAxis", listOf(
                mapOf(
                        "type", "value",
                        "gridIndex", 0,
                        "name", "Error (ms)",
                        "nameGap", 20
                ),
                mapOf(
                        "type", "value",
                        "gridIndex", 1,
                        "name", "Correlations",
                        "show", false,
                        "min", 0,
                        "max", 1

                )
        ));

        opt.put("dataZoom", listOf(
                mapOf(
                        "type", "inside",
                        "xAxisIndex", listOf(0,1)
                ),
                mapOf(
                        "type", "slider",
                        "xAxisIndex", listOf(0, 1),
                        "showDataShadow", false,
                        "backgroundColor", "#D9FBE8",
                        "fillerColor", "#B3F5D1",
                        "moveHandleStyle", mapOf(
                                "color", "#007F45"
                        )
                )
        ));

        opt.put("series", listOf(
                mapOf(
                        "id", errorTrendingMetrics.trendingPeriodId.get(),
                        "name", errorTrendingMetrics.title.get(),
                        "type", "line",
                        "xAxisIndex", 0,
                        "yAxisIndex", 0,
                        "symbol", "circle",
                        "symbolSize", 6,
                        "lineStyle", mapOf("color", "#75EDAE"),
                        "itemStyle", mapOf("color", "#00A155"),
                        "showSymbol", true,
                        "data", errorTrendingMetrics.tlmForPlotting.get().stream().map(TrendingReportModel::toEchartTlmPoint).collect(Collectors.toList())

                ),
                mapOf(
                        "id", "timeCorrRecords-for-"+errorTrendingMetrics.trendingPeriodId.get(),
                        "name", "SCLK Kernel Records",
                        "type", "scatter",
                        "xAxisIndex", 1,
                        "yAxisIndex", 1,
                        "symbol", "triangle",
                        "symbolSize", 14,
                        "itemStyle", mapOf("color", "#0A5331"),
                        "data", triplets.stream().map(TrendingReportModel::toEchartTriplet).collect(Collectors.toList()),
                        "cursor", "default"
                )
        ));

        try {
            return escapeJsonForHtml(new ObjectMapper().writeValueAsString(opt));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<Object, Object> toEchartTlmPoint(TlmUtils.TlmPoint tlmPoint) {
        return mapOf(
                "name", TimeConvert.timeToIsoUtcString(tlmPoint.getTimeScetUtc(), 3),
                "value", listOf(tlmPoint.getTimeScetUtc().toInstant().toEpochMilli(), tlmPoint.getVal().doubleValue()),
                "originalTlmPoint", mapOf("timeScetUtc", formatDateTime(tlmPoint.getTimeScetUtc()), "value", formatBigDecimal(tlmPoint.getVal())),
                "itemStyle", mapOf(
                        "color", "#00A155"
                )
        );
    }

    private static Map<Object, Object> toEchartTriplet(CorrelationTriplet triplet) throws RuntimeException {
        // todo check this
        try {
            OffsetDateTime tripletScetUtc = TimeConvert.tdtToUtc(triplet.getTdt(), 6);

            return mapOf(
                    "name", TimeConvert.timeToIsoUtcString(tripletScetUtc, 6),
                    "value", listOf(tripletScetUtc.toInstant().toEpochMilli(), 0.5),
                    "originalTriplet", mapOf("encSclk", formatBigDecimal(triplet.encSclk), "tdtGCalStr", triplet.getTdtCalStr(3), "clkchgrate", formatBigDecimal(triplet.clkChgRate))
            );
        } catch (TimeConvertException e) {
            throw new RuntimeException(e);
        }
    }

    private static String escapeJsonForHtml(String json) {
        return json
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("&", "\\u0026");
    }
}
