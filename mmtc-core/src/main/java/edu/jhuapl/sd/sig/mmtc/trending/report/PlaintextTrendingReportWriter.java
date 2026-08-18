package edu.jhuapl.sd.sig.mmtc.trending.report;

import edu.jhuapl.sd.sig.mmtc.reporting.PlaintextTemplater;
import edu.jhuapl.sd.sig.mmtc.trending.TrendingReportContext;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class PlaintextTrendingReportWriter extends PlaintextTemplater<TrendingReportContext> implements ReportWriter {
    private final Path outputPath;

    public PlaintextTrendingReportWriter(TrendingReportContext ctx, Path outputPath) {
        super(ctx, "trending_report.txt");
        this.outputPath = outputPath;
    }


    @Override
    public Path write() throws IOException {
        try (Writer writer = Files.newBufferedWriter(outputPath)) {
            getBuiltInTemplate().process(new TrendingReportModel(ctx), writer);
        } catch (TemplateException e){
            throw new IOException(e);
        }

        return outputPath;
    }
}
