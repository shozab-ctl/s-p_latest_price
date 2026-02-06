package sp.global.exercise.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.ResponseEntity;
import sp.global.exercise.model.*;
import sp.global.exercise.service.PriceService;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PriceControllerTest {

    @Mock
    private PriceService service;

    @InjectMocks
    private PriceController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ======================
    // Test /upload endpoint
    // ======================
    @Test
    void testUpload_Success() {
        UploadRequest request = new UploadRequest(Collections.emptyList());
        UUID batchId = UUID.randomUUID();
        when(service.uploadAll(request)).thenReturn(batchId);

        ResponseEntity<UUID> response = controller.upload(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(batchId, response.getBody());
    }

    @Test
    void testUpload_Failure() {
        UploadRequest request = new UploadRequest(Collections.emptyList());
        when(service.uploadAll(request)).thenThrow(new RuntimeException("Upload failed"));

        ResponseEntity<UUID> response = controller.upload(request);

        assertEquals(500, response.getStatusCode().value());
        assertNull(response.getBody());
    }

    // ======================
    // Test /cancel endpoint
    // ======================
    @Test
    void testCancel_Success() {
        UUID batchId = UUID.randomUUID();

        doNothing().when(service).cancel(batchId);

        ResponseEntity<String> response = controller.cancel(batchId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Batch cancelled successfully", response.getBody());
    }

    @Test
    void testCancel_Failure() {
        UUID batchId = UUID.randomUUID();

        doThrow(new RuntimeException("Cancel failed")).when(service).cancel(batchId);

        ResponseEntity<String> response = controller.cancel(batchId);

        assertEquals(500, response.getStatusCode().value());
        assertTrue(response.getBody().contains("Failed to cancel batch"));
    }

    // ======================
    // Test /latest endpoint
    // ======================
    @Test
    void testLatest_SuccessWithData() {
        List<String> ids = List.of("id1", "id2");
        Map<String, PriceRecord> prices = new HashMap<>();
        prices.put("id1", new PriceRecord("id1", Instant.ofEpochSecond(100), new Date().toInstant()));
        when(service.getLatest(ids)).thenReturn(prices);

        ResponseEntity<Map<String, PriceRecord>> response = controller.latest(ids);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(prices, response.getBody());
    }

    @Test
    void testLatest_NoData() {
        List<String> ids = List.of("id1", "id2");
        when(service.getLatest(ids)).thenReturn(Collections.emptyMap());

        ResponseEntity<Map<String, PriceRecord>> response = controller.latest(ids);

        assertEquals(204, response.getStatusCode().value()); // No Content
        assertNull(response.getBody());
    }

    @Test
    void testLatest_Failure() {
        List<String> ids = List.of("id1");
        when(service.getLatest(ids)).thenThrow(new RuntimeException("Fetch failed"));

        ResponseEntity<Map<String, PriceRecord>> response = controller.latest(ids);

        assertEquals(500, response.getStatusCode().value());
        assertNull(response.getBody());
    }

    // ======================
    // Test /batch/{id}/status endpoint
    // ======================
    @Test
    void testStatus_Success() {
        UUID batchId = UUID.randomUUID();
        BatchInfo batchInfo = new BatchInfo(batchId, BatchStatus.COMPLETED,
                new Date().toInstant(), new Date().toInstant(),
                10, 10, 100.0);
        when(service.getBatchStatus(batchId)).thenReturn(batchInfo);

        ResponseEntity<BatchInfo> response = controller.status(batchId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(batchInfo, response.getBody());
    }

    @Test
    void testStatus_NotFound() {
        UUID batchId = UUID.randomUUID();
        when(service.getBatchStatus(batchId)).thenReturn(null);

        ResponseEntity<BatchInfo> response = controller.status(batchId);

        assertEquals(404, response.getStatusCode().value());
        assertNull(response.getBody());
    }

    @Test
    void testStatus_Failure() {
        UUID batchId = UUID.randomUUID();
        when(service.getBatchStatus(batchId)).thenThrow(new RuntimeException("Status failed"));

        ResponseEntity<BatchInfo> response = controller.status(batchId);

        assertEquals(500, response.getStatusCode().value());
        assertNull(response.getBody());
    }
}

