package uk.gov.defra.trade.imports.operators.operator;

import java.util.Optional;
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
}
