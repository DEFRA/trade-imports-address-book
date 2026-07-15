package uk.gov.defra.trade.imports.operators.operator;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import uk.gov.defra.trade.imports.operators.exceptions.BadRequestException;
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

  private static final int MAX_PAGE_SIZE = 100;

  private final OperatorRepository repository;
  private final MeterRegistry meterRegistry;

  /**
   * One page of the caller's ACTIVE operators, newest first, optionally searched and type-filtered
   * (design §1.2/§1.3, contract {@code list-operators}). Scoped to {@code crn}; DELETED tombstones
   * are excluded because the query pins {@code status ACTIVE}. {@code page} is 1-based and translated
   * to Spring Data's 0-based index; the sort is {@code created_at} descending, served by the
   * {@code crn_status_type_created} index. {@code total_pages} is derived here, not in the
   * controller.
   *
   * <p><strong>Sentinel convention (documented here, in one place).</strong> The repository's single
   * {@link OperatorRepository#search} method covers all four combinations of the two optional filters
   * by never letting them be "absent" at the query layer:
   *
   * <ul>
   *   <li>absent {@code operatorType} &rarr; the {@code $in} is passed <em>all seven</em>
   *       {@link OperatorType} values, so it matches every operator;
   *   <li>absent {@code q} &rarr; the search regex is the empty string {@code ""}, which matches
   *       everything.
   * </ul>
   *
   * <p>{@code q} is {@link Pattern#quote quoted} before it reaches Mongo, so a user's regex
   * metacharacters (e.g. {@code .*} or {@code (}) are matched literally — never compiled as a
   * pattern. This is the c-012 server-side-only search and the c-004 country match is against the
   * stored display-name string (there is no code&lt;-&gt;name conversion anywhere).
   *
   * <p>An out-of-range {@code page} (&lt; 1) or {@code pageSize} (&lt; 1 or &gt; {@value
   * #MAX_PAGE_SIZE}) is a {@link BadRequestException} — a 400 bad-request problem with no
   * {@code errors} map, since these are malformed query parameters, not body-field validation
   * failures.
   *
   * <p>The {@code OperatorListQuery} timer wraps the query, tagged {@code filtered=true} when a
   * search or type filter is applied and {@code false} otherwise (§6) — the tripwire that measures
   * the bounded-scan assumption.
   *
   * @param crn the caller's company reference number, from the identity header
   * @param q the free-text search, or {@code null} when absent
   * @param operatorType the exact operator-type filter, or {@code null} when absent
   * @param page the 1-based page number
   * @param pageSize the page size (1..{@value #MAX_PAGE_SIZE})
   * @return one page of ACTIVE operators with pagination metadata
   * @throws BadRequestException if {@code page} or {@code pageSize} is out of range
   */
  public OperatorPageResponse list(
      String crn, String q, OperatorType operatorType, int page, int pageSize) {
    if (page < 1) {
      throw new BadRequestException("page must be 1 or greater");
    }
    if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      throw new BadRequestException("page_size must be between 1 and " + MAX_PAGE_SIZE);
    }

    List<OperatorType> types =
        operatorType == null ? List.of(OperatorType.values()) : List.of(operatorType);
    String quotedRegex = q == null ? "" : Pattern.quote(q);
    boolean filtered = operatorType != null || (q != null && !q.isBlank());

    Pageable pageable =
        PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

    Timer.Sample sample = Timer.start(meterRegistry);
    Page<Operator> result = repository.search(crn, types, quotedRegex, pageable);
    sample.stop(
        Timer.builder("OperatorListQuery")
            .tag("filtered", Boolean.toString(filtered))
            .register(meterRegistry));

    List<OperatorResponse> items =
        result.getContent().stream().map(OperatorMapper::toResponse).toList();
    return new OperatorPageResponse(
        items, page, pageSize, (int) result.getTotalElements(), result.getTotalPages());
  }

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
