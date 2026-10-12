package ma.ceac.telereleve.reading;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read access to readings for other features (meter details, anomaly rules). */
@Service
@Transactional(readOnly = true)
public class ReadingQueryService {

    private final ReadingRepository readings;

    public ReadingQueryService(ReadingRepository readings) {
        this.readings = readings;
    }

    public Optional<StoredReading> latest(UUID meterId) {
        return readings.findLatest(meterId);
    }
}
