package uk.gov.defra.trade.imports.addressbook.exceptions;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * RFC 9457 validation problem body with a per-field camelCase {@code errors} map.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "RFC 9457 validation problem with field errors")
public record ValidationProblem(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String type,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer status,
    String detail,
    String traceId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Map<String, List<String>> errors) {}
