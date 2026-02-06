package sp.global.exercise.model;

import java.time.Instant;

public record PriceRecord(
        String id,
        Instant asOf,
        Object payload
) {}

