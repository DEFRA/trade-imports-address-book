package uk.gov.defra.trade.imports.addressbook;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;

/**
 * The organisation-scoped, paginated Mongo query every address-book list query runs: live
 * documents for the caller's organisation, narrowed by whatever extra clauses the caller's own
 * criteria add.
 */
public final class OrganisationScopedListQuery {

  private OrganisationScopedListQuery() {}

  /**
   * @param mongoTemplate the template to query with
   * @param documentType the document type being listed
   * @param organisationId the owning organisation id
   * @param activeStatus the status value a live document carries
   * @param extraClauses the caller's own clauses (a keep filter, a search match), ANDed in
   *     alongside the organisation and status clauses
   * @param pageable the requested page
   * @return one page of the matching documents with its total count
   */
  public static <T> Page<T> listLive(
      MongoTemplate mongoTemplate,
      Class<T> documentType,
      String organisationId,
      Object activeStatus,
      List<Criteria> extraClauses,
      Pageable pageable) {
    List<Criteria> clauses = new ArrayList<>();
    clauses.add(Criteria.where("organisationId").is(organisationId));
    clauses.add(Criteria.where("status").is(activeStatus));
    clauses.addAll(extraClauses);
    Query query = Query.query(new Criteria().andOperator(clauses));
    List<T> content = mongoTemplate.find(Query.of(query).with(pageable), documentType);
    return PageableExecutionUtils.getPage(
        content, pageable, () -> mongoTemplate.exactCount(query, documentType));
  }
}
