package ma.ceac.telereleve.meter;

import java.util.UUID;
import ma.ceac.telereleve.common.ConflictException;
import ma.ceac.telereleve.common.NotFoundException;
import ma.ceac.telereleve.meter.dto.MeterRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MeterService {

    private final MeterRepository meters;

    public MeterService(MeterRepository meters) {
        this.meters = meters;
    }

    public Page<Meter> search(MeterType type, MeterStatus status, String search, Pageable pageable) {
        return meters.findAll(MeterSpecifications.matching(type, status, search), pageable);
    }

    public Meter get(UUID id) {
        return meters.findById(id).orElseThrow(() -> new NotFoundException("Ce compteur n’existe pas."));
    }

    @Transactional
    public Meter create(MeterRequest request) {
        if (meters.existsBySerial(request.serial())) {
            throw duplicateSerial(request.serial());
        }
        Meter meter = new Meter(request.serial(), request.type(), request.clientName().trim(), request.address().trim(),
                request.latitude(), request.longitude(), request.readingIntervalMin(), request.status());
        return meters.save(meter);
    }

    @Transactional
    public Meter update(UUID id, MeterRequest request) {
        Meter meter = get(id);
        if (meters.existsBySerialAndIdNot(request.serial(), id)) {
            throw duplicateSerial(request.serial());
        }
        meter.update(request.serial(), request.type(), request.clientName().trim(), request.address().trim(),
                request.latitude(), request.longitude(), request.readingIntervalMin(), request.status());
        return meter;
    }

    private static ConflictException duplicateSerial(String serial) {
        return new ConflictException("Le numéro de série " + serial + " existe déjà.");
    }
}
