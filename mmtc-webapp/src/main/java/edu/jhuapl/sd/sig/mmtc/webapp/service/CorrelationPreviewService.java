package edu.jhuapl.sd.sig.mmtc.webapp.service;

import edu.jhuapl.sd.sig.mmtc.webapp.TimeCorrelationWebAppConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CorrelationPreviewService {
    private final Map<UUID, CorrelationPreview> correlationPreviews = new ConcurrentHashMap<>();

    public record CorrelationPreview (
            UUID uuid,
            TimeCorrelationWebAppConfig.CorrelationConfig correlationConfig,
            Path tempSclkKernelOnDisk
    ) { }

    public UUID registerCorrelationPreview(TimeCorrelationWebAppConfig.CorrelationConfig correlationConfig, Path pathToPreviewSclkKernel) {
        final UUID newPreviewId = UUID.randomUUID();
        correlationPreviews.put(newPreviewId, new CorrelationPreview(newPreviewId, correlationConfig, pathToPreviewSclkKernel));
        return newPreviewId;
    }

    public void removeCorrelationPreview(UUID previewId) throws IOException {
        Files.delete(correlationPreviews.remove(previewId).tempSclkKernelOnDisk);
    }

    public CorrelationPreview get(UUID previewId) {
        return correlationPreviews.get(previewId);
    }
}
