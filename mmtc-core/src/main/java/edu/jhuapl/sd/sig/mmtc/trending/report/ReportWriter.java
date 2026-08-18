package edu.jhuapl.sd.sig.mmtc.trending.report;

import java.io.IOException;
import java.nio.file.Path;

public interface ReportWriter {
    Path write() throws IOException;
}
