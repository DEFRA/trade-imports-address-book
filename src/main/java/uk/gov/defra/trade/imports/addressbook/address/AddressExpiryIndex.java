package uk.gov.defra.trade.imports.addressbook.address;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;
import uk.gov.defra.trade.imports.addressbook.configuration.AddressTtlConfig;

/**
 * Creates the Mongo TTL index that removes addresses once their {@code expireAt} has passed — the
 * removal half of {@link AddressTtlConfig}'s prod safeguards.
 *
 * <p>Created here rather than with {@code @Indexed(expireAfter)} on the field because
 * {@code auto-index-creation} would then build it in every environment, prod included. The bean
 * exists only when {@code address-book.ttl.expiry.enabled=true} is set explicitly (no
 * {@code matchIfMissing}). Mongo's TTL monitor skips documents with no date in {@code expireAt}, so
 * addresses created before expiry was switched on are never removed by it.
 *
 * <p>Switching the flag back off does not drop an index that already exists.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "address-book.ttl.expiry", name = "enabled", havingValue = "true")
public class AddressExpiryIndex {

  static final String INDEX_NAME = "address_expire_at_ttl";

  private final MongoTemplate mongoTemplate;
  private final AddressTtlConfig ttlConfig;

  @EventListener(ApplicationReadyEvent.class)
  public void createIndex() {
    if (ttlConfig.isProd()) {
      log.warn("Address expiry is enabled in prod; refusing to create the TTL index");
      return;
    }
    mongoTemplate
        .indexOps(Address.class)
        .createIndex(
            new Index().on("expireAt", Sort.Direction.ASC).expire(Duration.ZERO).named(INDEX_NAME));
    log.info("Address expiry TTL index {} is in place", INDEX_NAME);
  }
}
