package com.samharrison.payments.reporting.internal;

import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public final class ReportController {

    private static final MediaType CSV =
        MediaType.parseMediaType(
            "text/csv;charset=UTF-8"
        );

    private final OperationalSummaryService
        summaryService;

    private final ReportExportService exportService;

    public ReportController(
        OperationalSummaryService summaryService,
        ReportExportService exportService
    ) {
        this.summaryService =
            Objects.requireNonNull(
                summaryService,
                "summaryService must not be null"
            );
        this.exportService =
            Objects.requireNonNull(
                exportService,
                "exportService must not be null"
            );
    }

    @GetMapping(
        value = "/operational-summary",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
        summary = "Read operational report summary",
        description =
            "Returns role-scoped exact payment, "
                + "settlement and reconciliation "
                + "aggregates from one repeatable-read "
                + "snapshot."
    )
    public ResponseEntity<OperationalSummaryResponse>
        operationalSummary(
            @RequestParam Instant from,
            @RequestParam Instant to
        ) {
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(
                summaryService.summarize(
                    new ReportWindow(from, to)
                )
            );
    }

    @GetMapping(
        value = "/audit-events.csv",
        produces = "text/csv"
    )
    public ResponseEntity<byte[]> auditEvents(
        @RequestParam Instant from,
        @RequestParam Instant to
    ) {
        return csv(
            from,
            to,
            exportService::auditEvents
        );
    }

    @GetMapping(
        value = "/payments.csv",
        produces = "text/csv"
    )
    public ResponseEntity<byte[]> payments(
        @RequestParam Instant from,
        @RequestParam Instant to
    ) {
        return csv(
            from,
            to,
            exportService::payments
        );
    }

    @GetMapping(
        value = "/settlements.csv",
        produces = "text/csv"
    )
    public ResponseEntity<byte[]> settlements(
        @RequestParam Instant from,
        @RequestParam Instant to
    ) {
        return csv(
            from,
            to,
            exportService::settlements
        );
    }

    @GetMapping(
        value = "/reconciliation.csv",
        produces = "text/csv"
    )
    public ResponseEntity<byte[]> reconciliation(
        @RequestParam Instant from,
        @RequestParam Instant to
    ) {
        return csv(
            from,
            to,
            exportService::reconciliation
        );
    }

    private static ResponseEntity<byte[]> csv(
        Instant from,
        Instant to,
        Function<ReportWindow, CsvReport> exporter
    ) {
        CsvReport report =
            exporter.apply(
                new ReportWindow(from, to)
            );

        return ResponseEntity
            .ok()
            .contentType(CSV)
            .cacheControl(CacheControl.noStore())
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\""
                    + report.filename()
                    + "\""
            )
            .header(
                "X-Content-Type-Options",
                "nosniff"
            )
            .body(report.content());
    }
}
