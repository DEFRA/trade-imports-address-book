package uk.gov.defra.trade.imports.addressbook.address;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import uk.gov.defra.trade.imports.addressbook.ListMatchCriteria;
import uk.gov.defra.trade.imports.addressbook.OrganisationScopedListQuery;
import uk.gov.defra.trade.imports.addressbook.address.ListCriteria.HoldingAnyRole;
import uk.gov.defra.trade.imports.addressbook.address.ListCriteria.InCountries;
import uk.gov.defra.trade.imports.addressbook.address.ListCriteria.Match;
import uk.gov.defra.trade.imports.addressbook.address.ListCriteria.TextMatching;

class AddressListQueryImpl implements AddressListQuery {

  private static final List<String> SEARCHABLE_FIELDS =
      List.of("name", "addressLine1", "addressLine2", "townOrCity", "county", "postcode");

  private final MongoTemplate mongoTemplate;

  AddressListQueryImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public Page<Address> listLiveAddresses(
      String organisationId, ListCriteria criteria, Pageable pageable) {
    return OrganisationScopedListQuery.listLive(
        mongoTemplate,
        Address.class,
        organisationId,
        AddressStatus.ACTIVE,
        extraClauses(criteria),
        pageable);
  }

  private static List<Criteria> extraClauses(ListCriteria criteria) {
    List<Criteria> clauses = new ArrayList<>();
    if (!criteria.roles().isEmpty()) {
      clauses.add(holdingAnyOf(criteria.roles()));
    }
    if (!criteria.matches().isEmpty()) {
      clauses.add(
          new Criteria()
              .orOperator(criteria.matches().stream().map(AddressListQueryImpl::clauseFor).toList()));
    }
    return clauses;
  }

  private static Criteria clauseFor(Match match) {
    return switch (match) {
      case TextMatching(String pattern) -> ListMatchCriteria.textMatching(SEARCHABLE_FIELDS, pattern);
      case InCountries(List<String> countryCodes) -> ListMatchCriteria.inCountries(countryCodes);
      case HoldingAnyRole(List<AddressRole> roles) -> holdingAnyOf(roles);
    };
  }

  private static Criteria holdingAnyOf(List<AddressRole> roles) {
    return Criteria.where("roles").in(roles);
  }
}
