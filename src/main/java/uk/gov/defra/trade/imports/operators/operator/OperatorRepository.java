package uk.gov.defra.trade.imports.operators.operator;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
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
   * One page of a caller's operators of a given status, newest-first when the {@link Pageable}
   * sorts by {@code createdAt} descending. The {@code crn + status} predicate is served by the
   * {@code crn_status_type_created} compound index; DELETED tombstones are excluded by passing
   * {@code ACTIVE}. The search + type-filter variant arrives in inc-010.
   *
   * @param crn the caller's company reference number
   * @param status the status to include (ACTIVE for the address-book list)
   * @param pageable the page number, size and sort
   * @return one page of matching operators plus the total count
   */
  Page<Operator> findByCrnAndStatus(String crn, OperatorStatus status, Pageable pageable);
}
