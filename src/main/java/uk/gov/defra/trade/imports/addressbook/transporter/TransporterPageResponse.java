package uk.gov.defra.trade.imports.addressbook.transporter;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** {@code items} is copied and never null. */
@Schema(description = "Paginated list of live transporters (never a bare array)")
public record TransporterPageResponse(
    @Schema(
            description = "Transporters on this page",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<TransporterResponse> items,
    @Schema(
            description = "Current 1-based page number",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minimum = "1")
        int page,
    @Schema(
            description = "Page size served: the requested one, else the default",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minimum = "1")
        int pageSize,
    @Schema(
            description = "Total number of matching transporters",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minimum = "0")
        int totalItems,
    @Schema(
            description = "Total number of pages",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minimum = "0")
        int totalPages) {

  public TransporterPageResponse {
    items = List.copyOf(items);
  }
}
