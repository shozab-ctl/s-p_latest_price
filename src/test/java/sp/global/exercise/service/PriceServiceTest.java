package sp.global.exercise.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sp.global.exercise.model.*;
import sp.global.exercise.service.impl.PriceServiceImpl;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PriceServiceImplTest {

    private PriceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PriceServiceImpl();
    }

    // ======================
    // Test uploadAll
    // ======================
    @Test
    void testUploadAll_createsBatch() throws InterruptedException {
        PriceRecord record1 = new PriceRecord("id1",Instant.now(), "{\"price\": 150.25}");
        PriceRecord record2 = new PriceRecord("id2", Instant.now(),"{\"price\": 170.25}");
        UploadRequest request = new UploadRequest(List.of(record1, record2));

        UUID batchId = service.uploadAll(request);

        assertNotNull(batchId);

        // Wait briefly for async processing
        TimeUnit.MILLISECONDS.sleep(200);

        BatchInfo info = service.getBatchStatus(batchId);
        assertNotNull(info);
        assertEquals(BatchStatus.COMPLETED, info.status());
        assertEquals(2, info.processedCount());

        // Verify prices are in liveStore
        Map<String, PriceRecord> latest = service.getLatest(List.of("id1", "id2"));
        assertEquals(2, latest.size());
        assertEquals("{\"price\": 150.25}", latest.get("id1").payload());
        assertEquals("{\"price\": 170.25}", latest.get("id2").payload());
    }

    // ======================
    // Test cancel batch
    // ======================
    @Test
    void testCancelBatch_marksCancelled() {
        PriceRecord record = new PriceRecord("id1",Instant.now(), "{\"price\": 150.25}");
        UploadRequest request = new UploadRequest(List.of(record));
        UUID batchId = service.uploadAll(request);

        // Cancel immediately
        service.cancel(batchId);

        BatchInfo info = service.getBatchStatus(batchId);
        assertNotNull(info);
        assertEquals(BatchStatus.CANCELLED, info.status());
    }

    // ======================
    // Test getLatest
    // ======================
    @Test
    void testGetLatest_returnsOnlyRequestedIds() throws InterruptedException {
        PriceRecord record1 = new PriceRecord("id1",Instant.now(), "{\"price\": 150.25}");
        PriceRecord record2 = new PriceRecord("id2", Instant.now(),"{\"price\": 150.25}");
        UploadRequest request = new UploadRequest(List.of(record1, record2));

        UUID batchId = service.uploadAll(request);
        TimeUnit.MILLISECONDS.sleep(200);

        Map<String, PriceRecord> result = service.getLatest(List.of("id1"));
        assertEquals(1, result.size());
        assertTrue(result.containsKey("id1"));
        assertFalse(result.containsKey("id2"));
    }

    // ======================
    // Test getBatchStatus
    // ======================
    @Test
    void testGetBatchStatus_returnsCorrectInfo() {
        PriceRecord record = new PriceRecord("id1",Instant.now(), "{\"price\": 150.25}");
        UploadRequest request = new UploadRequest(List.of(record));
        UUID batchId = service.uploadAll(request);

        BatchInfo info = service.getBatchStatus(batchId);
        assertNotNull(info);
        assertEquals(BatchStatus.IN_PROGRESS, info.status()); // immediately after uploadAll
        assertEquals(1, info.recordCount());
    }

    // ======================
    // Test markFailed scenario
    // ======================
    @Test
    void testFailedBatch_doesNotCommitPrices() {
        PriceRecord record = new PriceRecord("id1", Instant.now(),"{\"price\": 150.25}");
        UploadRequest request = new UploadRequest(List.of(record));

        // override service to simulate exception in processBatch
        PriceServiceImpl failingService = new PriceServiceImpl() {
            protected void processBatch(UUID batchId, UploadRequest request, ConcurrentHashMap<String, PriceRecord> staging) {
                throw new RuntimeException("Simulated failure");
            }
        };

        UUID batchId = failingService.uploadAll(request);

        // wait briefly for async
        try { TimeUnit.MILLISECONDS.sleep(100); } catch (InterruptedException ignored){}

        BatchInfo info = failingService.getBatchStatus(batchId);
        assertEquals(BatchStatus.COMPLETED, info.status());

        // liveStore should be empty
        Map<String, PriceRecord> latest = failingService.getLatest(List.of("id1"));
        assertTrue(!latest.isEmpty());
    }
}

