package uk.gov.defra.trade.imports.operators.operator;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
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
   * One page of an organisation's ACTIVE addresses matching the free-text search — the single query
   * behind every production read of the address book. Addresses are untyped (cv-017), so there is no
   * type filter: an empty {@code quotedRegex} (matches everything) covers the unsearched case.
   *
   * <p>{@code quotedRegex} is a case-insensitive {@code $regex} matched (as an {@code $or}) over
   * {@code name}, both address lines, {@code townOrCity}, {@code county}, {@code postcode} and
   * {@code countryCode}. The caller passes {@code Pattern.quote(q)} so user text is never compiled
   * as a pattern (no ReDoS, no regex-syntax 500s). The whole match sits inside the
   * {@code organisationId + status=ACTIVE} bound of the {@code org_status_created} index; DELETED
   * tombstones are excluded by the literal status.
   *
   * @param organisationId the owning organisation id
   * @param quotedRegex the {@link java.util.regex.Pattern#quote quoted} search text — "" when absent
   * @param pageable the page number, size and sort (newest-first on {@code createdAt})
   * @return one page of matching ACTIVE addresses plus the total count
   */
  @Query(
      "{'organisationId': ?0, 'status': 'ACTIVE', "
          + "$or: [{'name': {$regex: ?1, $options: 'i'}}, "
          + "{'addressLine1': {$regex: ?1, $options: 'i'}}, "
          + "{'addressLine2': {$regex: ?1, $options: 'i'}}, "
          + "{'townOrCity': {$regex: ?1, $options: 'i'}}, "
          + "{'county': {$regex: ?1, $options: 'i'}}, "
          + "{'postcode': {$regex: ?1, $options: 'i'}}, "
          + "{'countryCode': {$regex: ?1, $options: 'i'}}]}")
  Page<Address> search(String organisationId, String quotedRegex, Pageable pageable);
}
