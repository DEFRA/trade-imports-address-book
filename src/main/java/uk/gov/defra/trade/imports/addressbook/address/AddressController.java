package uk.gov.defra.trade.imports.addressbook.address;

import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import uk.gov.defra.trade.imports.addressbook.exceptions.NotFoundException;
import uk.gov.defra.trade.imports.addressbook.exceptions.Problem;
import uk.gov.defra.trade.imports.addressbook.exceptions.ValidationProblem;
import uk.gov.defra.trade.imports.addressbook.filter.IdentityHeaderFilter;

/**
 * REST API for an organisation's address book. Every operation is path-scoped to an
 * {@code orgId} and authorised against the caller's trusted {@code Trade-Imports-Organisation-Id}
 * forwarded header: the path is never trusted alone. A caller whose organisation disagrees with
 * the path {@code orgId} gets a 404, so another organisation's addresses are never disclosed.
 */
@RestController
@RequestMapping("/organisation/{orgId}/addresses")
@Tag(name = "addresses", description = "Address book, scoped to the caller's organisation")
@SecurityRequirement(name = "Trade-Imports-Organisation-Id")
@Slf4j
@RequiredArgsConstructor
public class AddressController {

  private final AddressService addressService;
  private final AddressMapper addressMapper;

  private static void authoriseOrg(String orgId, String organisationId) {
    if (!organisationId.equals(orgId)) {
      throw new NotFoundException("Address not found");
    }
  }

  /**
   * Lists the caller's live addresses, newest first, one page at a time. {@code page} is 1-based
   * (default 1) and {@code pageSize} runs from {@link AddressService#MIN_PAGE_SIZE} to {@link
   * AddressService#MAX_PAGE_SIZE} (default {@link AddressService#DEFAULT_PAGE_SIZE}); an
   * out-of-range or non-numeric value for either is a 400 bad-request problem with no {@code errors} map. The
   * response is a top-level object ({@code items} + pagination metadata), never a bare array.
   *
   * @param orgId the organisation scope from the path, authorised against the identity header
   * @param organisationId the caller's organisation id, from {@code Trade-Imports-Organisation-Id}
   * @param page the 1-based page number (default 1)
   * @param pageSize the number of addresses a page, or null for the default
   * @param q optional case-insensitive partial-word search over name, address line 1, address line
   *     2, town or city, county and postcode
   * @param countryCodes optional ISO alpha-2 country codes; an address in any of them matches. With
   *     {@code q} or the search uses, an address matching {@code q}, one of these codes or one of
   *     the search uses is returned
   * @param addressType the address type whose uses the listing filters by; {@code
   *     ORIGIN_OR_CONSIGNOR} also lists an address with no uses; narrows the search ({@code q},
   *     {@code countryCode} and {@code searchUse}) and combines with {@code use}; an unknown value
   *     is refused with 400
   * @param use optional use an address must hold; narrows the search ({@code q}, {@code
   *     countryCode} and {@code searchUse}) and combines with the address type; an unknown value
   *     is refused with 400
   * @param searchUses optional uses that make an address match the search, as {@code q} and the
   *     country codes do; an unknown value is refused with 400
   * @return 200 with one page of live addresses
   */
  @GetMapping
  @Operation(
      operationId = "list-addresses",
      summary = "List the caller's live addresses (paginated, searchable)")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "One page of the caller's live addresses",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = AddressPageResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "An out-of-range or non-numeric page or page size, an unknown address type, use or"
                + " search use, or a missing org header",
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
  public AddressPageResponse list(
      @PathVariable String orgId,
      @RequestHeader(IdentityHeaderFilter.ORGANISATION_ID_HEADER) String organisationId,
      @Parameter(
              description = "1-based page number",
              schema = @Schema(type = "integer", format = "int32", minimum = "1", defaultValue = "1"))
          @RequestParam(defaultValue = "1")
          int page,
      @Parameter(
              description =
                  "Addresses a page, from "
                      + AddressService.MIN_PAGE_SIZE
                      + " to "
                      + AddressService.MAX_PAGE_SIZE
                      + " (default "
                      + AddressService.DEFAULT_PAGE_SIZE
                      + ")",
              schema =
                  @Schema(
                      type = "integer",
                      format = "int32",
                      minimum = "" + AddressService.MIN_PAGE_SIZE,
                      maximum = "" + AddressService.MAX_PAGE_SIZE,
                      defaultValue = "" + AddressService.DEFAULT_PAGE_SIZE))
          @RequestParam(required = false)
          Integer pageSize,
      @RequestParam(required = false) String q,
      @RequestParam(name = "countryCode", required = false) List<String> countryCodes,
      @Parameter(
              description =
                  "The address type to list. The listing returns addresses holding any of the"
                      + " type's uses; ORIGIN_OR_CONSIGNOR also lists an address with no uses."
                      + " Narrows the search (q, countryCode and searchUse) and combines with use"
                      + " (AND). An unknown value is refused with 400.",
              schema = @Schema(implementation = AddressType.class))
          @RequestParam(required = false)
          AddressType addressType,
      @Parameter(
              description =
                  "Keeps only addresses holding this use. Narrows the search (q, countryCode and"
                      + " searchUse) and combines with addressType (AND); an address with no uses"
                      + " never matches. An unknown value is refused with 400.",
              schema = @Schema(implementation = AddressUse.class))
          @RequestParam(required = false)
          AddressUse use,
      @Parameter(
              description =
                  "Uses that make an address match the search: an address holding any of them"
                      + " matches, as one matching q or countryCode does. Repeatable. An unknown"
                      + " value is refused with 400.",
              array = @ArraySchema(schema = @Schema(implementation = AddressUse.class)))
          @RequestParam(name = "searchUse", required = false)
          List<AddressUse> searchUses) {
    authoriseOrg(orgId, organisationId);
    log.info(
        "GET addresses - page {}, pageSize {}, q present {}, countryCodes {}, addressType {},"
            + " use {}, searchUses {}",
        page,
        pageSize,
        q != null,
        countryCodes,
        addressType,
        use,
        searchUses);
    return addressService.list(
        organisationId,
        page,
        pageSize,
        new RequestedListFilters(q, countryCodes, addressType, use, searchUses));
  }

  /**
   * Creates an address in the caller's address book. {@code organisationId} is stamped from the
   * identity header, never from the body; server-assigned fields carried in the body are ignored,
   * not rejected.
   *
   * @param orgId the organisation scope from the path, authorised against the identity header
   * @param organisationId the caller's organisation id, from {@code Trade-Imports-Organisation-Id}
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
                description = "Absolute URI of the created address",
                schema = @Schema(type = "string")),
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = AddressResponse.class))),
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
  public ResponseEntity<AddressResponse> create(
      @PathVariable String orgId,
      @RequestHeader(IdentityHeaderFilter.ORGANISATION_ID_HEADER) String organisationId,
      @Valid @RequestBody AddressRequest request) {
    authoriseOrg(orgId, organisationId);
    log.info("POST addresses - creating address");
    Address created = addressService.create(request, organisationId);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{address-id}")
            .buildAndExpand(created.getId())
            .encode()
            .toUri();
    return ResponseEntity.created(location).body(addressMapper.toResponse(created));
  }

  /**
   * Fetches one address by id within the caller's organisation scope, tombstones included. A DELETED
   * address is returned with 200 and {@code deleted: true} so the caller can detect a deletion; an
   * unknown id, or one belonging to another organisation, is a 404 (existence is never leaked). A
   * 404 is therefore NOT a deletion signal: only the tombstone is.
   *
   * @param orgId the organisation scope from the path, authorised against the identity header
   * @param organisationId the caller's organisation id, from {@code Trade-Imports-Organisation-Id}
   * @param addressId the opaque address id from the path
   * @return 200 with the address (deleted false or true)
   */
  @GetMapping("/{address-id}")
  @Operation(operationId = "get-address", summary = "Fetch one address, including tombstones")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "The address (deleted false or true)",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = AddressResponse.class))),
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
  public AddressResponse get(
      @PathVariable String orgId,
      @RequestHeader(IdentityHeaderFilter.ORGANISATION_ID_HEADER) String organisationId,
      @PathVariable("address-id") String addressId) {
    authoriseOrg(orgId, organisationId);
    log.info("GET addresses/{}", addressId);
    Address address =
        addressService
            .get(addressId, organisationId)
            .orElseThrow(() -> new NotFoundException("Address not found"));
    return addressMapper.toResponse(address);
  }

  /**
   * Replaces an address's mutable fields (full representation replace). {@code modifiedAt} is bumped
   * on success (audit only). An unknown id, an id outside the caller's organisation, or a
   * soft-deleted tombstone all yield a 404 — tombstones are outside the caller's live set and
   * existence is never leaked.
   *
   * @param orgId the organisation scope from the path, authorised against the identity header
   * @param organisationId the caller's organisation id, from {@code Trade-Imports-Organisation-Id}
   * @param addressId the opaque address id from the path
   * @param request the validated update body
   * @return 200 with the updated address and its bumped {@code modifiedAt}
   */
  @PutMapping("/{address-id}")
  @Operation(operationId = "update-address", summary = "Replace an address's mutable fields")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Updated address with bumped modifiedAt",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = AddressResponse.class))),
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
        responseCode = "409",
        description = "Concurrent update conflict",
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
  public AddressResponse update(
      @PathVariable String orgId,
      @RequestHeader(IdentityHeaderFilter.ORGANISATION_ID_HEADER) String organisationId,
      @PathVariable("address-id") String addressId,
      @Valid @RequestBody AddressRequest request) {
    authoriseOrg(orgId, organisationId);
    log.info("PUT addresses/{}", addressId);
    Address updated = addressService.update(addressId, request, organisationId);
    return addressMapper.toResponse(updated);
  }

  /**
   * Soft-deletes an address. The document is not removed: {@code status} flips to {@code DELETED}
   * and {@code modifiedAt} is bumped, and the tombstone stays fetchable by id so a deletion is
   * detectable. Idempotent — deleting an already-DELETED address is a 204 with no state change. An
   * unknown id, or one outside the caller's organisation, is a 404 (existence is never leaked).
   *
   * @param orgId the organisation scope from the path, authorised against the identity header
   * @param organisationId the caller's organisation id, from {@code Trade-Imports-Organisation-Id}
   * @param addressId the opaque address id from the path
   * @return 204 No Content (soft-deleted, or already deleted)
   */
  @DeleteMapping("/{address-id}")
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
      @RequestHeader(IdentityHeaderFilter.ORGANISATION_ID_HEADER) String organisationId,
      @PathVariable("address-id") String addressId) {
    authoriseOrg(orgId, organisationId);
    log.info("DELETE addresses/{}", addressId);
    addressService.delete(addressId, organisationId);
    return ResponseEntity.noContent().build();
  }
}
