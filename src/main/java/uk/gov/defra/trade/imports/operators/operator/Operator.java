package uk.gov.defra.trade.imports.operators.operator;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * An operator (address-book entry) owned by an organisation. The field set is flat — the Jira
 * ticket field table is one flat form, so there is no {@code Address} value object.
 *
 * <p>{@code country} is a display-name string (c-004), not an ISO alpha-2 code — do not "fix" it.
 *
 * <p>{@code modifiedAt} is bumped by auditing on every write (PUT and soft delete) but is
 * <strong>audit only</strong>: c-017 removed re-sync entirely, so nothing reads it to refresh
 * anything. The two compound indexes serve every production read ({@code crn_status_type_created})
 * and the ruled future org-sharing flip ({@code org_status}); both are built now while the
 * collection is empty so neither is a later migration or index build on a populated collection.
 *
 * <p>The entity is never serialised onto the wire — the response records own the wire contract.
 */
@Document(collection = "operators")
@CompoundIndex(
    name = "crn_status_type_created",
    def = "{'crn': 1, 'status': 1, 'operatorType': 1, 'createdAt': -1}")
@CompoundIndex(name = "org_status", def = "{'organisationId': 1, 'status': 1}")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class Operator {

  @EqualsAndHashCode.Include
  @Id
  private String id;

  private OperatorType operatorType;

  private String name;

  private String addressLine1;

  private String addressLine2;

  private String town;

  private String county;

  private String postcode;

  private String country;

  private String telephone;

  private String email;

  private String approvalNumber;

  private TransporterCategory transporterCategory;

  private String crn;

  private String organisationId;

  private OperatorStatus status;

  @CreatedDate
  private Instant createdAt;

  @LastModifiedDate
  private Instant modifiedAt;
}
