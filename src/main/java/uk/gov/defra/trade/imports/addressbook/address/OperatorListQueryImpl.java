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

class OperatorListQueryImpl implements OperatorListQuery {

  private final MongoTemplate mongoTemplate;

  OperatorListQueryImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public Page<Address> listLiveAddresses(
      String organisationId, ListCriteria criteria, Pageable pageable) {
    Query query = Query.query(matching(organisationId, criteria));
    List<Address> content = mongoTemplate.find(Query.of(query).with(pageable), Address.class);
    return PageableExecutionUtils.getPage(
        content, pageable, () -> mongoTemplate.exactCount(query, Address.class));
  }

  private static Criteria matching(String organisationId, ListCriteria criteria) {
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
      clauses.add(matchesSearch(criteria));
    }
    return new Criteria().andOperator(clauses);
  }

  private static Criteria listedUnder(AddressType addressType) {
    Criteria holdsAUse = Criteria.where("uses").in(addressType.uses());
    if (!addressType.listsAddressWithNoUses()) {
      return holdsAUse;
    }
    return new Criteria()
        .orOperator(holdsAUse, Criteria.where("uses").is(null), Criteria.where("uses").size(0));
  }

  private static Criteria matchesSearch(ListCriteria criteria) {
    List<String> searchedFields =
        List.of("name", "addressLine1", "addressLine2", "townOrCity", "county", "postcode");
    Stream<Criteria> inSearchedField =
        criteria.searchPattern() == null
            ? Stream.empty()
            : searchedFields.stream()
                .map(field -> Criteria.where(field).regex(criteria.searchPattern(), "i"));
    Stream<Criteria> inCountry =
        criteria.countryCodes().isEmpty()
            ? Stream.empty()
            : Stream.of(Criteria.where("countryCode").in(criteria.countryCodes()));
    Stream<Criteria> holdingSearchUse =
        criteria.searchUses().isEmpty()
            ? Stream.empty()
            : Stream.of(Criteria.where("uses").in(criteria.searchUses()));
    return new Criteria()
        .orOperator(
            Stream.of(inSearchedField, inCountry, holdingSearchUse).flatMap(s -> s).toList());
  }
}
