package uk.gov.defra.trade.imports.addressbook.address;

import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** An organisation-owned address-book entry. {@code roles} can be null or empty. */
@Document(collection = "addresses")
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

  private List<AddressRole> roles;

  private String organisationId;

  private AddressStatus status;

  @CreatedDate
  private Instant createdAt;

  @LastModifiedDate
  private Instant modifiedAt;

  private Instant expireAt;

  @Version
  private Long version;
}
