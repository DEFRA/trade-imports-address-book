package uk.gov.defra.trade.imports.operators.operator;

/**
 * Soft-delete tombstone status (c-003). {@code DELETED} operators are excluded from lists but
 * remain readable by id so a consumer can distinguish "deleted" (200 + DELETED) from "unknown"
 * (404).
 */
public enum OperatorStatus {
  ACTIVE,
  DELETED
}
