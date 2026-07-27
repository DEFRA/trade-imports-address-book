package uk.gov.defra.trade.imports.addressbook.address;

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

  /**
   * Case-insensitive partial-word search over {@code name}, {@code townOrCity} and {@code postcode}
   * for one organisation's ACTIVE addresses. Uses a quoted regex (ReDoS-safe), not {@code $text}.
   *
   * @param organisationId the owning organisation id
   * @param regex the quoted partial-match regex (e.g. {@code .*gree.*})
   * @param pageable the page number, size and sort (newest-first on {@code createdAt})
   * @return one page of matching ACTIVE addresses plus the total count
   */
  @Query(
      "{ 'organisationId': ?0, 'status': 'ACTIVE', '$or': [ "
          + "{ 'name': { '$regex': ?1, '$options': 'i' } }, "
          + "{ 'townOrCity': { '$regex': ?1, '$options': 'i' } }, "
          + "{ 'postcode': { '$regex': ?1, '$options': 'i' } } ] }")
  Page<Address> searchByQuery(String organisationId, String regex, Pageable pageable);

  /**
   * Search by a resolved ISO alpha-2 {@code countryCode} for one organisation's ACTIVE addresses.
   *
   * @param organisationId the owning organisation id
   * @param countryCode the ISO alpha-2 country code to match
   * @param pageable the page number, size and sort (newest-first on {@code createdAt})
   * @return one page of matching ACTIVE addresses plus the total count
   */
  @Query("{ 'organisationId': ?0, 'status': 'ACTIVE', 'countryCode': ?1 }")
  Page<Address> searchByCountryCode(
      String organisationId, String countryCode, Pageable pageable);

  /**
   * Combined text search over {@code name}/{@code townOrCity}/{@code postcode} OR an exact
   * {@code countryCode} match for one organisation's ACTIVE addresses.
   *
   * @param organisationId the owning organisation id
   * @param regex the quoted partial-match regex for the text fields
   * @param countryCode the ISO alpha-2 country code to match
   * @param pageable the page number, size and sort (newest-first on {@code createdAt})
   * @return one page of matching ACTIVE addresses plus the total count
   */
  @Query(
      "{ 'organisationId': ?0, 'status': 'ACTIVE', '$or': [ "
          + "{ 'name': { '$regex': ?1, '$options': 'i' } }, "
          + "{ 'townOrCity': { '$regex': ?1, '$options': 'i' } }, "
          + "{ 'postcode': { '$regex': ?1, '$options': 'i' } }, "
          + "{ 'countryCode': ?2 } ] }")
  Page<Address> searchByQueryAndCountryCode(
      String organisationId, String regex, String countryCode, Pageable pageable);
}
