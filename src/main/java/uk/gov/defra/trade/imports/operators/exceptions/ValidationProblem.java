package uk.gov.defra.trade.imports.operators.exceptions;

import java.util.List;
import java.util.Map;

/**
 * RFC 9457 problem body for a field-validation 400 — a {@link Problem} plus a per-field
 * {@code errors} map, the <em>validation-error</em> branch of the POST/PUT 400 {@code anyOf}
 * (design §1.6).
 *
 * <p>Declared as a real DTO record so springdoc can reference it in
 * {@code @Schema(anyOf = {ValidationProblem.class, Problem.class})}. A validation body carries
 * {@code errors} and so matches <em>both</em> {@code anyOf} branches — which is exactly why the
 * contract uses {@code anyOf} and not {@code oneOf}: {@code oneOf} would reject the commonest 400
 * this service returns. Do not "tidy" it back to {@code oneOf}.
 *
 * <p>The {@code errors} keys are always the camelCase wire field names ({@code addressLine1}) — the
 * frontend error mapping depends on it.
 *
 * @param type one of the CDP problem-family URIs
 * @param title short human-readable summary
 * @param status HTTP status code
 * @param detail human-readable explanation, may be {@code null}
 * @param traceId x-cdp-request-id correlation value, may be {@code null}
 * @param errors map of camelCase wire field name to its list of messages
 */
public record ValidationProblem(
    String type,
    String title,
    Integer status,
    String detail,
    String traceId,
    Map<String, List<String>> errors) {}
