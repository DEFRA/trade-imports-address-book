package uk.gov.defra.trade.imports.addressbook.address;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Lists an organisation's live addresses. */
public interface OperatorListQuery {

  /**
   * One page of the organisation's {@code ACTIVE} addresses. The criteria's address type and use
   * narrow the listing; its search pattern, country codes and search uses form one search group,
   * and an address matching any of them is returned.
   *
   * @param organisationId the owning organisation id; the query never reads another organisation
   * @param criteria the filters to match
   * @param pageable the page and sort
   * @return the page, with its total over every match
   */
  Page<Address> listLiveAddresses(String organisationId, ListCriteria criteria, Pageable pageable);
}
