package uk.gov.defra.trade.imports.operators.operator;

/**
 * Static entity &harr; DTO mapping. No framework, no reflection — the wire contract is owned here,
 * decoupled from the {@code @Document} entity which is never serialised.
 */
public final class OperatorMapper {

  private OperatorMapper() {}

  /** Maps a persisted {@link Operator} onto its wire response. */
  public static OperatorResponse toResponse(Operator operator) {
    return OperatorResponse.builder()
        .id(operator.getId())
        .operatorType(operator.getOperatorType())
        .name(operator.getName())
        .addressLine1(operator.getAddressLine1())
        .addressLine2(operator.getAddressLine2())
        .town(operator.getTown())
        .county(operator.getCounty())
        .postcode(operator.getPostcode())
        .country(operator.getCountry())
        .telephone(operator.getTelephone())
        .email(operator.getEmail())
        .approvalNumber(operator.getApprovalNumber())
        .transporterCategory(operator.getTransporterCategory())
        .crn(operator.getCrn())
        .organisationId(operator.getOrganisationId())
        .status(operator.getStatus())
        .createdAt(operator.getCreatedAt())
        .modifiedAt(operator.getModifiedAt())
        .build();
  }

  /**
   * Maps a client request onto a new entity carrying only the client-supplied fields. Server-owned
   * fields ({@code id}, {@code crn}, {@code organisationId}, {@code status}, timestamps) are set by
   * the service on create/update, not here.
   */
  public static Operator toEntity(OperatorRequest request) {
    return Operator.builder()
        .operatorType(request.operatorType())
        .name(request.name())
        .addressLine1(request.addressLine1())
        .addressLine2(request.addressLine2())
        .town(request.town())
        .county(request.county())
        .postcode(request.postcode())
        .country(request.country())
        .telephone(request.telephone())
        .email(request.email())
        .approvalNumber(request.approvalNumber())
        .transporterCategory(request.transporterCategory())
        .build();
  }
}
