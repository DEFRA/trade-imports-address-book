package uk.gov.defra.trade.imports.addressbook.exceptions;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * RFC 9457 problem details body — the <em>bad-request</em> / <em>not-found</em> / <em>internal-error</em>
 * shape, and the open branch of the POST/PUT 400 {@code anyOf}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "RFC 9457 problem details")
public record Problem(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String type,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer status,
    String detail,
    String traceId) {}
