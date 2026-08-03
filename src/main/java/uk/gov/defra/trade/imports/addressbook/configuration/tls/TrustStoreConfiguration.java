package uk.gov.defra.trade.imports.addressbook.configuration.tls;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures SSL/TLS for the Trade Imports Address Book.
 *
 * <p>Creates a custom {@link SSLContext} that combines the default JVM trust store with optional
 * CDP {@code TRUSTSTORE_*} certificates. Bean ordering is guaranteed by {@link
 * uk.gov.defra.trade.imports.addressbook.configuration.MongoConfig} injecting this configuration
 * when building the Mongo client.
 */
@Configuration
@Slf4j
public class TrustStoreConfiguration {

  private final CertificateLoader certificateLoader;

  public TrustStoreConfiguration(CertificateLoader certificateLoader) {
    this.certificateLoader = certificateLoader;
  }

  /**
   * Creates a custom SSLContext that includes the default JVM trust store and any configured CDP
   * certificates.
   */
  @Bean
  public SSLContext customSslContext() {
    log.info("Initializing custom SSL context with CDP certificates");

    try {
      X509Certificate cert = certificateLoader.loadCustomCertificate();
      X509TrustManager combinedTrustManager = createCombinedTrustManager(cert);

      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new TrustManager[] {combinedTrustManager}, new SecureRandom());

      int customCertCount = cert == null ? 0 : 1;
      log.info(
          "Custom SSL context initialized successfully with {} custom certificate(s)",
          customCertCount);

      return sslContext;

    } catch (GeneralSecurityException | IOException e) {
      log.error("Failed to initialize custom SSL context: {}", e.getMessage(), e);
      throw new IllegalStateException("Cannot initialize SSL context", e);
    }
  }

  private X509TrustManager createCombinedTrustManager(X509Certificate cert)
      throws GeneralSecurityException, IOException {

    TrustManagerFactory defaultTmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    defaultTmf.init((KeyStore) null);

    X509TrustManager defaultTrustManager =
        Arrays.stream(defaultTmf.getTrustManagers())
            .filter(tm -> tm instanceof X509TrustManager)
            .map(tm -> (X509TrustManager) tm)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No default X509TrustManager found"));

    if (cert == null) {
      log.info("No custom certificates found, using default JVM trust store only");
      return defaultTrustManager;
    }

    KeyStore customKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    customKeyStore.load(null, null);
    customKeyStore.setCertificateEntry("TRUSTSTORE_CDP_ROOT_CA", cert);

    log.debug("Added certificate to custom trust store: TRUSTSTORE_CDP_ROOT_CA");

    TrustManagerFactory customTmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    customTmf.init(customKeyStore);

    X509TrustManager customTrustManager =
        Arrays.stream(customTmf.getTrustManagers())
            .filter(tm -> tm instanceof X509TrustManager)
            .map(tm -> (X509TrustManager) tm)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No custom X509TrustManager found"));

    return new CombinedTrustManager(defaultTrustManager, customTrustManager);
  }

  /**
   * Trust manager that delegates to both default and custom trust managers.
   *
   * <p>Tries the default JVM trust manager first (for common CAs), then falls back to the custom
   * CDP trust manager. When both reject a chain the default manager's error is preserved as the
   * primary cause.
   */
  private static class CombinedTrustManager implements X509TrustManager {

    private final X509TrustManager defaultTrustManager;
    private final X509TrustManager customTrustManager;

    CombinedTrustManager(
        X509TrustManager defaultTrustManager, X509TrustManager customTrustManager) {
      this.defaultTrustManager = defaultTrustManager;
      this.customTrustManager = customTrustManager;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
        throws CertificateException {
      CertificateException defaultFailure = null;
      try {
        defaultTrustManager.checkClientTrusted(chain, authType);
        return;
      } catch (CertificateException e) {
        defaultFailure = e;
      }

      try {
        customTrustManager.checkClientTrusted(chain, authType);
      } catch (CertificateException customFailure) {
        if (defaultFailure != null) {
          customFailure.addSuppressed(defaultFailure);
        }
        throw customFailure;
      }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
        throws CertificateException {
      CertificateException defaultFailure = null;
      try {
        defaultTrustManager.checkServerTrusted(chain, authType);
        return;
      } catch (CertificateException e) {
        defaultFailure = e;
      }

      try {
        customTrustManager.checkServerTrusted(chain, authType);
      } catch (CertificateException customFailure) {
        if (defaultFailure != null) {
          customFailure.addSuppressed(defaultFailure);
        }
        throw customFailure;
      }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      X509Certificate[] defaultIssuers = defaultTrustManager.getAcceptedIssuers();
      X509Certificate[] customIssuers = customTrustManager.getAcceptedIssuers();

      X509Certificate[] combined =
          new X509Certificate[defaultIssuers.length + customIssuers.length];
      System.arraycopy(defaultIssuers, 0, combined, 0, defaultIssuers.length);
      System.arraycopy(customIssuers, 0, combined, defaultIssuers.length, customIssuers.length);

      return combined;
    }
  }
}
