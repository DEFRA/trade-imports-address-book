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
import uk.gov.defra.trade.imports.addressbook.address.ListCriteria.HoldingAnyRole;
import uk.gov.defra.trade.imports.addressbook.address.ListCriteria.InCountries;
import uk.gov.defra.trade.imports.addressbook.address.ListCriteria.Match;
import uk.gov.defra.trade.imports.addressbook.address.ListCriteria.TextMatching;

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
    if (!criteria.roles().isEmpty()) {
      clauses.add(holdingAnyOf(criteria.roles()));
    }
    if (!criteria.matches().isEmpty()) {
      clauses.add(
          new Criteria()
              .orOperator(
                  criteria.matches().stream().flatMap(AddressListQueryImpl::clausesOf).toList()));
    }
    return new Criteria().andOperator(clauses);
  }

  private static Stream<Criteria> clausesOf(Match match) {
    return switch (match) {
      case TextMatching(String pattern) ->
          Stream.of("name", "addressLine1", "addressLine2", "townOrCity", "county", "postcode")
              .map(field -> Criteria.where(field).regex(pattern, "i"));
      case InCountries(List<String> countryCodes) ->
          Stream.of(Criteria.where("countryCode").in(countryCodes));
      case HoldingAnyRole(List<AddressRole> roles) -> Stream.of(holdingAnyOf(roles));
    };
  }

  private static Criteria holdingAnyOf(List<AddressRole> roles) {
    return Criteria.where("roles").in(roles);
  }
}
