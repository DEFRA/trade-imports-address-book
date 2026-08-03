package uk.gov.defra.trade.imports.addressbook.address;

import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.defra.trade.imports.addressbook.exceptions.NotFoundException;
import uk.gov.defra.trade.imports.addressbook.exceptions.Problem;
import uk.gov.defra.trade.imports.addressbook.exceptions.ValidationProblem;
import uk.gov.defra.trade.imports.addressbook.filter.IdentityHeaderFilter;

/**
 * REST API for an organisation's address book. Every operation is path-scoped to an
 * {@code orgId} and authorised against the caller's trusted {@code Trade-Imports-Organisation-Id}
 * forwarded header (cv-010, cv-040): the path is never trusted alone. The
 * {@code IdentityHeaderFilter} fails fast on a missing or blank header, so it is present and
 * non-blank by the time a handler runs; {@link #authoriseOrg} then rejects a caller whose session
 * organisation disagrees with the path {@code orgId} with a 404 (no existence disclosure).
 */
@RestController
@RequestMapping("/organisation/{orgId}/addresses")
@Tag(name = "addresses", description = "Address book, scoped to the caller's organisation")
@SecurityRequirement(name = "Trade-Imports-Organisation-Id")
@Slf4j
@RequiredArgsConstructor
public class OperatorController {

  private final OperatorService operatorService;
  private final OperatorMapper operatorMapper;

  /**
   * Authorises the caller's forwarded session organisation against the path {@code orgId}. A
   * mismatch throws {@link NotFoundException} &rarr; 404 (never 403): the API must not disclose that
   * another organisation's addresses exist (cv-040). The filter guarantees {@code sessionOrg} is
   * present and non-blank before any handler — and therefore before this guard — runs.
   *
   * @param orgId the organisation id from the path
   * @param sessionOrg the caller's organisation id from the forwarded identity header
   * @throws NotFoundException if the session organisation does not match the path {@code orgId}
   */
  private static void authoriseOrg(String orgId, String sessionOrg) {
    if (!sessionOrg.equals(orgId)) {
      throw new NotFoundException("Address not found");
    }
  }

  /**
   * Lists the caller's ACTIVE addresses, newest first, one page at a time. DELETED tombstones are
   * excluded. {@code page} is 1-based (default 1); an out-of-range or non-numeric value is a 400
   * bad-request problem with no {@code errors} map. The page size is a server-side config (cv-025),
   * not a request parameter. The response is a top-level object ({@code items} + pagination
   * metadata), never a bare array.
   *
   * @param orgId the organisation scope from the path, authorised against the identity header
   * @param sessionOrg the caller's organisation id, from {@code Trade-Imports-Organisation-Id}
   * @param page the 1-based page number (default 1)
   * @param q optional case-insensitive partial-word search over name/townOrCity/postcode
   * @param countryCode optional ISO alpha-2 country code (FE-resolved, cv-048)
   * @return 200 with one page of ACTIVE addresses
   */
  @GetMapping
  @Operation(
      operationId = "list-addresses",
      summary = "List the caller's ACTIVE addresses (paginated, searchable)")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "One page of the caller's ACTIVE addresses",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = OperatorPageResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "An out-of-range or non-numeric page, or a missing org header",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = Problem.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Path orgId does not match the caller's organisation header",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = Problem.class))),
    @ApiResponse(
        responseCode = "500",
        description = "Unexpected server error",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = Problem.class)))
  })
  @Timed("controller.listOperators.time")
  public OperatorPageResponse list(
      @PathVariable String orgId,
      @RequestHeader(IdentityHeaderFilter.ORGANISATION_ID_HEADER) String sessionOrg,
      @Parameter(
              description = "1-based page number",
              schema = @Schema(minimum = "1", defaultValue = "1"))
          @RequestParam(defaultValue = "1")
          int page,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String countryCode) {
    authoriseOrg(orgId, sessionOrg);
    log.info("GET addresses - page {}, q present {}, countryCode {}", page, q != null, countryCode);
    return operatorService.list(sessionOrg, page, q, countryCode);
  }

  /**
   * Creates an address in the caller's address book. {@code organisationId} is stamped from the
   * identity header, never from the body; server-assigned fields carried in the body are ignored,
   * not rejected (Zalando default).
   *
   * @param orgId the organisation scope from the path, authorised against the identity header
   * @param sessionOrg the caller's organisation id, from {@code Trade-Imports-Organisation-Id}
   * @param request the validated create body
   * @return 201 with the created address and a {@code Location} header
   */
  @PostMapping
  @Operation(
      operationId = "create-address",
      summary = "Create an address in the caller's address book")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Created",
        headers =
            @Header(
                name = "Location",
                description = "Relative URI of the created address",
                schema = @Schema(type = "string")),
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = OperatorResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Validation error, or a missing identity header",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema =
                    @Schema(
                        type = "object",
                        anyOf = {ValidationProblem.class, Problem.class}))),
    @ApiResponse(
        responseCode = "404",
        description = "Path orgId does not match the caller's organisation header",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = Problem.class))),
    @ApiResponse(
        responseCode = "500",
        description = "Unexpected server error",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = Problem.class)))
  })
  @Timed("controller.createOperator.time")
  public ResponseEntity<OperatorResponse> create(
      @PathVariable String orgId,
      @RequestHeader(IdentityHeaderFilter.ORGANISATION_ID_HEADER) String sessionOrg,
      @Valid @RequestBody AddressRequest request) {
    authoriseOrg(orgId, sessionOrg);
    log.info("POST addresses - creating address");
    Address created = operatorService.create(request, sessionOrg);
    URI location =
        UriComponentsBuilder.fromPath("/organisation/{orgId}/addresses/{operator-id}")
            .buildAndExpand(orgId, created.getId())
            .encode()
            .toUri();
    return ResponseEntity.created(location).body(operatorMapper.toResponse(created));
  }

  /**
   * Fetches one address by id within the caller's organisation scope, tombstones included. A DELETED
   * address is returned with 200 and {@code deleted: true} so the caller can detect a deletion; an
   * unknown id, or one belonging to another organisation, is a 404 (existence is never leaked). A
   * 404 is therefore NOT a deletion signal: only the tombstone is.
   *
   * @param orgId the organisation scope from the path, authorised against the identity header
   * @param sessionOrg the caller's organisation id, from {@code Trade-Imports-Organisation-Id}
   * @param operatorId the opaque address id from the path
   * @return 200 with the address (deleted false or true)
   */
  @GetMapping("/{operator-id}")
  @Operation(operationId = "get-address", summary = "Fetch one address, including tombstones")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "The address (deleted false or true)",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = OperatorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Unknown id, or an id outside the caller's organisation scope",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = Problem.class))),
    @ApiResponse(
        responseCode = "500",
        description = "Unexpected server error",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = Problem.class)))
  })
  @Timed("controller.getOperator.time")
  public OperatorResponse get(
      @PathVariable String orgId,
      @RequestHeader(IdentityHeaderFilter.ORGANISATION_ID_HEADER) String sessionOrg,
      @PathVariable("operator-id") String operatorId) {
    authoriseOrg(orgId, sessionOrg);
    log.info("GET addresses/{}", operatorId);
    Address address =
        operatorService
            .get(operatorId, sessionOrg)
            .orElseThrow(() -> new NotFoundException("Address not found"));
    return operatorMapper.toResponse(address);
  }

  /**
   * Replaces an address's mutable fields (full representation replace). {@code modifiedAt} is bumped
   * on success (audit only). An unknown id, an id outside the caller's organisation, or a
   * soft-deleted tombstone all yield a 404 — tombstones are outside the caller's live set and
   * existence is never leaked.
   *
   * @param orgId the organisation scope from the path, authorised against the identity header
   * @param sessionOrg the caller's organisation id, from {@code Trade-Imports-Organisation-Id}
   * @param operatorId the opaque address id from the path
   * @param request the validated update body
   * @return 200 with the updated address and its bumped {@code modifiedAt}
   */
  @PutMapping("/{operator-id}")
  @Operation(operationId = "update-address", summary = "Replace an address's mutable fields")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Updated address with bumped modifiedAt",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = OperatorResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Validation error, or a missing org header",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema =
                    @Schema(
                        type = "object",
                        anyOf = {ValidationProblem.class, Problem.class}))),
    @ApiResponse(
        responseCode = "404",
        description = "Unknown id, an id outside the caller's organisation scope, or a tombstone",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = Problem.class))),
    @ApiResponse(
        responseCode = "500",
        description = "Unexpected server error",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = Problem.class)))
  })
  @Timed("controller.updateOperator.time")
  public OperatorResponse update(
      @PathVariable String orgId,
      @RequestHeader(IdentityHeaderFilter.ORGANISATION_ID_HEADER) String sessionOrg,
      @PathVariable("operator-id") String operatorId,
      @Valid @RequestBody AddressRequest request) {
    authoriseOrg(orgId, sessionOrg);
    log.info("PUT addresses/{}", operatorId);
    Address updated = operatorService.update(operatorId, request, sessionOrg);
    return operatorMapper.toResponse(updated);
  }

  /**
   * Soft-deletes an address. The document is not removed: {@code status} flips to {@code DELETED}
   * and {@code modifiedAt} is bumped, and the tombstone stays fetchable by id so a deletion is
   * detectable. Idempotent — deleting an already-DELETED address is a 204 with no state change. An
   * unknown id, or one outside the caller's organisation, is a 404 (existence is never leaked). The
   * UI reaches this only via the delete-confirmation page.
   *
   * @param orgId the organisation scope from the path, authorised against the identity header
   * @param sessionOrg the caller's organisation id, from {@code Trade-Imports-Organisation-Id}
   * @param operatorId the opaque address id from the path
   * @return 204 No Content (soft-deleted, or already deleted)
   */
  @DeleteMapping("/{operator-id}")
  @Operation(operationId = "delete-address", summary = "Soft-delete an address (tombstone)")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Soft-deleted (or already deleted — idempotent)"),
    @ApiResponse(
        responseCode = "400",
        description = "Missing org header",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = Problem.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Unknown id, or an id outside the caller's organisation scope",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = Problem.class))),
    @ApiResponse(
        responseCode = "500",
        description = "Unexpected server error",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = Problem.class)))
  })
  @Timed("controller.deleteOperator.time")
  public ResponseEntity<Void> delete(
      @PathVariable String orgId,
      @RequestHeader(IdentityHeaderFilter.ORGANISATION_ID_HEADER) String sessionOrg,
      @PathVariable("operator-id") String operatorId) {
    authoriseOrg(orgId, sessionOrg);
    log.info("DELETE addresses/{}", operatorId);
    operatorService.delete(operatorId, sessionOrg);
    return ResponseEntity.noContent().build();
  }
}
