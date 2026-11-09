package uk.gov.defra.trade.imports.addressbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.gov.defra.trade.imports.addressbook.exceptions.NotFoundException;
import uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs;

class OrganisationScopeTest {

  private static final String NOT_FOUND_MESSAGE = "Address not found";

  @Test
  void authorise_allowsAMatchingOrganisationSilently() {
    List<ILoggingEvent> events =
        CapturedLogs.loggedBy(
            OrganisationScope.class,
            () -> OrganisationScope.authorise("address", "abc", "org-1", "org-1", NOT_FOUND_MESSAGE));

    assertThat(events).isEmpty();
  }

  @Test
  void authorise_refusesAMismatchedOrganisationLoggingOneWarnWithBothOrganisationIds() {
    List<ILoggingEvent> events =
        CapturedLogs.loggedBy(
            OrganisationScope.class,
            () ->
                assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(
                        () ->
                            OrganisationScope.authorise(
                                "address", "abc", "org-1", "org-2", NOT_FOUND_MESSAGE))
                    .satisfies(
                        refusal -> assertThat(refusal.detail()).isEqualTo(NOT_FOUND_MESSAGE)));

    assertThat(events)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getFormattedMessage())
                  .contains("address")
                  .contains("abc")
                  .contains("org-1")
                  .contains("org-2");
            });
  }
}
