package uk.gov.defra.trade.imports.addressbook.address;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Top-level list response (rest-api.md — never a bare array). Wire fields are {@code items},
 * {@code page}, {@code pageSize}, {@code totalItems}, {@code totalPages} (camelCase via the global
 * naming strategy). The list/search increment (inc-009/inc-010) populates it from a
 * {@code Page<Operator>}.
 *
 * <p>{@code items} is null-guarded (service-boundary rule); the counts are primitives and cannot be
 * null.
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
            description = "Server-configured page size (cv-025)",
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
