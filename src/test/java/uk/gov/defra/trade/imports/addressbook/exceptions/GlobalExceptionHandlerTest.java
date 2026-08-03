package uk.gov.defra.trade.imports.addressbook.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
  void handleValidationException_shouldReturn400WithCamelCaseErrorsMapAndTraceId() {
    // Given
    MDC.put("trace.id", "trace-abc");

    // When
    ResponseEntity<ValidationProblem> response =
        exceptionHandler.handleValidationException(
            validationException(
                new FieldError("operatorRequest", "addressLine1", "Enter address line 1"),
                new FieldError("operatorRequest", "email", "Enter an email address")));

    // Then
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
    assertThat(errors).doesNotContainKey("address_line_1");
    assertThat(errors).doesNotContainKey("address_line1");
    assertThat(errors.get("addressLine1")).containsExactly("Enter address line 1");
    assertThat(errors.get("email")).containsExactly("Enter an email address");
  }

  @Test
  void handleValidationException_shouldCollapseMultipleMessagesForOneFieldIntoAList() {
    // When
    ResponseEntity<ValidationProblem> response =
        exceptionHandler.handleValidationException(
            validationException(
                new FieldError("operatorRequest", "name", "must not be blank"),
                new FieldError("operatorRequest", "name", "size must be at most 255")));

    // Then
    assertThat(response.getBody().errors().get("name"))
        .containsExactly("must not be blank", "size must be at most 255");
  }

  @Test
  void handleBadRequestException_shouldReturnDistinct400ShapeWithNoErrorsMap() {
    // Given
    MDC.put("trace.id", "trace-xyz");

    // When
    ResponseEntity<Problem> response =
        exceptionHandler.handleBadRequestException(
            new BadRequestException("Trade-Imports-Crn header is required"));

    // Then
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
    // Given
    MDC.put("trace.id", "trace-shared");

    // When
    ValidationProblem validation =
        exceptionHandler
            .handleValidationException(
                validationException(
                    new FieldError("operatorRequest", "postcode", "Enter a postcode")))
            .getBody();
    Problem badRequest =
        exceptionHandler
            .handleBadRequestException(new BadRequestException("missing header"))
            .getBody();

    // Then
    assertThat(validation.status()).isEqualTo(badRequest.status());
    assertThat(validation.errors()).isNotEmpty();
    assertThat(badRequest.type()).isNotEqualTo(validation.type());
  }

  @Test
  void handleNotFoundException_shouldReturn404ProblemJsonWithTraceId() {
    // Given
    MDC.put("trace.id", "trace-404");

    // When
    ResponseEntity<Problem> response =
        exceptionHandler.handleNotFoundException(new NotFoundException("Operator not found"));

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    Problem body = response.getBody();
    assertThat(body.type())
        .isEqualTo("https://api.cdp.defra.cloud/problems/not-found");
    assertThat(body.title()).isEqualTo("Resource Not Found");
    assertThat(body.detail()).isEqualTo("Operator not found");
    assertThat(body.traceId()).isEqualTo("trace-404");
  }

  @Test
  void handleException_shouldReturn500InternalErrorProblem() {
    // When
    ResponseEntity<Problem> response =
        exceptionHandler.handleException(new RuntimeException("boom"));

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    Problem body = response.getBody();
    assertThat(body.type())
        .isEqualTo("https://api.cdp.defra.cloud/problems/internal-error");
    assertThat(body.title()).isEqualTo("Internal Server Error");
    assertThat(body.detail())
        .isEqualTo("An unexpected error occurred. Please try again later.");
  }

  @Test
  void handleTypeMismatch_shouldReturn400BadRequestProblemNamingTheParameter()
      throws NoSuchMethodException {
    // Given
    Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("validationTarget", Object.class);
    MethodParameter parameter = new MethodParameter(method, 0);
    MethodArgumentTypeMismatchException ex =
        new MethodArgumentTypeMismatchException("abc", Integer.class, "page", parameter, null);

    // When
    ResponseEntity<Problem> response = exceptionHandler.handleTypeMismatch(ex);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    Problem body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.detail()).contains("page");
    assertThat(body.traceId()).isNull();
  }

  @Test
  void handleMessageNotReadable_shouldReturn400BadRequestProblem() {
    // Given
    MDC.put("trace.id", "trace-malformed");

    // When
    ResponseEntity<Problem> response =
        exceptionHandler.handleMessageNotReadable(
            new HttpMessageNotReadableException("JSON parse error", (Throwable) null));

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    Problem body = response.getBody();
    assertThat(body.type())
        .isEqualTo("https://api.cdp.defra.cloud/problems/bad-request");
    assertThat(body.traceId()).isEqualTo("trace-malformed");
  }

  @Test
  void handleNotFoundException_shouldOmitTraceIdWhenAbsentFromMdc() {
    // When
    ResponseEntity<Problem> response =
        exceptionHandler.handleNotFoundException(new NotFoundException("gone"));

    // Then
    assertThat(response.getBody().traceId()).isNull();
  }

  @SuppressWarnings("unused")
  private void validationTarget(Object body) {}

  private MethodArgumentNotValidException validationException(FieldError... fieldErrors) {
    try {
      Method method = this.getClass().getDeclaredMethod("validationTarget", Object.class);
      MethodParameter methodParameter = new MethodParameter(method, 0);
      BindingResult bindingResult = mock(BindingResult.class);
      when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldErrors));
      return new MethodArgumentNotValidException(methodParameter, bindingResult);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("Failed to build MethodArgumentNotValidException", e);
    }
  }
}
