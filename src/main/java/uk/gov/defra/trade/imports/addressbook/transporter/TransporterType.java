package uk.gov.defra.trade.imports.addressbook.transporter;

import io.swagger.v3.oas.annotations.media.Schema;

/** Whether a transporter is privately operated or a commercial haulier. */
@Schema(
    description = "Whether a transporter is privately operated or a commercial haulier",
    enumAsRef = true)
public enum TransporterType {
  @Schema(description = "A privately operated transporter")
  PRIVATE,
  @Schema(description = "A commercial haulier, based in Northern Ireland")
  COMMERCIAL
}
