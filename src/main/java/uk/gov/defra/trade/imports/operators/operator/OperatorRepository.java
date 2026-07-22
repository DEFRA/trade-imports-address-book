package uk.gov.defra.trade.imports.operators.operator;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/** Spring Data MongoDB repository for {@link Address}. */
@Repository
public interface OperatorRepository extends MongoRepository<Address, String> {

  /**
   * Fetch an address by id, scoped to the owning organisation. Scoping by {@code organisationId}
   * here is what stops one organisation reading another's address by guessing its id (cv-010).
   *
   * @param id the address id
   * @param organisationId the owning organisation id
   * @return the address if it exists and belongs to {@code organisationId}, otherwise empty
   */
  Optional<Address> findByIdAndOrganisationId(String id, String organisationId);

  /**
   * One page of an organisation's addresses in the given lifecycle status — the single query behind
   * every production read of the address book. Org-isolation is in the query (the
   * {@code organisationId} lead), not a post-filter (186.AC5); passing {@code AddressStatus.ACTIVE}
   * excludes DELETED tombstones. Both bounds sit inside the {@code org_status_created} compound
   * index, which also serves the newest-first sort carried on the {@link Pageable}.
   *
   * @param organisationId the owning organisation id
   * @param status the lifecycle status to match (ACTIVE for the live address book)
   * @param pageable the page number, size and sort (newest-first on {@code createdAt})
   * @return one page of matching addresses plus the total count
   */
  Page<Address> findByOrganisationIdAndStatus(
      String organisationId, AddressStatus status, Pageable pageable);
}
