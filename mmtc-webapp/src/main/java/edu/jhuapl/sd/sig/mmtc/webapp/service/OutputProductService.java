package edu.jhuapl.sd.sig.mmtc.webapp.service;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.products.definition.AppendedFileOutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.definition.EntireFileOutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.definition.OutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.definition.SclkKernelProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.webapp.config.MmtcWebAppConfig;
import edu.jhuapl.sd.sig.mmtc.webapp.controller.OutputProductController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class OutputProductService {
    private final MmtcWebAppConfig config;
    private final List<OutputProductDefinition<?>> outputProductDefs;
    private final SclkKernelProductDefinition sclkKernelOutputProductDef;

    public OutputProductService(MmtcWebAppConfig config) {
        this.config = config;
        this.outputProductDefs = config.getAllOutputProductDefs();
        this.sclkKernelOutputProductDef = (SclkKernelProductDefinition) this.outputProductDefs.stream().filter(def -> def.getName().equals(SclkKernelProductDefinition.PRODUCT_NAME)).findFirst().get();
    }

    public SclkKernelProductDefinition getSclkKernelOutputProductDef() {
        return this.sclkKernelOutputProductDef;
    }

    public List<String> getExistingFilenamesForDef(OutputProductDefinition<?> def) throws MmtcException, IOException {
        if (def instanceof EntireFileOutputProductDefinition) {
            return ((EntireFileOutputProductDefinition) def)
                    .resolveLocation(config)
                    .findAllMatching()
                    .stream()
                    .map(p -> p.getFileName().toString())
                    .sorted(Collections.reverseOrder()) // to sort most recent filenames first
                    .toList();
        }

        if (def instanceof AppendedFileOutputProductDefinition) {
            AppendedFileOutputProductDefinition appendedDef = (AppendedFileOutputProductDefinition) def;
            Path pathToProduct = appendedDef.resolveLocation(config).pathToProduct;
            if (Files.exists(pathToProduct)) {
                return List.of(pathToProduct.getFileName().toString());
            } else {
                return Collections.emptyList();
            }
        }

        throw new MmtcException("Unexpected type for: " + def.getName());
    }

    public Optional<String> getLatestFilenameForDef(OutputProductDefinition<?> def) throws MmtcException, IOException {
        return getExistingFilenamesForDef(def).stream().findFirst();
    }

    public List<OutputProductDef> getAllOutputProductDefs() {
        return outputProductDefs.stream()
                .map(def -> {
                    try {
                        return new OutputProductDef(
                                def.getName(),
                                def.isBuiltIn(),
                                def.getClass().getSimpleName(),
                                getProductTypeAsString(def),
                                isSingleFileOutputProduct(def),
                                getExistingFilenamesForDef(def)
                        );
                    } catch (MmtcException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
    }

    public static String getProductTypeAsString(OutputProductDefinition<?> def) throws MmtcException {
        if (def instanceof EntireFileOutputProductDefinition) {
            return "EntireFileOutputProductDefinition";
        }

        if (def instanceof AppendedFileOutputProductDefinition) {
            return "AppendedFileOutputProductDefinition";
        }

        throw new MmtcException("Unexpected type for: " + def.getName());
    }

    public static boolean isSingleFileOutputProduct(OutputProductDefinition<?> def) throws MmtcException {
        if (def instanceof EntireFileOutputProductDefinition) {
            return false;
        }

        if (def instanceof AppendedFileOutputProductDefinition) {
            return true;
        }

        throw new MmtcException("Unexpected type for: " + def.getName());
    }

    public record OutputProductDef(String name, boolean builtIn, String simpleClassName, String type, boolean singleFile, List<String> filenames) { }
}
