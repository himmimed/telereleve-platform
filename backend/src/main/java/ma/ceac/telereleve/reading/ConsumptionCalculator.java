package ma.ceac.telereleve.reading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import ma.ceac.telereleve.reading.ReadingRepository.BucketIndexes;

/**
 * Consumption = difference between successive indexes, per time bucket. Pure function.
 *
 * - Each bucket consumes "its last index - the last index known before it". The reference before the
 *   first bucket is the last reading before the period (openingIndex), or the first index of the first
 *   non-empty bucket when the meter has no earlier reading.
 * - Gaps: a bucket without readings consumes 0; the next bucket with readings receives everything
 *   consumed since the last known index (the consumption really happened, only its timing is unknown).
 * - An index that goes down (meter replaced, fraud: rule A4) never produces a negative value: that
 *   difference counts as 0 and the lower index becomes the new reference.
 */
public final class ConsumptionCalculator {

    private ConsumptionCalculator() {
    }

    public record Point(Instant bucket, BigDecimal value) {
    }

    /**
     * @param openingIndex last index before the period, or null if none
     * @param indexes      first / last index of every non-empty bucket
     * @param buckets      every bucket of the period, in order (empty ones included)
     */
    public static List<Point> compute(BigDecimal openingIndex, List<BucketIndexes> indexes, List<Instant> buckets) {
        Map<Instant, BucketIndexes> byBucket = indexes.stream()
                .collect(Collectors.toMap(BucketIndexes::bucket, Function.identity()));
        List<Point> points = new ArrayList<>(buckets.size());
        BigDecimal reference = openingIndex;
        for (Instant bucket : buckets) {
            BucketIndexes bucketIndexes = byBucket.get(bucket);
            if (bucketIndexes == null) {
                points.add(new Point(bucket, BigDecimal.ZERO));
                continue;
            }
            BigDecimal start = reference != null ? reference : bucketIndexes.firstIndex();
            BigDecimal consumed = bucketIndexes.lastIndex().subtract(start);
            points.add(new Point(bucket, consumed.signum() < 0 ? BigDecimal.ZERO : consumed));
            reference = bucketIndexes.lastIndex();
        }
        return points;
    }
}
