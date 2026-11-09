package uk.gov.defra.trade.imports.addressbook.address;

import java.util.List;
import uk.gov.defra.trade.imports.addressbook.RequestedListFilterSupport;

/**
 * The filters of a list request. A filter the request did not supply is empty, never null.
 *
 * @param searchTerm the user's search term, trimmed; empty for no search
 * @param countryCodes the requested ISO alpha-2 country codes, trimmed, without blanks
 * @param roles the requested roles to keep, without repeats
 * @param searchRoles the requested search roles, without repeats
 */
public record RequestedListFilters(
    String searchTerm,
    List<String> countryCodes,
    List<AddressRole> roles,
    List<AddressRole> searchRoles) {

  public RequestedListFilters {
    searchTerm = searchTerm == null ? "" : searchTerm.trim();
    countryCodes =
        RequestedListFilterSupport.presentElements(countryCodes)
            .map(String::trim)
            .filter(code -> !code.isEmpty())
            .toList();
    roles = RequestedListFilterSupport.presentElements(roles).distinct().toList();
    searchRoles = RequestedListFilterSupport.presentElements(searchRoles).distinct().toList();
  }
}
