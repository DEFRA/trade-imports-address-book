package uk.gov.defra.trade.imports.operators.operator;

import java.util.List;
import java.util.Objects;

/**
 * Top-level list response (rest-api.md — never a bare array). Wire fields are {@code items},
 * {@code page}, {@code pageSize}, {@code totalItems}, {@code totalPages} (camelCase via the global
 * naming strategy). The list/search increment (inc-009/inc-010) populates it from a
 * {@code Page<Operator>}.
 *
 * <p>{@code items} is null-guarded (service-boundary rule); the counts are primitives and cannot be
 * null.
 */
public record OperatorPageResponse(
    List<OperatorResponse> items, int page, int pageSize, int totalItems, int totalPages) {

  public OperatorPageResponse {
    Objects.requireNonNull(items, "items");
  }
}
