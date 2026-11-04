package uk.gov.defra.trade.imports.addressbook.address;

import java.util.List;

/** The kind of address an entry is, and the uses a listing by that type returns. */
public enum AddressType {
  ORIGIN_OR_CONSIGNOR(List.of(AddressUse.PLACE_OF_ORIGIN, AddressUse.CONSIGNOR)),
  DESTINATION_CONSIGNEE_OR_IMPORTER(
      List.of(AddressUse.PLACE_OF_DESTINATION, AddressUse.CONSIGNEE, AddressUse.IMPORTER)),
  BRANCH(List.of(AddressUse.BRANCH_ADDRESS));

  private final List<AddressUse> uses;

  AddressType(List<AddressUse> uses) {
    this.uses = uses;
  }

  /** The uses this type lists. */
  public List<AddressUse> uses() {
    return uses;
  }

  /** Whether this type also lists an address whose uses are missing, null or empty. */
  public boolean listsAddressWithNoUses() {
    return this == ORIGIN_OR_CONSIGNOR;
  }
}
