package uk.gov.defra.trade.imports.addressbook.address;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Lists an organisation's live addresses. */
public interface OperatorListQuery {

  /**
   * One page of the organisation's {@code ACTIVE} addresses.
   *
   * @param organisationId the owning organisation id; the query never reads another organisation
   * @param addressType when not null, the listing filters by its {@link AddressType#uses()}
   * @param searchPattern when not null, a regular expression that an address's name, address lines,
   *     town or city, county or postcode matches, case-insensitively
   * @param countryCodes country codes an address may be in; empty for none. With a search pattern,
   *     an address matching either is returned
   * @param pageable the page and sort
   * @return the page, with its total over every match
   */
  Page<Address> listLiveAddresses(
      String organisationId,
      AddressType addressType,
      String searchPattern,
      List<String> countryCodes,
      Pageable pageable);
}
