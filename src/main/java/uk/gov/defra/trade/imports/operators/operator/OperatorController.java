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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
}
