package uk.gov.defra.trade.imports.addressbook.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs;

class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler exceptionHandler;

  @BeforeEach
  void setUp() {
    exceptionHandler = new GlobalExceptionHandler();
    MDC.clear();
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void handleValidationException_shouldReturn400WithAnErrorsMapKeyedByFieldAndTraceId() {
    MDC.put("trace.id", "trace-abc");

    ResponseEntity<ValidationProblem> response =
        exceptionHandler.handleValidationException(
            validationException(
                new FieldError("addressRequest", "addressLine1", "Enter address line 1"),
                new FieldError("addressRequest", "email", "Enter an email address")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

    ValidationProblem body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.type())
        .isEqualTo("https://api.cdp.defra.cloud/problems/validation-error");
    assertThat(body.title()).isEqualTo("Validation Error");
    assertThat(body.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(body.traceId()).isEqualTo("trace-abc");

    Map<String, List<String>> errors = body.errors();
    assertThat(errors).containsKey("addressLine1");
    assertThat(errors.get("addressLine1")).containsExactly("Enter address line 1");
    assertThat(errors.get("email")).containsExactly("Enter an email address");
  }

  @Test
  void handleValidationException_shouldCollapseMultipleMessagesForOneFieldIntoAList() {
    ResponseEntity<ValidationProblem> response =
        exceptionHandler.handleValidationException(
            validationException(
                new FieldError("addressRequest", "name", "must not be blank"),
                new FieldError("addressRequest", "name", "size must be at most 255")));

    assertThat(response.getBody().errors().get("name"))
        .containsExactly("must not be blank", "size must be at most 255");
  }

  @Test
  void handleBadRequestException_shouldReturnDistinct400ShapeWithNoErrorsMap() {
    MDC.put("trace.id", "trace-xyz");

    ResponseEntity<Problem> response =
        exceptionHandler.handleBadRequestException(
            new BadRequestException("Trade-Imports-Crn header is required"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

    Problem body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.type())
        .isEqualTo("https://api.cdp.defra.cloud/problems/bad-request");
    assertThat(body.title()).isEqualTo("Bad Request");
    assertThat(body.detail()).isEqualTo("Trade-Imports-Crn header is required");
    assertThat(body.traceId()).isEqualTo("trace-xyz");
  }

  @Test
  void handleValidationException_andBadRequestException_shouldProduceDistinct400Shapes() {
    MDC.put("trace.id", "trace-shared");

    ValidationProblem validation =
        exceptionHandler
            .handleValidationException(
                validationException(
                    new FieldError("addressRequest", "postcode", "Enter a postcode")))
            .getBody();
    Problem badRequest =
        exceptionHandler
            .handleBadRequestException(new BadRequestException("missing header"))
            .getBody();

    assertThat(validation.status()).isEqualTo(badRequest.status());
    assertThat(validation.errors()).isNotEmpty();
    assertThat(badRequest.type()).isNotEqualTo(validation.type());
  }

  @Test
  void handleNotFoundException_shouldReturn404ProblemJsonWithTraceId() {
    MDC.put("trace.id", "trace-404");

    ResponseEntity<Problem> response =
        exceptionHandler.handleNotFoundException(
            NotFoundException.addressNotFound("addr-1", "org-a"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    Problem body = response.getBody();
    assertThat(body.type())
        .isEqualTo("https://api.cdp.defra.cloud/problems/not-found");
    assertThat(body.title()).isEqualTo("Resource Not Found");
    assertThat(body.detail()).isEqualTo("Address not found");
    assertThat(body.traceId()).isEqualTo("trace-404");
  }

  @Test
  void handleException_shouldReturn500InternalErrorProblem() {
    ResponseEntity<Problem> response =
        exceptionHandler.handleException(new RuntimeException("boom"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    Problem body = response.getBody();
    assertThat(body.type())
        .isEqualTo("https://api.cdp.defra.cloud/problems/internal-error");
    assertThat(body.title()).isEqualTo("Internal Server Error");
    assertThat(body.detail())
        .isEqualTo("An unexpected error occurred. Please try again later.");
  }

  @Test
  void handleTypeMismatch_shouldReturn400BadRequestProblemNamingTheParameter() {
    MethodParameter parameter = validationTargetParameter();
    MethodArgumentTypeMismatchException ex =
        new MethodArgumentTypeMismatchException("abc", Integer.class, "page", parameter, null);

    ResponseEntity<Problem> response = exceptionHandler.handleTypeMismatch(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    Problem body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.detail()).contains("page");
    assertThat(body.traceId()).isNull();
  }

  @Test
  void handleMessageNotReadable_shouldReturn400BadRequestProblem() {
    MDC.put("trace.id", "trace-malformed");

    ResponseEntity<Problem> response =
        exceptionHandler.handleMessageNotReadable(
            new HttpMessageNotReadableException("JSON parse error", (Throwable) null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    Problem body = response.getBody();
    assertThat(body.type())
        .isEqualTo("https://api.cdp.defra.cloud/problems/bad-request");
    assertThat(body.traceId()).isEqualTo("trace-malformed");
  }

  @Test
  void handleOptimisticLocking_shouldReturn409ConflictProblem() {
    ResponseEntity<Problem> response =
        exceptionHandler.handleOptimisticLocking(new OptimisticLockingFailureException("stale"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    Problem body = response.getBody();
    assertThat(body.type())
        .isEqualTo("https://api.cdp.defra.cloud/problems/conflict");
    assertThat(body.title()).isEqualTo("Conflict");
    assertThat(body.detail()).isEqualTo("The resource was modified by another request");
  }

  @Test
  void handleNotFoundException_shouldOmitTraceIdWhenAbsentFromMdc() {
    ResponseEntity<Problem> response =
        exceptionHandler.handleNotFoundException(
            NotFoundException.addressNotFound("addr-1", "org-a"));

    assertThat(response.getBody().traceId()).isNull();
  }

  @Test
  void handleNotFoundException_logsAnUnknownAddressWithTheAddressIdAndOrganisationId() {
    List<ILoggingEvent> events =
        CapturedLogs.loggedBy(
            GlobalExceptionHandler.class,
            () ->
                exceptionHandler.handleNotFoundException(
                    NotFoundException.addressNotFound("addr-1", "org-a")));

    assertThat(events)
        .singleElement()
        .extracting(ILoggingEvent::getFormattedMessage)
        .asString()
        .endsWith("Address addr-1 not found in organisation org-a");
  }

  @Test
  void handleNotFoundException_logsAnOrganisationMismatchWithBothOrganisationIds() {
    List<ILoggingEvent> events =
        CapturedLogs.loggedBy(
            GlobalExceptionHandler.class,
            () ->
                exceptionHandler.handleNotFoundException(
                    NotFoundException.organisationMismatch("org-path", "org-header")));

    assertThat(events)
        .singleElement()
        .extracting(ILoggingEvent::getFormattedMessage)
        .asString()
        .endsWith(
            "Organisation mismatch: path orgId org-path is not the caller's organisation"
                + " org-header");
  }

  @Test
  void handleNotFoundException_answersAnOrganisationMismatchWithTheUnknownAddressBody() {
    MDC.put("trace.id", "trace-404");

    Problem mismatch =
        exceptionHandler
            .handleNotFoundException(
                NotFoundException.organisationMismatch("org-path", "org-header"))
            .getBody();
    Problem unknownAddress =
        exceptionHandler
            .handleNotFoundException(NotFoundException.addressNotFound("addr-1", "org-a"))
            .getBody();

    assertThat(mismatch).isEqualTo(unknownAddress);
    assertThat(mismatch.detail()).isEqualTo("Address not found");
  }

  @Test
  void handleValidationException_shouldLogTheRejectedFieldNamesButNotTheirValues() {
    BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "addressRequest");
    bindingResult.addError(
        new FieldError(
            "addressRequest",
            "email",
            "jo.bloggs@example.com.invalid@",
            false,
            null,
            null,
            "Enter an email address in the correct format"));

    List<ILoggingEvent> events =
        CapturedLogs.loggedBy(
            GlobalExceptionHandler.class,
            () ->
                exceptionHandler.handleValidationException(
                    new MethodArgumentNotValidException(validationTargetParameter(), bindingResult)));

    assertThat(events)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getFormattedMessage()).contains("[email]");
              assertThat(event.getFormattedMessage()).doesNotContain("jo.bloggs");
              assertThat(event.getThrowableProxy()).isNull();
            });
  }

  @SuppressWarnings("unused")
  private void validationTarget(Object body) {}

  private MethodParameter validationTargetParameter() {
    try {
      Method method = this.getClass().getDeclaredMethod("validationTarget", Object.class);
      return new MethodParameter(method, 0);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("Failed to find the validation target method", e);
    }
  }

  private MethodArgumentNotValidException validationException(FieldError... fieldErrors) {
    BindingResult bindingResult = mock(BindingResult.class);
    when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldErrors));
    return new MethodArgumentNotValidException(validationTargetParameter(), bindingResult);
  }
}
