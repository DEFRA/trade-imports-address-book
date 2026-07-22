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
 * An untyped, organisation-owned address-book entry. The field set is flat — the Standard Address
 * Block is one flat form, so there is no nested {@code Address} value object.
 *
 * <p>{@code countryCode} is stored as an ISO 3166-1 alpha-2 code (cv-011), exactly as supplied — the
 * service does not convert it to or from a display name.
 *
 * <p>{@code modifiedAt} is bumped by auditing on every write (PUT and soft delete) but is
 * <strong>audit only</strong>: nothing reads it to refresh anything.
 *
 * <p>The single compound index {@code org_status_created} serves every production read: org-scoped,
 * status-bounded (ACTIVE), newest-first. It is built now while the collection is empty so it is
 * never a later migration or index build on a populated collection.
 *
 * <p>The entity is never serialised onto the wire — the response records own the wire contract.
 */
@Document(collection = "operators")
@CompoundIndex(
    name = "org_status_created",
    def = "{'organisationId': 1, 'status': 1, 'createdAt': -1}")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class Address {

  @EqualsAndHashCode.Include
  @Id
  private String id;

  private String name;

  private String addressLine1;

  private String addressLine2;

  private String townOrCity;

  private String county;

  private String postcode;

  private String countryCode;

  private String phone;

  private String email;

  private String organisationId;

  private AddressStatus status;

  @CreatedDate
  private Instant createdAt;

  @LastModifiedDate
  private Instant modifiedAt;
}
