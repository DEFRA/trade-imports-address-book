package uk.gov.defra.trade.imports.addressbook.address;

import java.util.List;
import org.springframework.lang.Nullable;

/**
 * The filters of a list query, as the query matches them.
 *
 * @param addressType when not null, the listing filters by its {@link AddressType#uses()}
 * @param use when not null, an address must hold this use
 * @param searchPattern when not null, a regular expression that an address's name, address lines,
 *     town or city, county or postcode matches, case-insensitively
 * @param countryCodes trimmed, non-blank country codes an address may be in; must not be null,
 *     empty for none
 * @param searchUses uses an address may hold to match the search; must not be null, empty for none
 */
public record ListCriteria(
    @Nullable AddressType addressType,
    @Nullable AddressUse use,
    @Nullable String searchPattern,
    List<String> countryCodes,
    List<AddressUse> searchUses) {

  public ListCriteria {
    countryCodes = List.copyOf(countryCodes);
    searchUses = List.copyOf(searchUses);
  }
}
