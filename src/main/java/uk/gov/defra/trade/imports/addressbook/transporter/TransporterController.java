package uk.gov.defra.trade.imports.addressbook.transporter;

import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.defra.trade.imports.addressbook.ListPaging;
import uk.gov.defra.trade.imports.addressbook.OrganisationScope;
import uk.gov.defra.trade.imports.addressbook.exceptions.NotFoundException;
import uk.gov.defra.trade.imports.addressbook.exceptions.Problem;
import uk.gov.defra.trade.imports.addressbook.exceptions.ValidationProblem;
import uk.gov.defra.trade.imports.addressbook.filter.IdentityHeaderFilter;

/**
 * REST API for an organisation's transporters. Every operation is path-scoped to an {@code orgId}
 * and authorised against the caller's trusted {@code Trade-Imports-Organisation-Id} forwarded
 * header: the path is never trusted alone. A caller whose organisation disagrees with the path
 * {@code orgId} gets a 404, so another organisation's transporters are never disclosed.
 */
@RestController
@RequestMapping("/organisation/{orgId}/transporters")
@Tag(name = "transporters", description = "An organisation's transporters, scoped to its address book")
@SecurityRequirement(name = "Trade-Imports-Organisation-Id")
@Slf4j
@RequiredArgsConstructor
public class TransporterController {

  private static final String RESOURCE = "transporter";

  private final TransporterService transporterService;
  private final TransporterMapper transporterMapper;

  /**
   * Lists the caller's live transporters, newest first, one page at a time. {@code page} is
   * 1-based (default 1) and {@code pageSize} runs from {@link ListPaging#MIN_PAGE_SIZE} to {@link
   * ListPaging#MAX_PAGE_SIZE} (default {@link ListPaging#DEFAULT_PAGE_SIZE}); an out-of-range or
   * non-numeric value for either is a 400 bad-request problem with no {@code errors} map. The
   * response is a top-level object ({@code items} + pagination metadata), never a bare array.
   *
   * @param orgId the organisation scope from the path, authorised against the identity header
   * @param organisationId the caller's organisation id, from {@code Trade-Imports-Organisation-Id}
   * @param page the 1-based page number (default 1)
   * @param pageSize the number of transporters a page, or null for the default
   * @param q the optional search term, as its {@code @Parameter} describes
   * @param countryCodes the optional country codes, as their {@code @Parameter} describes
   * @param types the optional types to keep, as their {@code @Parameter} describes
   * @param searchTypes the optional search types, as their {@code @Parameter} describes
   * @return 200 with one page of live transporters
   */
  @GetMapping
  @Operation(
      operationId = "list-transporters",
      summary = "List the caller's live transporters (paginated, searchable)")
  @ApiResponse(
      responseCode = "200",
      description = "One page of the caller's live transporters",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = TransporterPageResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description =
          "An out-of-range or non-numeric page or page size, an unknown type or search type, or"
              + " a missing org header",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = Problem.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Path orgId does not match the caller's organisation header",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = Problem.class)))
  @ApiResponse(
      responseCode = "500",
      description = "Unexpected server error",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = Problem.class)))
  @Timed("controller.listTransporters.time")
  public TransporterPageResponse list(
      @PathVariable String orgId,
      @RequestHeader(IdentityHeaderFilter.ORGANISATION_ID_HEADER) String organisationId,
      @Parameter(
              description = "1-based page number",
              schema = @Schema(type = "integer", format = "int32", minimum = "1", defaultValue = "1"))
          @RequestParam(defaultValue = "1")
          int page,
      @Parameter(
              description =
                  "Transporters a page, from "
                      + ListPaging.MIN_PAGE_SIZE
                      + " to "
                      + ListPaging.MAX_PAGE_SIZE
                      + " (default "
                      + ListPaging.DEFAULT_PAGE_SIZE
                      + ")",
              schema =
                  @Schema(
                      type = "integer",
                      format = "int32",
                      minimum = "" + ListPaging.MIN_PAGE_SIZE,
                      maximum = "" + ListPaging.MAX_PAGE_SIZE,
                      defaultValue = "" + ListPaging.DEFAULT_PAGE_SIZE))
          @RequestParam(required = false)
          Integer pageSize,
      @Parameter(
              description =
                  "Case-insensitive partial-word search over name, address line 1, address line"
                      + " 2, town or city, county, postcode and approval number. A transporter"
                      + " matching q, a countryCode or a searchType is returned.")
          @RequestParam(required = false)
          String q,
      @Parameter(
              description =
                  "ISO alpha-2 or alpha-2-plus-subdivision country codes. Repeatable. A"
                      + " transporter in any of them matches the search, as one matching q or a"
                      + " searchType does.")
          @RequestParam(name = "countryCode", required = false)
          List<String> countryCodes,
      @Parameter(
              description =
                  "Keeps only transporters of any of these types. Repeatable. Narrows the search"
                      + " (q, countryCode and searchType). An unknown value is refused with 400.",
              array = @ArraySchema(schema = @Schema(implementation = TransporterType.class)))
          @RequestParam(name = "type", required = false)
          List<TransporterType> types,
      @Parameter(
              description =
                  "Types that make a transporter match the search: a transporter of any of them"
                      + " matches, as one matching q or countryCode does. Repeatable. An unknown"
                      + " value is refused with 400.",
              array = @ArraySchema(schema = @Schema(implementation = TransporterType.class)))
          @RequestParam(name = "searchType", required = false)
          List<TransporterType> searchTypes) {
    OrganisationScope.authorise(
        RESOURCE, null, orgId, organisationId, TransporterService.TRANSPORTER_NOT_FOUND);
    log.info(
        "GET transporters - page {}, pageSize {}, q present {}, countryCodes {}, types {},"
            + " searchTypes {}",
        page,
        pageSize,
        q != null,
        countryCodes,
        types,
        searchTypes);
    return transporterService.list(
        organisationId,
        page,
        pageSize,
        new RequestedTransporterListFilters(q, countryCodes, types, searchTypes));
  }

  /**
   * Creates a transporter in the caller's address book. {@code organisationId} is stamped from the
   * identity header, never from the body; server-assigned fields carried in the body are ignored,
   * not rejected.
   *
   * @param orgId the organisation scope from the path, authorised against the identity header
   * @param organisationId the caller's organisation id, from {@code Trade-Imports-Organisation-Id}
   * @param request the validated create body
   * @return 201 with the created transporter and a {@code Location} header
   */
  @PostMapping
  @Operation(
      operationId = "create-transporter",
      summary = "Create a transporter in the caller's address book")
  @ApiResponse(
      responseCode = "201",
      description = "Created",
      headers =
          @Header(
              name = "Location",
              description = "Absolute URI of the created transporter",
              schema = @Schema(type = "string")),
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = TransporterResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Validation error, or a missing identity header",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema =
                  @Schema(
                      type = "object",
                      anyOf = {ValidationProblem.class, Problem.class})))
  @ApiResponse(
      responseCode = "404",
      description = "Path orgId does not match the caller's organisation header",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = Problem.class)))
  @ApiResponse(
      responseCode = "500",
      description = "Unexpected server error",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = Problem.class)))
  @Timed("controller.createTransporter.time")
  public ResponseEntity<TransporterResponse> create(
      @PathVariable String orgId,
      @RequestHeader(IdentityHeaderFilter.ORGANISATION_ID_HEADER) String organisationId,
      @Valid @RequestBody TransporterRequest request) {
    OrganisationScope.authorise(
        RESOURCE, null, orgId, organisationId, TransporterService.TRANSPORTER_NOT_FOUND);
    log.info("POST transporters - creating transporter");
    Transporter created = transporterService.create(request, organisationId);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{transporter-id}")
            .buildAndExpand(created.getId())
            .encode()
            .toUri();
    return ResponseEntity.created(location).body(transporterMapper.toResponse(created));
  }

  /**
   * Fetches one transporter by id within the caller's organisation scope, tombstones included. A
   * DELETED transporter is returned with 200 and {@code deleted: true} so the caller can detect a
   * deletion; an unknown id, or one belonging to another organisation, is a 404 (existence is
   * never leaked). A 404 is therefore NOT a deletion signal: only the tombstone is.
   *
   * @param orgId the organisation scope from the path, authorised against the identity header
   * @param organisationId the caller's organisation id, from {@code Trade-Imports-Organisation-Id}
   * @param transporterId the opaque transporter id from the path
   * @return 200 with the transporter (deleted false or true)
   */
  @GetMapping("/{transporter-id}")
  @Operation(operationId = "get-transporter", summary = "Fetch one transporter, including tombstones")
  @ApiResponse(
      responseCode = "200",
      description = "The transporter (deleted false or true)",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = TransporterResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Unknown id, or an id outside the caller's organisation scope",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = Problem.class)))
  @ApiResponse(
      responseCode = "500",
      description = "Unexpected server error",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = Problem.class)))
  @Timed("controller.getTransporter.time")
  public TransporterResponse get(
      @PathVariable String orgId,
      @RequestHeader(IdentityHeaderFilter.ORGANISATION_ID_HEADER) String organisationId,
      @PathVariable("transporter-id") String transporterId) {
    OrganisationScope.authorise(
        RESOURCE,
        transporterId,
        orgId,
        organisationId,
        TransporterService.TRANSPORTER_NOT_FOUND);
    log.info("GET transporters/{}", transporterId);
    Transporter transporter =
        transporterService
            .get(transporterId, organisationId)
            .orElseThrow(() -> new NotFoundException(TransporterService.TRANSPORTER_NOT_FOUND));
    return transporterMapper.toResponse(transporter);
  }

  /**
   * Replaces a transporter's mutable fields (full representation replace). {@code modifiedAt} is
   * bumped on success (audit only). An unknown id, an id outside the caller's organisation, or a
   * soft-deleted tombstone all yield a 404 — tombstones are outside the caller's live set and
   * existence is never leaked.
   *
   * @param orgId the organisation scope from the path, authorised against the identity header
   * @param organisationId the caller's organisation id, from {@code Trade-Imports-Organisation-Id}
   * @param transporterId the opaque transporter id from the path
   * @param request the validated update body
   * @return 200 with the updated transporter and its bumped {@code modifiedAt}
   */
  @PutMapping("/{transporter-id}")
  @Operation(operationId = "update-transporter", summary = "Replace a transporter's mutable fields")
  @ApiResponse(
      responseCode = "200",
      description = "Updated transporter with bumped modifiedAt",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = TransporterResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Validation error, or a missing org header",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema =
                  @Schema(
                      type = "object",
                      anyOf = {ValidationProblem.class, Problem.class})))
  @ApiResponse(
      responseCode = "404",
      description = "Unknown id, an id outside the caller's organisation scope, or a tombstone",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = Problem.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Concurrent update conflict",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = Problem.class)))
  @ApiResponse(
      responseCode = "500",
      description = "Unexpected server error",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = Problem.class)))
  @Timed("controller.updateTransporter.time")
  public TransporterResponse update(
      @PathVariable String orgId,
      @RequestHeader(IdentityHeaderFilter.ORGANISATION_ID_HEADER) String organisationId,
      @PathVariable("transporter-id") String transporterId,
      @Valid @RequestBody TransporterRequest request) {
    OrganisationScope.authorise(
        RESOURCE,
        transporterId,
        orgId,
        organisationId,
        TransporterService.TRANSPORTER_NOT_FOUND);
    log.info("PUT transporters/{}", transporterId);
    Transporter updated = transporterService.update(transporterId, request, organisationId);
    return transporterMapper.toResponse(updated);
  }

  /**
   * Soft-deletes a transporter. The document is not removed: {@code status} flips to {@code
   * DELETED} and {@code modifiedAt} is bumped, and the tombstone stays fetchable by id so a
   * deletion is detectable. Idempotent — deleting an already-DELETED transporter is a 204 with no
   * state change. An unknown id, or one outside the caller's organisation, is a 404 (existence is
   * never leaked).
   *
   * @param orgId the organisation scope from the path, authorised against the identity header
   * @param organisationId the caller's organisation id, from {@code Trade-Imports-Organisation-Id}
   * @param transporterId the opaque transporter id from the path
   * @return 204 No Content (soft-deleted, or already deleted)
   */
  @DeleteMapping("/{transporter-id}")
  @Operation(operationId = "delete-transporter", summary = "Soft-delete a transporter (tombstone)")
  @ApiResponse(responseCode = "204", description = "Soft-deleted (or already deleted — idempotent)")
  @ApiResponse(
      responseCode = "400",
      description = "Missing org header",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = Problem.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Unknown id, or an id outside the caller's organisation scope",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = Problem.class)))
  @ApiResponse(
      responseCode = "500",
      description = "Unexpected server error",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = Problem.class)))
  @Timed("controller.deleteTransporter.time")
  public ResponseEntity<Void> delete(
      @PathVariable String orgId,
      @RequestHeader(IdentityHeaderFilter.ORGANISATION_ID_HEADER) String organisationId,
      @PathVariable("transporter-id") String transporterId) {
    OrganisationScope.authorise(
        RESOURCE,
        transporterId,
        orgId,
        organisationId,
        TransporterService.TRANSPORTER_NOT_FOUND);
    log.info("DELETE transporters/{}", transporterId);
    transporterService.delete(transporterId, organisationId);
    return ResponseEntity.noContent().build();
  }
}
