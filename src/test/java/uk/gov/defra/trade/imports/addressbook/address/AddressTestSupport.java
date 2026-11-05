package uk.gov.defra.trade.imports.addressbook.address;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import uk.gov.defra.trade.imports.addressbook.configuration.AddressTtlConfig;

public final class AddressTestSupport {

  static final AddressTtlConfig NO_TTL = new AddressTtlConfig(null, "dev");

  public static final ListCriteria NO_LIST_CRITERIA = listCriteria().build();

  static final String STORED_ADDRESS_ID = "665f1c2ab3e4d51a2c9d0e77";
  static final Instant STORED_CREATED_AT = Instant.parse("2026-07-01T09:00:00Z");
  static final Instant STORED_MODIFIED_AT = Instant.parse("2026-07-02T10:30:00Z");

  private AddressTestSupport() {}

  static AddressRequest.AddressRequestBuilder validRequest() {
    return AddressRequest.builder()
        .name("Highland Livestock Ltd")
        .addressLine1("14 Drover's Way")
        .townOrCity("Inverness")
        .postcode("IV2 3JH")
        .countryCode("GB")
        .phone("+44 1463 234567")
        .email("exports@highlandlivestock.example.com")
        .roles(List.of("PLACE_OF_ORIGIN"));
  }

  static AddressRequest.AddressRequestBuilder replacementRequest() {
    return validRequest()
        .name("Lowland Cattle Co")
        .addressLine1("2 Market Street")
        .addressLine2("Suite 5")
        .townOrCity("Perth")
        .county("Perth and Kinross")
        .postcode("PH1 5AA")
        .countryCode("IE")
        .phone("+44 1738 111222")
        .email("ops@lowlandcattle.example.com")
        .roles(List.of("BRANCH_ADDRESS"));
  }

  static Address.AddressBuilder storedAddress(String organisationId, AddressStatus status) {
    return Address.builder()
        .id(STORED_ADDRESS_ID)
        .name("Highland Livestock Ltd")
        .addressLine1("14 Drover's Way")
        .addressLine2("Unit 3")
        .townOrCity("Inverness")
        .county("Highland")
        .postcode("IV2 3JH")
        .countryCode("GB")
        .phone("+44 1463 234567")
        .email("exports@highlandlivestock.example.com")
        .roles(List.of(AddressRole.IMPORTER, AddressRole.CONSIGNEE))
        .organisationId(organisationId)
        .status(status)
        .createdAt(STORED_CREATED_AT)
        .modifiedAt(STORED_MODIFIED_AT);
  }

  public static ListCriteriaBuilder listCriteria() {
    return new ListCriteriaBuilder();
  }

  public static final class ListCriteriaBuilder {

    private List<AddressRole> roles = List.of();
    private String searchPattern;
    private List<String> countryCodes = List.of();
    private List<AddressRole> searchRoles = List.of();

    private ListCriteriaBuilder() {}

    public ListCriteriaBuilder roles(AddressRole... roles) {
      this.roles = List.of(roles);
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

    public ListCriteriaBuilder searchRoles(AddressRole... searchRoles) {
      this.searchRoles = List.of(searchRoles);
      return this;
    }

    public ListCriteria build() {
      List<ListCriteria.SearchTerm> searchTerms = new ArrayList<>();
      if (searchPattern != null) {
        searchTerms.add(new ListCriteria.TextMatching(searchPattern));
      }
      if (!countryCodes.isEmpty()) {
        searchTerms.add(new ListCriteria.InCountries(countryCodes));
      }
      if (!searchRoles.isEmpty()) {
        searchTerms.add(new ListCriteria.HoldingAnyRole(searchRoles));
      }
      return new ListCriteria(roles, searchTerms);
    }
  }
}
