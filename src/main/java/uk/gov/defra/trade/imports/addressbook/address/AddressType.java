package uk.gov.defra.trade.imports.addressbook.address;

import io.swagger.v3.oas.annotations.media.Schema;

/** The kind of address an entry is. */
@Schema(description = "The kind of address an entry is")
public enum AddressType {
  @Schema(description = "Origin or consignor")
  ORIGIN_OR_CONSIGNOR,
  @Schema(description = "Destination, consignee or importer")
  DESTINATION_CONSIGNEE_OR_IMPORTER,
  @Schema(description = "Branch")
  BRANCH
}
