package com.orbisflow.requests.application;

import com.orbisflow.documents.persistence.DocumentRepository;
import com.orbisflow.documents.persistence.S3DocumentStore;
import com.orbisflow.integration.ai.FastApiExtractionClient;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ExtractionCoordinator {
    private final Executor executor;
    private final DocumentRepository documents;
    private final S3DocumentStore objectStore;
    private final FastApiExtractionClient client;
    private final ExtractionWorkflowService workflow;

    public ExtractionCoordinator(
            @Qualifier("extractionExecutor") Executor executor,
            DocumentRepository documents,
            S3DocumentStore objectStore,
            FastApiExtractionClient client,
            ExtractionWorkflowService workflow) {
        this.executor = executor;
        this.documents = documents;
        this.objectStore = objectStore;
        this.client = client;
        this.workflow = workflow;
    }

    public void start(UUID requestId, long attemptVersion, String correlationId) {
        UUID correlation = parseCorrelation(correlationId);
        try {
            executor.execute(() -> run(requestId, attemptVersion, correlation));
        } catch (RejectedExecutionException exception) {
            workflow.markSchedulingFailure(requestId, attemptVersion);
        }
    }

    private void run(UUID requestId, long attemptVersion, UUID correlationId) {
        var document = documents.findCurrentByRequestId(requestId).orElse(null);
        if (document == null) {
            workflow.markSchedulingFailure(requestId, attemptVersion);
            return;
        }
        try (var stored = objectStore.open(document.s3ObjectKey())) {
            byte[] content = stored.inputStream().readAllBytes();
            var result = client.extract(
                    requestId, document.mimeType(), content, correlationId);
            workflow.complete(requestId, attemptVersion, result);
        } catch (IOException | RuntimeException exception) {
            workflow.markSchedulingFailure(requestId, attemptVersion);
        }
    }

    private UUID parseCorrelation(String correlationId) {
        try {
            return UUID.fromString(correlationId);
        } catch (RuntimeException exception) {
            return UUID.randomUUID();
        }
    }
}
