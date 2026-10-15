package uk.gov.defra.trade.imports.addressbook.address;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The three categories the address book's "Choose an address type" question offers. There is no
 * transporter type. The wire value of each constant is its name.
 */
@Schema(description = "What kind of address an address book record is")
public enum AddressType {
  @Schema(description = "Origin or consignor: a place of origin or a consignor")
  ORIGIN_OR_CONSIGNOR,
  @Schema(description = "Destination, consignee or importer")
  DESTINATION_CONSIGNEE_OR_IMPORTER,
  @Schema(description = "A branch address of the trader's own business")
  BRANCH
}
