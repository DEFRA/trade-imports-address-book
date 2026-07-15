package uk.gov.defra.trade.imports.operators.exceptions;

/**
 * RFC 9457 problem details body — the <em>bad-request</em> / <em>not-found</em> / <em>internal-error</em>
 * shape, and the open branch of the POST/PUT 400 {@code anyOf}.
 *
 * <p>This is a real DTO record, not merely a YAML fragment, so springdoc has a class to reference in
 * {@code @Schema(anyOf = {ValidationProblem.class, Problem.class})} (design §1.6). It carries no
 * {@code errors} map — that is what distinguishes a bad-request from a {@link ValidationProblem}.
 *
 * <p>{@code trace_id} is snake_case by literal field name — a deliberate divergence from the
 * animals-backend's camelCase {@code traceId}; this is a new API surface and the locked contract
 * fixes the wire name.
 *
 * @param type one of the CDP problem-family URIs
 * @param title short human-readable summary
 * @param status HTTP status code
 * @param detail human-readable explanation, may be {@code null}
 * @param traceId x-cdp-request-id correlation value, may be {@code null}
 */
public record Problem(String type, String title, Integer status, String detail, String traceId) {}
