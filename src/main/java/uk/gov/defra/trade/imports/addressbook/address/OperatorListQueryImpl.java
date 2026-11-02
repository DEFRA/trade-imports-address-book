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

  private static final List<String> SEARCHED_FIELDS =
      List.of("name", "addressLine1", "addressLine2", "townOrCity", "county", "postcode");

  private final MongoTemplate mongoTemplate;

  OperatorListQueryImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public Page<Address> listLiveAddresses(
      String organisationId,
      AddressType addressType,
      String searchPattern,
      List<String> countryCodes,
      Pageable pageable) {
    Query query = Query.query(matching(organisationId, addressType, searchPattern, countryCodes));
    List<Address> content = mongoTemplate.find(Query.of(query).with(pageable), Address.class);
    return PageableExecutionUtils.getPage(
        content, pageable, () -> mongoTemplate.exactCount(query, Address.class));
  }

  private static Criteria matching(
      String organisationId,
      AddressType addressType,
      String searchPattern,
      List<String> countryCodes) {
    List<Criteria> clauses = new ArrayList<>();
    clauses.add(Criteria.where("organisationId").is(organisationId));
    clauses.add(Criteria.where("status").is(AddressStatus.ACTIVE));
    if (addressType != null) {
      clauses.add(listedUnder(addressType));
    }
    if (searchPattern != null || !countryCodes.isEmpty()) {
      clauses.add(matchesSearchOrCountry(searchPattern, countryCodes));
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

  private static Criteria matchesSearchOrCountry(String searchPattern, List<String> countryCodes) {
    Stream<Criteria> searched =
        searchPattern == null
            ? Stream.empty()
            : SEARCHED_FIELDS.stream()
                .map(field -> Criteria.where(field).regex(searchPattern, "i"));
    Stream<Criteria> inCountry =
        countryCodes.isEmpty()
            ? Stream.empty()
            : Stream.of(Criteria.where("countryCode").in(countryCodes));
    return new Criteria().orOperator(Stream.concat(searched, inCountry).toList());
  }
}
