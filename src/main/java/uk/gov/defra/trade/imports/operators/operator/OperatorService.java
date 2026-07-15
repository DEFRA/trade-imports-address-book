package uk.gov.defra.trade.imports.operators.operator;

import java.util.Optional;
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

  /**
   * Fetches one operator by id within the caller's crn scope, tombstones included. A
   * soft-deleted operator is returned WITH {@code status DELETED} so a consumer can tell "deleted"
   * (present, DELETED) from "unknown or not yours" (empty &rarr; 404) — c-003 / EUDPA-293.AC2. An id
   * outside the caller's crn is indistinguishable from an unknown id: both are empty, so 404 leaks
   * no existence across organisations (c-001).
   *
   * @param id the operator id
   * @param crn the caller's company reference number, from the identity header
   * @return the operator if it exists in the caller's scope, otherwise empty
   */
  public Optional<Operator> get(String id, String crn) {
    return repository.findByIdAndCrn(id, crn);
  }
}
