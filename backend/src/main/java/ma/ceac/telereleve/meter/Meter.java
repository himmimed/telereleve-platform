package ma.ceac.telereleve.meter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "meter")
public class Meter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String serial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeterType type;

    @Column(name = "client_name", nullable = false, length = 120)
    private String clientName;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "reading_interval_min", nullable = false)
    private int readingIntervalMin = 15;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeterStatus status = MeterStatus.ACTIVE;

    @Column(name = "last_reading_at")
    private Instant lastReadingAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Meter() {
        // for JPA
    }

    public Meter(String serial, MeterType type, String clientName, String address,
                 double latitude, double longitude, int readingIntervalMin, MeterStatus status) {
        this.serial = serial;
        this.type = type;
        this.clientName = clientName;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.readingIntervalMin = readingIntervalMin;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** Replaces every editable field (PUT semantics). */
    public void update(String serial, MeterType type, String clientName, String address,
                       double latitude, double longitude, int readingIntervalMin, MeterStatus status) {
        this.serial = serial;
        this.type = type;
        this.clientName = clientName;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.readingIntervalMin = readingIntervalMin;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public String getSerial() {
        return serial;
    }

    public MeterType getType() {
        return type;
    }

    public String getClientName() {
        return clientName;
    }

    public String getAddress() {
        return address;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public int getReadingIntervalMin() {
        return readingIntervalMin;
    }

    public MeterStatus getStatus() {
        return status;
    }

    public Instant getLastReadingAt() {
        return lastReadingAt;
    }

    public void setLastReadingAt(Instant lastReadingAt) {
        this.lastReadingAt = lastReadingAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
