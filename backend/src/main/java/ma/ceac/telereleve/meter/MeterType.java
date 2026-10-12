package ma.ceac.telereleve.meter;

/** Energy measured by a meter. The serial prefix follows it: CEAC-E-... or CEAC-W-... */
public enum MeterType {
    ELECTRICITY('E', "kWh"),
    WATER('W', "m3");

    private final char serialLetter;
    private final String unit;

    MeterType(char serialLetter, String unit) {
        this.serialLetter = serialLetter;
        this.unit = unit;
    }

    public char serialLetter() {
        return serialLetter;
    }

    public String unit() {
        return unit;
    }
}
