package uk.gov.defra.trade.imports.addressbook.address;

import io.swagger.v3.oas.annotations.media.Schema;

/** What an address can be used for. */
@Schema(description = "What an address can be used for")
public enum AddressUse {
  PLACE_OF_ORIGIN,
  CONSIGNOR,
  PLACE_OF_DESTINATION,
  CONSIGNEE,
  IMPORTER,
  BRANCH_ADDRESS
}
