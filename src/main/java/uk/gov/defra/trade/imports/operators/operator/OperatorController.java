package uk.gov.defra.trade.imports.operators.operator;

import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.defra.trade.imports.operators.exceptions.NotFoundException;
import uk.gov.defra.trade.imports.operators.exceptions.Problem;
import uk.gov.defra.trade.imports.operators.exceptions.ValidationProblem;

/**
 * REST API for an organisation's operator address book (design §1.1, contract
 * {@code /operators}). Every operation is scoped to the caller's {@code crn}, taken from the
 * trusted {@code Trade-Imports-Crn} forwarded header (c-001); {@code Trade-Imports-Organisation-Id}
 * is additionally required on create. The {@code IdentityHeaderFilter} fails fast on a missing or
 * blank header, so both are present and non-blank by the time a handler runs.
 */
@RestController
@RequestMapping("/operators")
@Tag(name = "operators", description = "Address-book operators, scoped to the caller's organisation")
@Slf4j
@RequiredArgsConstructor
public class OperatorController {

  private static final String CRN_HEADER = "Trade-Imports-Crn";
  private static final String ORGANISATION_ID_HEADER = "Trade-Imports-Organisation-Id";

  private final OperatorService operatorService;

  /**
   * Lists the caller's ACTIVE operators, newest first, one page at a time. DELETED tombstones are
   * excluded (design §1.2). {@code page} is 1-based (default 1) and {@code page_size} defaults to 25
   * (EUDPA-185.AC4), max 100; an out-of-range or non-numeric value is a 400 bad-request problem with
   * no {@code errors} map. The response is a top-level object ({@code items} + pagination metadata),
   * never a bare array. Server-side search and the {@code operator_type} filter arrive in inc-010.
   *
   * @param crn the caller's company reference number, from {@code Trade-Imports-Crn}
   * @param page the 1-based page number (default 1)
   * @param pageSize the page size (default 25, max 100)
   * @return 200 with one page of ACTIVE operators
   */
  @GetMapping
  @Operation(
      operationId = "list-operators",
      summary = "List the caller's ACTIVE operators (paginated)")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "One page of the caller's ACTIVE operators"),
    @ApiResponse(
        responseCode = "400",
        description = "Out-of-range or non-numeric pagination parameters, or a missing crn header",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = Problem.class)))
  })
  @Timed("controller.listOperators.time")
  public OperatorPageResponse list(
      @RequestHeader(CRN_HEADER) String crn,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(name = "page_size", defaultValue = "25") int pageSize) {
    log.info("GET /operators - page {} size {}", page, pageSize);
    return operatorService.list(crn, page, pageSize);
  }

  /**
   * Creates an operator in the caller's address book. {@code crn} and {@code organisation_id} are
   * stamped from the identity headers, never from the body; server-assigned fields carried in the
   * body are ignored, not rejected (Zalando default).
   *
   * @param crn the caller's company reference number, from {@code Trade-Imports-Crn}
   * @param organisationId the caller's organisation id, from {@code Trade-Imports-Organisation-Id}
   * @param request the validated create body
   * @return 201 with the created operator and a {@code Location} header
   */
  @PostMapping
  @Operation(
      operationId = "create-operator",
      summary = "Create an operator in the caller's address book")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Created"),
    @ApiResponse(
        responseCode = "400",
        description = "Validation error, or a missing identity header",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(anyOf = {ValidationProblem.class, Problem.class})))
  })
  @Timed("controller.createOperator.time")
  public ResponseEntity<OperatorResponse> create(
      @RequestHeader(CRN_HEADER) String crn,
      @RequestHeader(ORGANISATION_ID_HEADER) String organisationId,
      @Valid @RequestBody OperatorRequest request) {
    log.info("POST /operators - creating {} operator", request.operatorType());
    Operator created = operatorService.create(request, crn, organisationId);
    URI location = URI.create("/operators/" + created.getId());
    return ResponseEntity.created(location).body(OperatorMapper.toResponse(created));
  }

  /**
   * Fetches one operator by id within the caller's crn scope, tombstones included. A DELETED
   * operator is returned with 200 and {@code status: DELETED} so the caller can detect a deletion
   * (c-003 / EUDPA-293.AC2); an unknown id, or one belonging to another crn, is a 404 (c-001 —
   * existence is never leaked). A 404 is therefore NOT a deletion signal: only the tombstone is.
   *
   * @param crn the caller's company reference number, from {@code Trade-Imports-Crn}
   * @param operatorId the opaque operator id from the path
   * @return 200 with the operator (status ACTIVE or DELETED)
   */
  @GetMapping("/{operator-id}")
  @Operation(
      operationId = "get-operator",
      summary = "Fetch one operator, including tombstones")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "The operator (status ACTIVE or DELETED)"),
    @ApiResponse(
        responseCode = "404",
        description = "Unknown id, or an id outside the caller's crn scope",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = Problem.class)))
  })
  @Timed("controller.getOperator.time")
  public OperatorResponse get(
      @RequestHeader(CRN_HEADER) String crn, @PathVariable("operator-id") String operatorId) {
    log.info("GET /operators/{}", operatorId);
    Operator operator =
        operatorService
            .get(operatorId, crn)
            .orElseThrow(() -> new NotFoundException("Operator not found"));
    return OperatorMapper.toResponse(operator);
  }

  /**
   * Replaces an operator's mutable fields. {@code operator_type} is immutable: a body whose type
   * differs from the stored value is rejected with a 400 validation error keyed
   * {@code operator_type} (not silently ignored). {@code modified_at} is bumped on success (audit
   * only — c-017; a notification's embedded operator copy is not refreshed by the edit). An unknown
   * id, an id outside the caller's crn, or a soft-deleted tombstone all yield a 404 — tombstones are
   * outside the caller's live set and existence is never leaked.
   *
   * @param crn the caller's company reference number, from {@code Trade-Imports-Crn}
   * @param operatorId the opaque operator id from the path
   * @param request the validated update body
   * @return 200 with the updated operator and its bumped {@code modified_at}
   */
  @PutMapping("/{operator-id}")
  @Operation(
      operationId = "update-operator",
      summary = "Replace an operator's mutable fields")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Updated operator with bumped modified_at"),
    @ApiResponse(
        responseCode = "400",
        description = "Validation error (including an operator_type change), or a missing crn header",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(anyOf = {ValidationProblem.class, Problem.class}))),
    @ApiResponse(
        responseCode = "404",
        description = "Unknown id, an id outside the caller's crn scope, or a tombstone",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = Problem.class)))
  })
  @Timed("controller.updateOperator.time")
  public OperatorResponse update(
      @RequestHeader(CRN_HEADER) String crn,
      @PathVariable("operator-id") String operatorId,
      @Valid @RequestBody OperatorRequest request) {
    log.info("PUT /operators/{}", operatorId);
    Operator updated = operatorService.update(operatorId, request, crn);
    return OperatorMapper.toResponse(updated);
  }

  /**
   * Soft-deletes an operator. The document is not removed: {@code status} flips to {@code DELETED}
   * and {@code modified_at} is bumped (c-003), and the tombstone stays fetchable by id so a deletion
   * is detectable (c-018 / EUDPA-293.AC2). Idempotent — deleting an already-DELETED operator is a
   * 204 with no state change. An unknown id, or one outside the caller's crn, is a 404 (existence is
   * never leaked — c-001). The UI reaches this only via the delete-confirmation page (c-010).
   *
   * @param crn the caller's company reference number, from {@code Trade-Imports-Crn}
   * @param operatorId the opaque operator id from the path
   * @return 204 No Content (soft-deleted, or already deleted)
   */
  @DeleteMapping("/{operator-id}")
  @Operation(
      operationId = "delete-operator",
      summary = "Soft-delete an operator (tombstone)")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Soft-deleted (or already deleted — idempotent)"),
    @ApiResponse(
        responseCode = "400",
        description = "Missing crn header",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = Problem.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Unknown id, or an id outside the caller's crn scope",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = Problem.class)))
  })
  @Timed("controller.deleteOperator.time")
  public ResponseEntity<Void> delete(
      @RequestHeader(CRN_HEADER) String crn, @PathVariable("operator-id") String operatorId) {
    log.info("DELETE /operators/{}", operatorId);
    operatorService.delete(operatorId, crn);
    return ResponseEntity.noContent().build();
  }
}
