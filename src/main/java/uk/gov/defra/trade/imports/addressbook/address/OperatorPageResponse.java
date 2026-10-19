package uk.gov.defra.trade.imports.addressbook.address;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Top-level list response, never a bare array. Wire fields are {@code items}, {@code page},
 * {@code pageSize}, {@code totalItems} and {@code totalPages}.
 *
 * <p>{@code items} is copied and never null.
 */
@Schema(description = "Paginated list of ACTIVE addresses (never a bare array)")
public record OperatorPageResponse(
    @Schema(
            description = "Addresses on this page",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<OperatorResponse> items,
    @Schema(
            description = "Current 1-based page number",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minimum = "1")
        int page,
    @Schema(
            description = "Server-configured page size",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minimum = "1")
        int pageSize,
    @Schema(
            description = "Total number of matching addresses",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minimum = "0")
        int totalItems,
    @Schema(
            description = "Total number of pages",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minimum = "0")
        int totalPages) {

  public OperatorPageResponse {
    items = List.copyOf(items);
  }
}
