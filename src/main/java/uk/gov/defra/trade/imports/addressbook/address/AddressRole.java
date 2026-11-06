package uk.gov.defra.trade.imports.addressbook.address;

import io.swagger.v3.oas.annotations.media.Schema;

/** A role a notification can choose this address for: a party, or a place. */
@Schema(
    description = "A role a notification can choose this address for: a party, or a place",
    enumAsRef = true)
public enum AddressRole {
  @Schema(description = "Where the animals or goods start their journey")
  PLACE_OF_ORIGIN(RoleGroup.SENDING),
  @Schema(description = "The party sending the consignment")
  CONSIGNOR(RoleGroup.SENDING),
  @Schema(description = "Where the animals or goods are unloaded in Great Britain")
  PLACE_OF_DESTINATION(RoleGroup.RECEIVING),
  @Schema(description = "The party receiving the consignment")
  CONSIGNEE(RoleGroup.RECEIVING),
  @Schema(description = "The party importing the consignment")
  IMPORTER(RoleGroup.RECEIVING),
  @Schema(description = "The trader's own contact address")
  BRANCH_ADDRESS(RoleGroup.BRANCH);

  private final RoleGroup group;

  AddressRole(RoleGroup group) {
    this.group = group;
  }

  RoleGroup group() {
    return group;
  }
}
