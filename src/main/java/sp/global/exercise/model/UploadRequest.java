package sp.global.exercise.model;

import java.util.List;

public record UploadRequest(
        List<PriceRecord> prices
) {}


