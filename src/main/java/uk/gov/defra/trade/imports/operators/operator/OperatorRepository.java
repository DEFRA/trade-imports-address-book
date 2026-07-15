package uk.gov.defra.trade.imports.operators.operator;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/** Spring Data MongoDB repository for {@link Operator}. */
@Repository
public interface OperatorRepository extends MongoRepository<Operator, String> {

  /**
   * Fetch an operator by id, scoped to the owning organisation's CRN. Scoping by CRN here is what
   * stops one organisation reading another's operator by guessing its id.
   *
   * @param id the operator id
   * @param crn the owning organisation's company reference number
   * @return the operator if it exists and belongs to {@code crn}, otherwise empty
   */
  Optional<Operator> findByIdAndCrn(String id, String crn);

  /**
   * One page of a caller's ACTIVE operators matching the list-params — the single query behind every
   * production read of the address book (design §1.3). All four combinations of the two optional
   * filters ({@code q} search text and {@code operator_type}) are covered by one method through the
   * {@link OperatorService#list} sentinel convention: an all-7-types list when {@code operator_type}
   * is absent, and an empty {@code quotedRegex} (matches everything) when {@code q} is absent.
   *
   * <p>{@code types} drives the {@code operatorType $in} clause; {@code quotedRegex} is a
   * case-insensitive {@code $regex} matched (as an {@code $or}) over {@code name}, both address
   * lines, {@code town}, {@code county}, {@code postcode} and {@code country} — a display-name
   * substring match on country (c-004; there is no code&lt;-&gt;name conversion). The caller passes
   * {@code Pattern.quote(q)} so user text is never compiled as a pattern (no ReDoS, no regex-syntax
   * 500s). The whole match sits inside the {@code crn + status=ACTIVE} bound of the
   * {@code crn_status_type_created} index; DELETED tombstones are excluded by the literal status.
   * There is deliberately no {@code $text} index (one per collection, tokenises postcodes badly, no
   * substring match).
   *
   * @param crn the caller's company reference number
   * @param types the operator types to include ($in) — all 7 when unfiltered
   * @param quotedRegex the {@link java.util.regex.Pattern#quote quoted} search text — "" when absent
   * @param pageable the page number, size and sort (newest-first on {@code createdAt})
   * @return one page of matching ACTIVE operators plus the total count
   */
  @Query(
      "{'crn': ?0, 'status': 'ACTIVE', 'operatorType': {$in: ?1}, "
          + "$or: [{'name': {$regex: ?2, $options: 'i'}}, "
          + "{'addressLine1': {$regex: ?2, $options: 'i'}}, "
          + "{'addressLine2': {$regex: ?2, $options: 'i'}}, "
          + "{'town': {$regex: ?2, $options: 'i'}}, "
          + "{'county': {$regex: ?2, $options: 'i'}}, "
          + "{'postcode': {$regex: ?2, $options: 'i'}}, "
          + "{'country': {$regex: ?2, $options: 'i'}}]}")
  Page<Operator> search(
      String crn, List<OperatorType> types, String quotedRegex, Pageable pageable);
}
