package uk.gov.defra.trade.imports.addressbook.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

class ProxyConfigTest {

  private ProxySelector originalSelector;

  @BeforeEach
  void saveOriginalProxySelector() {
    originalSelector = ProxySelector.getDefault();
  }

  @AfterEach
  void cleanup() {
    System.clearProperty("http.proxyHost");
    System.clearProperty("http.proxyPort");
    System.clearProperty("https.proxyHost");
    System.clearProperty("https.proxyPort");
    System.clearProperty("http.nonProxyHosts");
    System.clearProperty("https.nonProxyHosts");
    ProxySelector.setDefault(originalSelector);
  }

  @Test
  void configureProxy_blankProxyUrl_leavesSystemPropertiesUnset() {
    ProxyConfig config = new ProxyConfig("   ");
    config.configureProxy();

    assertThat(System.getProperty("http.proxyHost")).isNull();
  }

  @Test
  void configureProxy_validUrl_setsSystemPropertiesAndNonProxyHosts() {
    ProxyConfig config = new ProxyConfig("http://localhost:3128");
    config.configureProxy();

    assertThat(System.getProperty("http.proxyHost")).isEqualTo("localhost");
    assertThat(System.getProperty("http.proxyPort")).isEqualTo("3128");
    assertThat(System.getProperty("https.proxyHost")).isEqualTo("localhost");
    assertThat(System.getProperty("https.proxyPort")).isEqualTo("3128");
    assertThat(System.getProperty("http.nonProxyHosts"))
        .isEqualTo(ProxyConfig.NON_PROXY_HOSTS);
    assertThat(System.getProperty("https.nonProxyHosts"))
        .isEqualTo(ProxyConfig.NON_PROXY_HOSTS);
  }

  @Test
  void configureProxy_invalidUrl_doesNotSetSystemProperties() {
    ProxyConfig config = new ProxyConfig("not-a-valid-uri");
    config.configureProxy();

    assertThat(System.getProperty("http.proxyHost")).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://proxy-user:s3cret-password@proxy example:3128",
        "proxy-user:s3cret-password@proxy.example:3128",
        "http://proxy-user:s3cret@password@proxy.example:3128"
      })
  void configureProxy_unusableUrlWithCredentials_logsRedactedUrlWithoutCredentialsOrThrowable(
      String proxyUrl) {
    ProxyConfig config = new ProxyConfig(proxyUrl);

    List<ILoggingEvent> events = logEventsWhileConfiguring(config);

    assertThat(System.getProperty("http.proxyHost")).isNull();
    assertThat(events)
        .isNotEmpty()
        .allSatisfy(
            event -> {
              assertThat(event.getFormattedMessage())
                  .contains("***@")
                  .doesNotContain("proxy-user")
                  .doesNotContain("password");
              assertThat(event.getThrowableProxy()).isNull();
            });
  }

  @Test
  void redactCredentials_replacesUserInfoAndKeepsSchemeHostAndPort() {
    assertThat(ProxyConfig.redactCredentials("http://user:secret@proxy.example:3128"))
        .isEqualTo("http://***@proxy.example:3128");
  }

  @Test
  void redactCredentials_leavesUrlWithoutCredentialsUnchanged() {
    assertThat(ProxyConfig.redactCredentials("http://proxy.example:3128"))
        .isEqualTo("http://proxy.example:3128");
  }

  @Test
  void shouldBypassProxy_matchesLoopbackAndInternalCdpHosts() {
    assertThat(ProxyConfig.shouldBypassProxy("localhost")).isTrue();
    assertThat(ProxyConfig.shouldBypassProxy("127.0.0.1")).isTrue();
    assertThat(ProxyConfig.shouldBypassProxy("[::1]")).isTrue();
    assertThat(ProxyConfig.shouldBypassProxy("mongo.cdp-int.defra.cloud")).isTrue();
    assertThat(ProxyConfig.shouldBypassProxy("external.example.com")).isFalse();
  }

  @Test
  void proxySelector_bypassesLocalhost() throws Exception {
    ProxyConfig config = new ProxyConfig("http://proxy.example:3128");
    config.configureProxy();

    ProxySelector selector = ProxySelector.getDefault();
    assertThat(selector.select(URI.create("http://localhost:4566")))
        .containsExactly(Proxy.NO_PROXY);
    assertThat(selector.select(URI.create("http://external.example/api")))
        .extracting(Proxy::type)
        .containsExactly(Proxy.Type.HTTP);
  }

  private static List<ILoggingEvent> logEventsWhileConfiguring(ProxyConfig config) {
    Logger logger = (Logger) LoggerFactory.getLogger(ProxyConfig.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      config.configureProxy();
    } finally {
      logger.detachAppender(appender);
    }
    return appender.list;
  }
}
