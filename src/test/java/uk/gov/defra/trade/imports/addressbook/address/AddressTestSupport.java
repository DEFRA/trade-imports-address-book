package uk.gov.defra.trade.imports.addressbook.address;

import java.util.List;
import uk.gov.defra.trade.imports.addressbook.configuration.AddressTtlConfig;

public final class AddressTestSupport {

  static final AddressTtlConfig NO_TTL = new AddressTtlConfig(null, "dev");

  public static final ListCriteria NO_LIST_CRITERIA = listCriteria().build();

  private AddressTestSupport() {}

  public static ListCriteriaBuilder listCriteria() {
    return new ListCriteriaBuilder();
  }

  public static final class ListCriteriaBuilder {

    private AddressType addressType;
    private AddressUse use;
    private String searchPattern;
    private List<String> countryCodes = List.of();
    private List<AddressUse> searchUses = List.of();

    private ListCriteriaBuilder() {}

    public ListCriteriaBuilder addressType(AddressType addressType) {
      this.addressType = addressType;
      return this;
    }

    public ListCriteriaBuilder use(AddressUse use) {
      this.use = use;
      return this;
    }

    public ListCriteriaBuilder searchPattern(String searchPattern) {
      this.searchPattern = searchPattern;
      return this;
    }

    public ListCriteriaBuilder countryCodes(String... countryCodes) {
      this.countryCodes = List.of(countryCodes);
      return this;
    }

    public ListCriteriaBuilder searchUses(AddressUse... searchUses) {
      this.searchUses = List.of(searchUses);
      return this;
    }

    public ListCriteria build() {
      return new ListCriteria(addressType, use, searchPattern, countryCodes, searchUses);
    }
  }
}
