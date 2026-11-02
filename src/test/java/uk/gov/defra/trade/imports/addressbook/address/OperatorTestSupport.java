package uk.gov.defra.trade.imports.addressbook.address;

import java.util.List;
import uk.gov.defra.trade.imports.addressbook.configuration.AddressTtlConfig;

public final class OperatorTestSupport {

  static final AddressTtlConfig NO_TTL = new AddressTtlConfig(null, "dev");

  public static final ListCriteria NO_LIST_CRITERIA =
      new ListCriteria(null, null, null, List.of(), List.of());

  private OperatorTestSupport() {}
}
