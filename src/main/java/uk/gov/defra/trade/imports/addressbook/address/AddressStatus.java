package uk.gov.defra.trade.imports.operators.operator;

/**
 * Soft-delete tombstone status (cv-016). {@code DELETED} addresses are excluded from lists but
 * remain readable by id so a consumer can distinguish "deleted" (200 + DELETED) from "unknown"
 * (404). The wire never exposes this enum — a derived {@code deleted} boolean is emitted instead.
 */
public enum AddressStatus {
  ACTIVE,
  DELETED
}
