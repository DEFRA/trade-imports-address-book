package uk.gov.defra.trade.imports.addressbook.address;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Organisation-scoped address persistence. Only declares org-scoped finders and {@code save} — no
 * unscoped {@code findById} / {@code deleteById} (cv-010).
 */
@org.springframework.stereotype.Repository
public interface OperatorRepository extends Repository<Address, String> {

  Address save(Address address);

  void deleteAll();

  Optional<Address> findByIdAndOrganisationId(String id, String organisationId);

  Page<Address> findByOrganisationIdAndStatus(
      String organisationId, AddressStatus status, Pageable pageable);

  @Query(
      "{ 'organisationId': ?0, 'status': ?1, '$or': [ "
          + "{ 'name': { '$regex': ?2, '$options': 'i' } }, "
          + "{ 'townOrCity': { '$regex': ?2, '$options': 'i' } }, "
          + "{ 'postcode': { '$regex': ?2, '$options': 'i' } } ] }")
  Page<Address> searchByQuery(
      String organisationId, AddressStatus status, String regex, Pageable pageable);

  @Query("{ 'organisationId': ?0, 'status': ?1, 'countryCode': ?2 }")
  Page<Address> searchByCountryCode(
      String organisationId, AddressStatus status, String countryCode, Pageable pageable);

  @Query(
      "{ 'organisationId': ?0, 'status': ?1, '$or': [ "
          + "{ 'name': { '$regex': ?2, '$options': 'i' } }, "
          + "{ 'townOrCity': { '$regex': ?2, '$options': 'i' } }, "
          + "{ 'postcode': { '$regex': ?2, '$options': 'i' } }, "
          + "{ 'countryCode': ?3 } ] }")
  Page<Address> searchByQueryAndCountryCode(
      String organisationId,
      AddressStatus status,
      String regex,
      String countryCode,
      Pageable pageable);
}
