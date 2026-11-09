package uk.gov.defra.trade.imports.addressbook.transporter;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Lists an organisation's live transporters. */
public interface TransporterListQuery {

  /**
   * One page of the organisation's {@code ACTIVE} transporters that match the criteria.
   *
   * @param organisationId the owning organisation id; the query never reads another organisation
   * @param criteria the filters to match
   * @param pageable the page and sort
   * @return the page, with its total over every match
   */
  Page<Transporter> listLiveTransporters(
      String organisationId, TransporterListCriteria criteria, Pageable pageable);
}
