package sp.global.exercise.model;

import java.time.Instant;
import java.util.UUID;

public record BatchInfo(
        UUID batchId,
        BatchStatus status,
        Instant startedAt,
        Instant completedAt,
        int recordCount,
        int processedCount,
        double progressPercent
) {}


