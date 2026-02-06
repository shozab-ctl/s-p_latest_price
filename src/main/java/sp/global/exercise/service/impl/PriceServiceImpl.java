package sp.global.exercise.service.impl;

import org.springframework.stereotype.Service;
import sp.global.exercise.model.BatchInfo;
import sp.global.exercise.model.BatchStatus;
import sp.global.exercise.model.PriceRecord;
import sp.global.exercise.model.UploadRequest;
import sp.global.exercise.service.PriceService;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class PriceServiceImpl implements PriceService {

    private static final int CHUNK_SIZE = 1000;
    private static final Duration BATCH_TIMEOUT = Duration.ofSeconds(30);

    // Using for Atomic Search
    private final AtomicReference<Map<String, PriceRecord>> liveStore =
            new AtomicReference<>(new ConcurrentHashMap<>());

    // Batch tracking
    private final ConcurrentHashMap<UUID, BatchInfo> batchStatus = new ConcurrentHashMap<>();


    // ============================
    // API 1 — Upload ALL data
    // ============================
    @Override
    public UUID uploadAll(UploadRequest request) {

        UUID batchId = UUID.randomUUID();
        int totalRecords = request.prices().size();

        // Initialize batch status
        batchStatus.put(batchId, new BatchInfo(
                batchId,
                BatchStatus.IN_PROGRESS,
                Instant.now(),
                null,
                totalRecords,
                0,
                0.0
        ));

        ConcurrentHashMap<String, PriceRecord> staging = new ConcurrentHashMap<>();

        // Run processing asynchronously
        CompletableFuture.runAsync(() -> processBatch(batchId, request, staging));

        return batchId;
    }


    // ============================
    // Internal batch processor
    // ============================
    private void processBatch(UUID batchId,
                              UploadRequest request,
                              ConcurrentHashMap<String, PriceRecord> staging) {

        try {
            List<List<PriceRecord>> chunks = chunk(request.prices(), CHUNK_SIZE);

            AtomicInteger processed = new AtomicInteger(0);
            int total = request.prices().size();

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

                List<Future<?>> tasks = new ArrayList<>();

                for (var chunk : chunks) {
                    tasks.add(executor.submit(() -> {
                        for (var record : chunk) {

                            // Skip if batch cancelled
                            if (isCancelled(batchId)) return;

                            // Keep newest price by asOf
                            staging.merge(
                                    record.id(),
                                    record,
                                    (oldVal, newVal) ->
                                            newVal.asOf().isAfter(oldVal.asOf()) ? newVal : oldVal
                            );

                            // Update progress
                            int done = processed.incrementAndGet();
                            updateProgress(batchId, done, total);
                        }
                    }));
                }

                // Timeout guard
                long deadline = System.currentTimeMillis() + BATCH_TIMEOUT.toMillis();

                for (var task : tasks) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        throw new TimeoutException("Batch timed out");
                    }
                    task.get(remaining, TimeUnit.MILLISECONDS);
                }
            }

            // Skip commit if cancelled
            if (isCancelled(batchId)) return;

            // Atomic commit
            commit(staging);

            markCompleted(batchId, total);

        } catch (Exception e) {
            markFailed(batchId);
        }
    }


    // ============================
    // Atomic commit to live store
    // ============================
    private void commit(Map<String, PriceRecord> staging) {
        liveStore.updateAndGet(current -> {
            var newStore = new ConcurrentHashMap<>(current);

            for (var entry : staging.entrySet()) {
                newStore.merge(
                        entry.getKey(),
                        entry.getValue(),
                        (oldVal, newVal) ->
                                newVal.asOf().isAfter(oldVal.asOf()) ? newVal : oldVal
                );
            }

            return newStore;
        });
    }


    // ============================
    // API 2 — Cancel batch
    // ============================
    @Override
    public void cancel(UUID batchId) {
        batchStatus.computeIfPresent(batchId, (id, info) ->
                new BatchInfo(
                        id,
                        BatchStatus.CANCELLED,
                        info.startedAt(),
                        Instant.now(),
                        info.recordCount(),
                        info.processedCount(),
                        info.progressPercent()
                )
        );
    }


    // ============================
    // API 3 — Get latest prices
    // ============================
    @Override
    public Map<String, PriceRecord> getLatest(List<String> ids) {
        var snapshot = liveStore.get();
        Map<String, PriceRecord> result = new HashMap<>();

        for (var id : ids) {
            var price = snapshot.get(id);
            if (price != null) {
                result.put(id, price);
            }
        }
        return result;
    }


    // ============================
    // API 4 — Get batch status
    // ============================
    @Override
    public BatchInfo getBatchStatus(UUID batchId) {
        return batchStatus.get(batchId);
    }


    // ============================
    // Helpers
    // ============================

    private boolean isCancelled(UUID batchId) {
        var info = batchStatus.get(batchId);
        return info != null && info.status() == BatchStatus.CANCELLED;
    }

    private void updateProgress(UUID batchId, int processed, int total) {
        batchStatus.computeIfPresent(batchId, (id, info) ->
                new BatchInfo(
                        id,
                        BatchStatus.IN_PROGRESS,
                        info.startedAt(),
                        null,
                        info.recordCount(),
                        processed,
                        (processed * 100.0) / total
                )
        );
    }

    private void markCompleted(UUID batchId, int total) {
        batchStatus.computeIfPresent(batchId, (id, info) ->
                new BatchInfo(
                        id,
                        BatchStatus.COMPLETED,
                        info.startedAt(),
                        Instant.now(),
                        info.recordCount(),
                        total,
                        100.0
                )
        );
    }

    private void markFailed(UUID batchId) {
        batchStatus.computeIfPresent(batchId, (id, info) ->
                new BatchInfo(
                        id,
                        BatchStatus.FAILED,
                        info.startedAt(),
                        Instant.now(),
                        info.recordCount(),
                        info.processedCount(),
                        info.progressPercent()
                )
        );
    }

    private List<List<PriceRecord>> chunk(List<PriceRecord> list, int size) {
        List<List<PriceRecord>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            chunks.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return chunks;
    }
}


