package edu.jhuapl.sd.sig.mmtc.products.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class NewSclkKernel {
    private static final String SCLK_KERNEL_IDENTIFIER = "KPL/SCLK";

    private List<KernelSection> sections;

    public static class KernelSection {
        protected List<String> lines;
    }

    public static class DataKernelSection extends KernelSection {

    }

    public static class SclkTripletSection extends DataKernelSection {

    }

    public static class TextKernelSection extends KernelSection {

    }



    public NewSclkKernel(NewSclkKernel other) {
        this.sections = new ArrayList<>(other.sections);
    }

    public static NewSclkKernel read(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);

        if (! lines.get(0).contains(SCLK_KERNEL_IDENTIFIER)) {
            throw new IOException("First line of file does not contain " + SCLK_KERNEL_IDENTIFIER);
        }


    }
}
