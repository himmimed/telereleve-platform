package ma.ceac.telereleve.simulator;

/** A meter of the park, as returned by GET /api/meters. */
public record SimulatedMeter(String serial, MeterType type) {

    public enum MeterType {
        ELECTRICITY("kWh"),
        WATER("m3");

        private final String unit;

        MeterType(String unit) {
            this.unit = unit;
        }

        public String unit() {
            return unit;
        }
    }

    public String topic() {
        return "ceac/meters/" + serial + "/readings";
    }
}
