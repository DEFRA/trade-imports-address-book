package uk.gov.defra.trade.imports.operators.operator;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.defra.trade.imports.operators.exceptions.NotFoundException;
import uk.gov.defra.trade.imports.operators.exceptions.ValidationException;

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

  /**
   * Replaces an operator's mutable fields within the caller's crn scope (design §1.5). The wire can
   * never move the server-owned fields: {@code id}, {@code crn}, {@code organisationId},
   * {@code status}, {@code createdAt} and {@code operatorType} are all preserved from the stored
   * entity; {@code modifiedAt} is bumped by auditing on save (audit only — c-017: nothing re-syncs
   * off it, and a notification's embedded operator copy is not refreshed by an edit).
   *
   * <p>{@code operatorType} is immutable after create: a request whose type differs from the stored
   * value is <strong>rejected</strong> with a 400 validation error keyed {@code operator_type} — the
   * design locks reject over silent-ignore because a silent ignore hides a client bug and is
   * undiagnosable from logs. An unknown id, an id owned by another crn, or a soft-deleted tombstone
   * are all outside the caller's live set and yield a 404 (existence is never leaked; the tombstone
   * is immutable).
   *
   * @param id the operator id
   * @param request the validated update body (client-supplied fields only)
   * @param crn the caller's company reference number, from the identity header
   * @return the updated operator, with its bumped {@code modifiedAt}
   * @throws NotFoundException if the id is unknown, out of scope, or a tombstone
   * @throws ValidationException if the request attempts to change {@code operatorType}
   */
  public Operator update(String id, OperatorRequest request, String crn) {
    Operator existing =
        repository
            .findByIdAndCrn(id, crn)
            .filter(operator -> operator.getStatus() != OperatorStatus.DELETED)
            .orElseThrow(() -> new NotFoundException("Operator not found"));

    if (request.operatorType() != existing.getOperatorType()) {
      throw new ValidationException(
          Map.of("operator_type", List.of("Operator type cannot be changed")));
    }

    existing.setName(request.name());
    existing.setAddressLine1(request.addressLine1());
    existing.setAddressLine2(request.addressLine2());
    existing.setTown(request.town());
    existing.setCounty(request.county());
    existing.setPostcode(request.postcode());
    existing.setCountry(request.country());
    existing.setTelephone(request.telephone());
    existing.setEmail(request.email());
    existing.setApprovalNumber(request.approvalNumber());
    existing.setTransporterCategory(request.transporterCategory());

    Operator saved = repository.save(existing);
    log.info("Updated operator {}", saved.getId());
    return saved;
  }

  /**
   * Soft-deletes an operator within the caller's crn scope (design §1.2). The document is
   * <strong>not</strong> removed: its {@code status} flips to {@code DELETED} and {@code modifiedAt}
   * is bumped on save. The tombstone is load-bearing (c-003 / c-018) — it stays fetchable by id so
   * the EUDPA-293.AC2 existence check can tell "deleted" (200 + DELETED) from "unknown or not yours"
   * (404); a hard delete would collapse both into a 404 and make deletion undetectable.
   *
   * <p>Idempotent: deleting an operator that is already a tombstone is a no-op — no re-save, so
   * {@code modifiedAt} is not bumped a second time and the state is unchanged. An unknown id, or an
   * id owned by another crn, is empty in the caller's scope and yields a 404 (existence is never
   * leaked — c-001).
   *
   * @param id the operator id
   * @param crn the caller's company reference number, from the identity header
   * @throws NotFoundException if the id is unknown or out of the caller's crn scope
   */
  public void delete(String id, String crn) {
    Operator existing =
        repository
            .findByIdAndCrn(id, crn)
            .orElseThrow(() -> new NotFoundException("Operator not found"));

    if (existing.getStatus() == OperatorStatus.DELETED) {
      return;
    }

    existing.setStatus(OperatorStatus.DELETED);
    repository.save(existing);
    log.info("Soft-deleted operator {}", existing.getId());
  }
}
