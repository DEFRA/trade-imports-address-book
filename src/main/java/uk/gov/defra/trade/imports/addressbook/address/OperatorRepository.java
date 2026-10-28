package uk.gov.defra.trade.imports.addressbook.address;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.Repository;

@org.springframework.stereotype.Repository
public interface OperatorRepository extends Repository<Address, String> {

  Address save(Address address);

  Optional<Address> findByIdAndOrganisationId(String id, String organisationId);

  Page<Address> findByOrganisationIdAndStatus(
      String organisationId, AddressStatus status, Pageable pageable);

  String TEXT_SEARCH_CLAUSES =
      "{ 'name': { '$regex': ?2, '$options': 'i' } }, "
          + "{ 'addressLine1': { '$regex': ?2, '$options': 'i' } }, "
          + "{ 'addressLine2': { '$regex': ?2, '$options': 'i' } }, "
          + "{ 'townOrCity': { '$regex': ?2, '$options': 'i' } }, "
          + "{ 'county': { '$regex': ?2, '$options': 'i' } }, "
          + "{ 'postcode': { '$regex': ?2, '$options': 'i' } }";

  @Query("{ 'organisationId': ?0, 'status': ?1, '$or': [ " + TEXT_SEARCH_CLAUSES + " ] }")
  Page<Address> searchByQuery(
      String organisationId, AddressStatus status, String regex, Pageable pageable);

  @Query("{ 'organisationId': ?0, 'status': ?1, 'countryCode': { '$in': ?2 } }")
  Page<Address> searchByAnyCountryCode(
      String organisationId, AddressStatus status, List<String> countryCodes, Pageable pageable);

  @Query(
      "{ 'organisationId': ?0, 'status': ?1, '$or': [ "
          + TEXT_SEARCH_CLAUSES
          + ", { 'countryCode': { '$in': ?3 } } ] }")
  Page<Address> searchByQueryOrAnyCountryCode(
      String organisationId,
      AddressStatus status,
      String regex,
      List<String> countryCodes,
      Pageable pageable);
}
