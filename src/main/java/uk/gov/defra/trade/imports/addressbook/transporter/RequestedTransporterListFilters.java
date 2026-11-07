package uk.gov.defra.trade.imports.addressbook.transporter;

import java.util.List;
import uk.gov.defra.trade.imports.addressbook.ListPaging;

/**
 * The filters of a transporter list request. A filter the request did not supply is empty, never
 * null.
 *
 * @param searchTerm the user's search term, trimmed; empty for no search
 * @param countryCodes the requested ISO alpha-2 country codes, trimmed, without blanks
 * @param types the requested types to keep, without repeats
 * @param searchTypes the requested search types, without repeats
 */
public record RequestedTransporterListFilters(
    String searchTerm,
    List<String> countryCodes,
    List<TransporterType> types,
    List<TransporterType> searchTypes) {

  public RequestedTransporterListFilters {
    searchTerm = searchTerm == null ? "" : searchTerm.trim();
    countryCodes =
        ListPaging.presentElements(countryCodes)
            .map(String::trim)
            .filter(code -> !code.isEmpty())
            .toList();
    types = ListPaging.presentElements(types).distinct().toList();
    searchTypes = ListPaging.presentElements(searchTypes).distinct().toList();
  }
}
