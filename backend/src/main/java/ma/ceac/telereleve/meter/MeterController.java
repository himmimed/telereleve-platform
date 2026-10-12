package ma.ceac.telereleve.meter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import ma.ceac.telereleve.common.PageResponse;
import ma.ceac.telereleve.meter.dto.MeterDetailsResponse;
import ma.ceac.telereleve.meter.dto.MeterRequest;
import ma.ceac.telereleve.meter.dto.MeterResponse;
import ma.ceac.telereleve.reading.ReadingQueryService;
import ma.ceac.telereleve.reading.dto.ReadingResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/meters")
@Tag(name = "Compteurs", description = "Parc de compteurs d'électricité et d'eau")
public class MeterController {

    private final MeterService meterService;
    private final ReadingQueryService readingQueries;

    public MeterController(MeterService meterService, ReadingQueryService readingQueries) {
        this.meterService = meterService;
        this.readingQueries = readingQueries;
    }

    @GetMapping
    @Operation(summary = "List meters", description = "Filters are optional and combined. Sorted by serial number.")
    public PageResponse<MeterResponse> search(
            @RequestParam(required = false) MeterType type,
            @RequestParam(required = false) MeterStatus status,
            @Parameter(description = "Searched in serial number, client name and address")
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, at most 1000")
            @RequestParam(defaultValue = "20") @Min(1) @Max(1000) int size) {
        var pageable = PageRequest.of(page, size, Sort.by("serial"));
        return PageResponse.of(meterService.search(type, status, search, pageable), MeterResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one meter with its last reading")
    public MeterDetailsResponse get(@PathVariable UUID id) {
        Meter meter = meterService.get(id);
        return new MeterDetailsResponse(MeterResponse.from(meter),
                readingQueries.latest(id).map(ReadingResponse::from).orElse(null));
    }

    @PostMapping
    @Operation(summary = "Register a meter")
    public ResponseEntity<MeterResponse> create(@Valid @RequestBody MeterRequest request) {
        Meter meter = meterService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(meter.getId()).toUri();
        return ResponseEntity.created(location).body(MeterResponse.from(meter));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a meter")
    public MeterResponse update(@PathVariable UUID id, @Valid @RequestBody MeterRequest request) {
        return MeterResponse.from(meterService.update(id, request));
    }
}
