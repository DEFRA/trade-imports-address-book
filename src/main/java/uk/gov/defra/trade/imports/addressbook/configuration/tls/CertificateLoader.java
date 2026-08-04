package uk.gov.defra.trade.imports.addressbook.configuration.tls;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Loads X509 certificates from TRUSTSTORE_* environment variables.
 *
 * <p>Decodes and parses base64-encoded PEM certificates provided by the CDP platform. When no
 * certificate is configured the loader returns {@code null}; when a certificate is configured but
 * cannot be parsed the loader fails fast so TLS misconfiguration surfaces at startup.
 */
@Component
@Slf4j
public class CertificateLoader {

  private final String certificate;

  public CertificateLoader(@Value("${cdp.certificate}") String certificate) {
    this.certificate = certificate;
  }

  public X509Certificate loadCustomCertificate() {
    if (certificate == null || certificate.isBlank()) {
      log.info("No custom certificates to load");
      return null;
    }

    byte[] certData = Base64.getDecoder().decode(certificate);

    CertificateFactory cf;
    try {
      cf = CertificateFactory.getInstance("X.509");
    } catch (CertificateException e) {
      log.error("Failed to get X.509 CertificateFactory: {}", e.getMessage());
      throw new IllegalStateException("Cannot initialize certificate factory", e);
    }

    try {
      ByteArrayInputStream certStream = new ByteArrayInputStream(certData);
      X509Certificate cert = (X509Certificate) cf.generateCertificate(certStream);

      log.info(
          "Successfully loaded certificate: (Subject: {})",
          cert.getSubjectX500Principal().getName());

      return cert;

    } catch (CertificateException e) {
      log.error("Failed to parse configured certificate: {}", e.getMessage());
      throw new IllegalStateException("Cannot parse configured CDP certificate", e);
    }
  }
}
