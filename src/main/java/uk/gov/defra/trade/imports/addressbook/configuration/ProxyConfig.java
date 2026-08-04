package uk.gov.defra.trade.imports.addressbook.configuration;

import jakarta.annotation.PostConstruct;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configures HTTP proxy for all outbound HTTP/HTTPS requests.
 *
 * <p>CDP Platform requirement: all egress traffic (except database connections) must go through the
 * platform's proxy server for security and access control.
 *
 * <p>Reads {@code cdp.proxyUrl} (mapped from the {@code HTTP_PROXY} environment variable), sets Java
 * system properties for traditional HTTP clients, and installs a {@link ProxySelector} that honours
 * {@code nonProxyHosts} for loopback and internal CDP targets.
 */
@Configuration
@Slf4j
public class ProxyConfig {

  static final String NON_PROXY_HOSTS = "localhost|127.*|[::1]|*.cdp-int.defra.cloud";

  private final String httpProxy;

  public ProxyConfig(@Value("${cdp.proxyUrl}") String httpProxy) {
    this.httpProxy = httpProxy;
  }

  @PostConstruct
  public void configureProxy() {
    if (httpProxy == null || httpProxy.isBlank()) {
      log.info("No HTTP_PROXY configured - using direct connections");
      return;
    }

    String proxyUrl = httpProxy.strip();
    try {
      URI proxyUri = URI.create(proxyUrl);
      String proxyHost = proxyUri.getHost();
      int proxyPort = proxyUri.getPort();

      if (proxyHost == null || proxyPort == -1) {
        log.warn(
            "Invalid HTTP_PROXY format: {}. Expected http://host:port",
            redactCredentials(proxyUrl));
        return;
      }

      System.setProperty("http.proxyHost", proxyHost);
      System.setProperty("http.proxyPort", String.valueOf(proxyPort));
      System.setProperty("https.proxyHost", proxyHost);
      System.setProperty("https.proxyPort", String.valueOf(proxyPort));
      System.setProperty("http.nonProxyHosts", NON_PROXY_HOSTS);
      System.setProperty("https.nonProxyHosts", NON_PROXY_HOSTS);

      configureProxyAuthentication(proxyHost, proxyPort, proxyUri.getUserInfo());

      ProxySelector.setDefault(createNonProxyAwareSelector(proxyHost, proxyPort));

      log.info("HTTP proxy configured: {}:{}", proxyHost, proxyPort);

    } catch (IllegalArgumentException e) {
      log.error(
          "Failed to parse HTTP_PROXY: {}. Error: {}",
          redactCredentials(proxyUrl),
          e.getMessage());
    }
  }

  static String redactCredentials(String proxyUrl) {
    return proxyUrl.replaceAll("//[^@/]+@", "//***@");
  }

  static boolean shouldBypassProxy(String host) {
    if (host == null) {
      return false;
    }
    if ("localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host)) {
      return true;
    }
    return host.endsWith(".cdp-int.defra.cloud");
  }

  private static void configureProxyAuthentication(
      String proxyHost, int proxyPort, String userInfo) {
    if (userInfo == null || userInfo.isBlank()) {
      return;
    }

    String[] parts = userInfo.split(":", 2);
    String username = parts[0];
    String password = parts.length > 1 ? parts[1] : "";
    System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
    Authenticator.setDefault(
        new Authenticator() {
          @Override
          protected PasswordAuthentication getPasswordAuthentication() {
            if (getRequestorType() != RequestorType.PROXY) {
              return null;
            }
            if (!proxyHost.equals(getRequestingHost()) || proxyPort != getRequestingPort()) {
              return null;
            }
            return new PasswordAuthentication(username, password.toCharArray());
          }
        });
    log.info("HTTP proxy authentication configured");
  }

  private static ProxySelector createNonProxyAwareSelector(String proxyHost, int proxyPort) {
    Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
    return new ProxySelector() {
      @Override
      public List<Proxy> select(URI uri) {
        if (shouldBypassProxy(uri.getHost())) {
          return List.of(Proxy.NO_PROXY);
        }
        return List.of(proxy);
      }

      @Override
      public void connectFailed(URI uri, SocketAddress sa, java.io.IOException ioe) {
        log.debug("Proxy connection failed for {} via {}: {}", uri, sa, ioe.getMessage());
      }
    };
  }
}
