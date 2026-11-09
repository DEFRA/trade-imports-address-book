package uk.gov.defra.trade.imports.addressbook.transporter;

/**
 * Soft-delete tombstone status. {@code DELETED} transporters are excluded from lists but remain
 * readable by id. The enum is never on the wire.
 */
public enum TransporterStatus {
  ACTIVE,
  DELETED
}
