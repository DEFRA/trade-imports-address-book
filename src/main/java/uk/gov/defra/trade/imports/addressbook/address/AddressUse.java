package uk.gov.defra.trade.imports.addressbook.address;

import io.swagger.v3.oas.annotations.media.Schema;

/** What an address can be used for. */
@Schema(description = "What an address can be used for")
public enum AddressUse {
  @Schema(description = "Place of origin")
  PLACE_OF_ORIGIN,
  @Schema(description = "Consignor")
  CONSIGNOR,
  @Schema(description = "Place of destination")
  PLACE_OF_DESTINATION,
  @Schema(description = "Consignee")
  CONSIGNEE,
  @Schema(description = "Importer")
  IMPORTER,
  @Schema(description = "Branch address")
  BRANCH_ADDRESS
}
