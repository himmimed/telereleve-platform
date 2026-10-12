package ma.ceac.telereleve.meter;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import ma.ceac.telereleve.meter.dto.MeterRequest;

/**
 * Generates a fictitious but plausible meter park around Fès for demos and load tests.
 * Deterministic: the same count always gives the same serials, names and positions, so the
 * simulator and the database agree, and serial N always keeps the same type even when the park grows.
 * All client names are invented.
 */
public final class FesMeterGenerator {

    private record Quarter(String name, double lat, double lng) {
    }

    private static final List<Quarter> QUARTERS = List.of(
            new Quarter("Agdal", 34.030, -5.005),
            new Quarter("Ville Nouvelle", 34.037, -4.999),
            new Quarter("Narjiss", 34.010, -4.985),
            new Quarter("Saïss", 34.000, -5.020),
            new Quarter("Zouagha", 34.042, -5.045),
            new Quarter("Aïn Chkef", 33.990, -5.005),
            new Quarter("Route d’Imouzzer", 34.018, -5.010),
            new Quarter("Bensouda", 34.045, -5.065),
            new Quarter("Atlas", 34.025, -4.980),
            new Quarter("Fès Jdid", 34.056, -4.990),
            new Quarter("Montfleuri", 34.022, -4.970),
            new Quarter("Médina", 34.064, -4.973));

    private static final List<String> STREETS = List.of(
            "Avenue Hassan II", "Rue Ibn Khaldoun", "Boulevard Allal El Fassi", "Rue Moulay Slimane",
            "Avenue des FAR", "Rue Ibn Sina", "Rue Abou Bakr Seddik", "Avenue Mohammed V",
            "Rue Oued Fès", "Rue des Orangers");

    private static final List<String> FAMILY_NAMES = List.of(
            "Alaoui", "Benjelloun", "Tazi", "Berrada", "El Idrissi", "Bennis", "Chraibi", "Lahlou",
            "Sqalli", "Kettani", "Lazrak", "Bouzidi", "Amrani", "Ouazzani", "Filali", "Hajji",
            "Mernissi", "Saadi", "Zerhouni", "Naciri", "Bekkali", "Cherkaoui", "Jabri", "Rami",
            "Guessous", "Benkirane", "Mekouar", "Slaoui", "Tahiri", "Ziani", "Belkhayat", "Hassani");

    // Not tied to a neighbourhood name, so a business never appears in the wrong part of town.
    private static final List<String> BUSINESSES = List.of(
            "Boulangerie Al Baraka", "Pressing Ennour", "Épicerie Assafa", "Cabinet dentaire Ibn Sina",
            "Atelier de couture Assalam", "Résidence Les Orangers", "Menuiserie Al Amal", "Hammam Ennakhil",
            "Salon de thé Les Jardins", "Clinique vétérinaire Al Wafa", "Supérette Al Khayr", "Imprimerie Annour");

    private FesMeterGenerator() {
    }

    /** Serial of the n-th meter (1-based). */
    public static String serial(int n, MeterType type) {
        return "CEAC-%c-%06d".formatted(type.serialLetter(), n);
    }

    /**
     * Type of the n-th meter (1-based) for a given share of water meters: meter n is a water meter when
     * floor(n * share) increases, which spreads water meters evenly and gives exactly
     * floor(count * share) of them.
     */
    public static MeterType typeOf(int n, double waterShare) {
        return Math.floor(n * waterShare) > Math.floor((n - 1) * waterShare) ? MeterType.WATER : MeterType.ELECTRICITY;
    }

    public static List<MeterRequest> generate(int count, double waterShare) {
        List<MeterRequest> meters = new ArrayList<>(count);
        for (int n = 1; n <= count; n++) {
            // One seeded random per meter: meter n is identical whatever the total count.
            Random random = new Random(20_261_011L * 31 + n);
            MeterType type = typeOf(n, waterShare);
            Quarter quarter = QUARTERS.get((n - 1) % QUARTERS.size());
            boolean business = n % 4 == 2;
            String clientName = business
                    ? BUSINESSES.get(random.nextInt(BUSINESSES.size()))
                    : "Foyer " + FAMILY_NAMES.get(random.nextInt(FAMILY_NAMES.size()));
            String address = "%d, %s, %s, Fès".formatted(
                    1 + random.nextInt(180), STREETS.get(random.nextInt(STREETS.size())), quarter.name());
            double lat = round6(quarter.lat() + (random.nextDouble() - 0.5) * 0.014);
            double lng = round6(quarter.lng() + (random.nextDouble() - 0.5) * 0.016);
            meters.add(new MeterRequest(serial(n, type), type, clientName, address, lat, lng, 15, MeterStatus.ACTIVE));
        }
        return meters;
    }

    private static double round6(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }
}
