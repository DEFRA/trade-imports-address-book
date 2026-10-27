package uk.gov.defra.trade.imports.addressbook.address;

import uk.gov.defra.trade.imports.addressbook.configuration.AddressTtlConfig;

final class OperatorTestSupport {

  static final AddressTtlConfig NO_TTL = new AddressTtlConfig(null, "dev");
  static final int DEFAULT_PAGE_SIZE = 25;

  private OperatorTestSupport() {}
}
