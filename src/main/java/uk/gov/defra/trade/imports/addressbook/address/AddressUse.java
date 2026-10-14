package uk.gov.defra.trade.imports.addressbook.address;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The roles an address book record can be offered for in a notification's address pickers. The wire
 * value of each constant is its name.
 */
@Schema(description = "A use an address book record can be put to")
public enum AddressUse {
  @Schema(description = "Where the consignment begins its journey")
  PLACE_OF_ORIGIN,
  @Schema(description = "The sender of the consignment")
  CONSIGNOR,
  @Schema(description = "Where the consignment is unloaded")
  PLACE_OF_DESTINATION,
  @Schema(description = "The receiver of the consignment")
  CONSIGNEE,
  @Schema(description = "The importer of the consignment")
  IMPORTER,
  @Schema(description = "A branch address of the trader's own business")
  BRANCH_ADDRESS
}
