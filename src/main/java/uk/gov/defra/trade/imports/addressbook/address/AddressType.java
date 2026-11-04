package uk.gov.defra.trade.imports.addressbook.address;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The kind of address an entry is, and a group of uses. An address is listed under a type when it
 * holds one of the type's uses, whatever address type it was saved with; any use may be saved with
 * any type.
 */
@Schema(
    description =
        "The kind of address an entry is, and a group of uses. An address is listed under a type"
            + " when it holds one of the type's uses, whatever address type it was saved with; any"
            + " use may be saved with any type")
public enum AddressType {
  @Schema(
      description =
          "Origin or consignor: place of origin and consignor. Also lists an address with no uses")
  ORIGIN_OR_CONSIGNOR(List.of(AddressUse.PLACE_OF_ORIGIN, AddressUse.CONSIGNOR)),
  @Schema(
      description =
          "Destination, consignee or importer: place of destination, consignee and importer")
  DESTINATION_CONSIGNEE_OR_IMPORTER(
      List.of(AddressUse.PLACE_OF_DESTINATION, AddressUse.CONSIGNEE, AddressUse.IMPORTER)),
  @Schema(description = "Branch: branch address")
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
