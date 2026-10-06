package uk.gov.defra.trade.imports.addressbook.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Automatic expiry of addresses in non-prod environments.
 *
 * <p>Prod is protected by two independent safeguards. Removing an address in prod would require
 * both to fail together:
 *
 * <ul>
 *   <li><b>Stamping guard</b> — {@code expireAt} is set on create only when {@link #days} is
 *       configured (non-prod config sets it; prod leaves it unset) <em>and</em> an explicit code
 *       check confirms {@link #environment} is not {@code prod}.
 *   <li><b>Removal guard</b> — the Mongo TTL index that deletes expired documents is only created
 *       where {@code address-book.ttl.expiry.enabled=true} (non-prod); see {@link
 *       uk.gov.defra.trade.imports.addressbook.address.AddressExpiryIndex}. Prod leaves it {@code false}.
 * </ul>
 *
 * <p>Defaults in {@code application.yml} are the prod-safe values; non-prod environments opt in via
 * {@code ADDRESS_TTL_DAYS} and {@code ADDRESS_TTL_EXPIRY_ENABLED}.
 *
 * @param days how many days after creation an address expires; {@code null} disables stamping (the
 *     prod default). Must be positive when set, so a misconfiguration fails at startup rather than
 *     stamping an already-elapsed {@code expireAt}
 * @param environment the running CDP environment name (from {@code ENVIRONMENT}); production is
 *     {@code prod}, in any case and ignoring surrounding whitespace. Required, so a blank value
 *     fails at startup rather than being read as non-prod
 */
@Validated
@ConfigurationProperties(prefix = "address-book.ttl")
public record AddressTtlConfig(@Positive Integer days, @NotBlank String environment) {

  /** True when the running environment is production, where expiry must never occur. */
  public boolean isProd() {
    return environment != null && "prod".equalsIgnoreCase(environment.strip());
  }
}
