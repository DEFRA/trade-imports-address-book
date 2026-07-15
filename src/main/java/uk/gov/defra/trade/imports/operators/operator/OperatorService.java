package uk.gov.defra.trade.imports.operators.operator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for operators. Owns the create/read/update/delete business rules; the wire
 * contract lives on the response records and the persistence shape on the {@link Operator} entity.
 *
 * <p>On create the identity ({@code crn}, {@code organisationId}) is stamped from the trusted
 * forwarded headers (c-001), never taken from the body, and the operator starts {@code ACTIVE}.
 * {@code id} is left for Mongo and the timestamps for auditing.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OperatorService {

  private final OperatorRepository repository;

  /**
   * Persists a new operator owned by the calling organisation.
   *
   * @param request the validated create body (client-supplied fields only)
   * @param crn the owning organisation's company reference number, from the identity header
   * @param organisationId the owning organisation id, from the identity header
   * @return the persisted operator, with its Mongo-assigned id and audited timestamps
   */
  public Operator create(OperatorRequest request, String crn, String organisationId) {
    Operator operator = OperatorMapper.toEntity(request);
    operator.setCrn(crn);
    operator.setOrganisationId(organisationId);
    operator.setStatus(OperatorStatus.ACTIVE);

    Operator saved = repository.save(operator);
    log.info("Created operator {}", saved.getId());
    return saved;
  }
}
