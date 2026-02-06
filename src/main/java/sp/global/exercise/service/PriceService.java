package sp.global.exercise.service;

import sp.global.exercise.model.BatchInfo;
import sp.global.exercise.model.PriceRecord;
import sp.global.exercise.model.UploadRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PriceService {

    UUID uploadAll(UploadRequest request);
    void cancel(UUID batchId);
    Map<String, PriceRecord> getLatest(List<String> ids);
    BatchInfo getBatchStatus(UUID batchId);
}

