package uk.gov.defra.trade.imports.addressbook.address;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;

class AddressListQueryImpl implements AddressListQuery {

  private final MongoTemplate mongoTemplate;

  AddressListQueryImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public Page<Address> listLiveAddresses(
      String organisationId, ListCriteria criteria, Pageable pageable) {
    Query query = Query.query(liveAddressCriteria(organisationId, criteria));
    List<Address> content = mongoTemplate.find(Query.of(query).with(pageable), Address.class);
    return PageableExecutionUtils.getPage(
        content, pageable, () -> mongoTemplate.exactCount(query, Address.class));
  }

  private static Criteria liveAddressCriteria(String organisationId, ListCriteria criteria) {
    List<Criteria> clauses = new ArrayList<>();
    clauses.add(Criteria.where("organisationId").is(organisationId));
    clauses.add(Criteria.where("status").is(AddressStatus.ACTIVE));
    if (criteria.addressType() != null) {
      clauses.add(listedUnder(criteria.addressType()));
    }
    if (criteria.use() != null) {
      clauses.add(Criteria.where("uses").is(criteria.use()));
    }
    if (criteria.searchPattern() != null
        || !criteria.countryCodes().isEmpty()
        || !criteria.searchUses().isEmpty()) {
      clauses.add(searchClause(criteria));
    }
    return new Criteria().andOperator(clauses);
  }

  private static Criteria listedUnder(AddressType addressType) {
    Criteria holdsOneOfTheTypesUses = Criteria.where("uses").in(addressType.uses());
    if (!addressType.listsAddressWithNoUses()) {
      return holdsOneOfTheTypesUses;
    }
    return new Criteria()
        .orOperator(
            holdsOneOfTheTypesUses,
            Criteria.where("uses").is(null),
            Criteria.where("uses").size(0));
  }

  private static Criteria searchClause(ListCriteria criteria) {
    return new Criteria()
        .orOperator(
            Stream.of(
                    searchedFieldClauses(criteria),
                    countryClauses(criteria),
                    searchUseClauses(criteria))
                .flatMap(List::stream)
                .toList());
  }

  private static List<Criteria> searchedFieldClauses(ListCriteria criteria) {
    if (criteria.searchPattern() == null) {
      return List.of();
    }
    return List.of("name", "addressLine1", "addressLine2", "townOrCity", "county", "postcode")
        .stream()
        .map(field -> Criteria.where(field).regex(criteria.searchPattern(), "i"))
        .toList();
  }

  private static List<Criteria> countryClauses(ListCriteria criteria) {
    if (criteria.countryCodes().isEmpty()) {
      return List.of();
    }
    return List.of(Criteria.where("countryCode").in(criteria.countryCodes()));
  }

  private static List<Criteria> searchUseClauses(ListCriteria criteria) {
    if (criteria.searchUses().isEmpty()) {
      return List.of();
    }
    return List.of(Criteria.where("uses").in(criteria.searchUses()));
  }
}
