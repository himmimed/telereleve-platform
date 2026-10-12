package ma.ceac.telereleve.reading;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import ma.ceac.telereleve.reading.ConsumptionCalculator.Point;
import ma.ceac.telereleve.reading.ReadingRepository.BucketIndexes;
import org.junit.jupiter.api.Test;

class ConsumptionCalculatorTest {

    private static final Instant H0 = Instant.parse("2026-10-12T00:00:00Z");

    private static Instant hour(int n) {
        return H0.plusSeconds(3600L * n);
    }

    private static BucketIndexes bucket(int hour, String first, String last) {
        return new BucketIndexes(hour(hour), new BigDecimal(first), new BigDecimal(last));
    }

    private static List<Instant> hours(int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(ConsumptionCalculatorTest::hour).toList();
    }

    private static List<String> values(List<Point> points) {
        return points.stream().map(p -> p.value().stripTrailingZeros().toPlainString()).toList();
    }

    @Test
    void consumptionIsTheDifferenceBetweenSuccessiveIndexes() {
        List<Point> points = ConsumptionCalculator.compute(new BigDecimal("100"),
                List.of(bucket(0, "100.5", "102"), bucket(1, "102.5", "105"), bucket(2, "105.2", "105.5")), hours(3));

        assertThat(values(points)).containsExactly("2", "3", "0.5");
    }

    @Test
    void withoutAnEarlierReadingTheFirstBucketStartsAtItsFirstIndex() {
        List<Point> points = ConsumptionCalculator.compute(null, List.of(bucket(0, "100", "103")), hours(1));

        assertThat(values(points)).containsExactly("3");
    }

    @Test
    void aGapGivesZeroThenTheNextBucketReceivesEverythingSinceTheLastKnownIndex() {
        // Readings stop after hour 0 and resume at hour 3.
        List<Point> points = ConsumptionCalculator.compute(new BigDecimal("100"),
                List.of(bucket(0, "100.5", "101"), bucket(3, "107", "108")), hours(4));

        assertThat(values(points)).containsExactly("1", "0", "0", "7");
        assertThat(points.stream().map(Point::value).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("8");
    }

    @Test
    void aDecreasingIndexNeverGivesANegativeValue() {
        // Meter replaced during hour 1: index restarts near 0.
        List<Point> points = ConsumptionCalculator.compute(new BigDecimal("500"),
                List.of(bucket(0, "500", "502"), bucket(1, "3", "4"), bucket(2, "4.5", "6")), hours(3));

        assertThat(values(points)).containsExactly("2", "0", "2");
    }

    @Test
    void returnsOnePointPerBucketEvenWithoutReadings() {
        List<Point> points = ConsumptionCalculator.compute(null, List.of(), hours(24));

        assertThat(points).hasSize(24).allMatch(p -> p.value().signum() == 0);
        assertThat(points.get(5).bucket()).isEqualTo(hour(5));
    }
}
