package uk.gov.defra.trade.imports.addressbook.transporter;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import uk.gov.defra.trade.imports.addressbook.ListMatchCriteria;
import uk.gov.defra.trade.imports.addressbook.OrganisationScopedListQuery;
import uk.gov.defra.trade.imports.addressbook.transporter.TransporterListCriteria.InCountries;
import uk.gov.defra.trade.imports.addressbook.transporter.TransporterListCriteria.Match;
import uk.gov.defra.trade.imports.addressbook.transporter.TransporterListCriteria.OfType;
import uk.gov.defra.trade.imports.addressbook.transporter.TransporterListCriteria.TextMatching;

class TransporterListQueryImpl implements TransporterListQuery {

  private static final List<String> SEARCHABLE_FIELDS =
      List.of(
          "name",
          "addressLine1",
          "addressLine2",
          "townOrCity",
          "county",
          "postcode",
          "approvalNumber");

  private final MongoTemplate mongoTemplate;

  TransporterListQueryImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public Page<Transporter> listLiveTransporters(
      String organisationId, TransporterListCriteria criteria, Pageable pageable) {
    return OrganisationScopedListQuery.listLive(
        mongoTemplate,
        Transporter.class,
        organisationId,
        TransporterStatus.ACTIVE,
        extraClauses(criteria),
        pageable);
  }

  private static List<Criteria> extraClauses(TransporterListCriteria criteria) {
    List<Criteria> clauses = new ArrayList<>();
    if (!criteria.types().isEmpty()) {
      clauses.add(ofAnyType(criteria.types()));
    }
    if (!criteria.matches().isEmpty()) {
      clauses.add(
          new Criteria()
              .orOperator(
                  criteria.matches().stream()
                      .map(TransporterListQueryImpl::clauseFor)
                      .toList()));
    }
    return clauses;
  }

  private static Criteria clauseFor(Match match) {
    return switch (match) {
      case TextMatching(String pattern) ->
          ListMatchCriteria.textMatching(SEARCHABLE_FIELDS, pattern);
      case InCountries(List<String> countryCodes) -> ListMatchCriteria.inCountries(countryCodes);
      case OfType(List<TransporterType> types) -> ofAnyType(types);
    };
  }

  private static Criteria ofAnyType(List<TransporterType> types) {
    return Criteria.where("type").in(types);
  }
}
