package sp.global.exercise.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.global.exercise.model.BatchInfo;
import sp.global.exercise.model.PriceRecord;
import sp.global.exercise.model.UploadRequest;
import sp.global.exercise.service.PriceService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/prices")
@AllArgsConstructor
@Slf4j
public class PriceController {

    private final PriceService service;

    /**
     * This API will break the data into chunks and upload the data async.
     * If the data was sent in batches then I shall create API to upload the batch asynchronously.
     * For simpler use I have made this assumption that the data will be in bulk. Although it is taking time from postman to hit the API.
     * @param request
     * @return
     */
    @PostMapping("/upload")
    public ResponseEntity<UUID> upload(@RequestBody UploadRequest request) {
        try {
            UUID batchId = service.uploadAll(request);
            return ResponseEntity.ok(batchId);
        } catch (Exception e) {
            log.error("Error while uploading data", e);
            return ResponseEntity.status(500)
                    .body(null);
        }
    }

    /**
     * This API will cancel the running batch
     * @param batchId
     */
    @PostMapping("/cancel/{batchId}")
    public ResponseEntity<String> cancel(@PathVariable UUID batchId) {
        try {
            service.cancel(batchId);
            return ResponseEntity.ok("Batch cancelled successfully");
        } catch (Exception e) {
            log.error("Error while cancelling", e);
            return ResponseEntity.status(500)
                    .body("Failed to cancel batch: " + e.getMessage());
        }
    }

    /**
     * This API will retrieve the latest prices
     * @param ids
     * @return
     */
    @PostMapping("/latest")
    public ResponseEntity<Map<String, PriceRecord>> latest(@RequestBody List<String> ids) {
        try {
            Map<String, PriceRecord> prices = service.getLatest(ids);
            if (prices.isEmpty()) {
                // No data available (e.g., batch still in progress)
                return ResponseEntity.noContent().build();
            }

            return ResponseEntity.ok(prices);
        } catch (Exception e) {
            log.error("Error while fetching price", e);
            return ResponseEntity.status(500)
                    .body(null);
        }
    }

    /**
     * This API will show the status of the batch with completion %.
     * @param batchId
     * @return
     */
    @GetMapping("/batch/{batchId}/status")
    public ResponseEntity<BatchInfo> status(@PathVariable UUID batchId) {
        try {
            BatchInfo batchInfo = service.getBatchStatus(batchId);

            if (batchInfo == null) {
                // Batch not found
                return ResponseEntity.notFound().build();
            }

            // Batch found
            return ResponseEntity.ok(batchInfo);
        } catch (Exception e) {
            log.error("Error while fetching status", e);
            return ResponseEntity.status(500).build();
        }
    }

}

