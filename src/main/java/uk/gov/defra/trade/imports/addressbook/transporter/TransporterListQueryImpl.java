package uk.gov.defra.trade.imports.addressbook.transporter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import uk.gov.defra.trade.imports.addressbook.transporter.TransporterListCriteria.InCountries;
import uk.gov.defra.trade.imports.addressbook.transporter.TransporterListCriteria.Match;
import uk.gov.defra.trade.imports.addressbook.transporter.TransporterListCriteria.OfType;
import uk.gov.defra.trade.imports.addressbook.transporter.TransporterListCriteria.TextMatching;

class TransporterListQueryImpl implements TransporterListQuery {

  private final MongoTemplate mongoTemplate;

  TransporterListQueryImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public Page<Transporter> listLiveTransporters(
      String organisationId, TransporterListCriteria criteria, Pageable pageable) {
    Query query = Query.query(liveTransporterCriteria(organisationId, criteria));
    List<Transporter> content = mongoTemplate.find(Query.of(query).with(pageable), Transporter.class);
    return PageableExecutionUtils.getPage(
        content, pageable, () -> mongoTemplate.exactCount(query, Transporter.class));
  }

  private static Criteria liveTransporterCriteria(
      String organisationId, TransporterListCriteria criteria) {
    List<Criteria> clauses = new ArrayList<>();
    clauses.add(Criteria.where("organisationId").is(organisationId));
    clauses.add(Criteria.where("status").is(TransporterStatus.ACTIVE));
    if (!criteria.types().isEmpty()) {
      clauses.add(ofAnyType(criteria.types()));
    }
    if (!criteria.matches().isEmpty()) {
      clauses.add(
          new Criteria()
              .orOperator(
                  criteria.matches().stream()
                      .flatMap(TransporterListQueryImpl::clausesOf)
                      .toList()));
    }
    return new Criteria().andOperator(clauses);
  }

  private static Stream<Criteria> clausesOf(Match match) {
    return switch (match) {
      case TextMatching(String pattern) ->
          Stream.of(
                  "name",
                  "addressLine1",
                  "addressLine2",
                  "townOrCity",
                  "county",
                  "postcode",
                  "approvalNumber")
              .map(field -> Criteria.where(field).regex(pattern, "i"));
      case InCountries(List<String> countryCodes) ->
          Stream.of(Criteria.where("countryCode").in(countryCodes));
      case OfType(List<TransporterType> types) -> Stream.of(ofAnyType(types));
    };
  }

  private static Criteria ofAnyType(List<TransporterType> types) {
    return Criteria.where("type").in(types);
  }
}
