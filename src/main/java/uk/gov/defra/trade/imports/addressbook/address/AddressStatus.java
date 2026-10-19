package uk.gov.defra.trade.imports.addressbook.address;

/**
 * Soft-delete tombstone status. {@code DELETED} addresses are excluded from lists but remain
 * readable by id. The enum is never on the wire.
 */
public enum AddressStatus {
  ACTIVE,
  DELETED
}
