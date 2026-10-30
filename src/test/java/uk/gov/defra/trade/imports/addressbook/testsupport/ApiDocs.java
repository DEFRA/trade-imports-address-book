package uk.gov.defra.trade.imports.addressbook.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.client.RestClient;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public final class ApiDocs {

  public static final Path COMMITTED_ARTIFACT = Path.of("docs/openapi/operators.yml");

  private ApiDocs() {}

  @SuppressWarnings("unchecked")
  public static Map<String, Object> fetchLive(int port) {
    String json =
        RestClient.create()
            .get()
            .uri("http://localhost:" + port + "/v3/api-docs")
            .retrieve()
            .body(String.class);
    try {
      return new ObjectMapper().readValue(json, Map.class);
    } catch (IOException e) {
      throw new AssertionError("could not parse /v3/api-docs", e);
    }
  }

  public static String renderCommittedForm(Map<String, Object> live) {
    Map<String, Object> withoutServers = new LinkedHashMap<>(live);
    withoutServers.remove("servers");
    return yaml().dump(withoutServers);
  }

  public static Map<String, Object> loadYaml(Path file) throws IOException {
    return yaml().load(Files.readString(file));
  }

  private static Yaml yaml() {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setSplitLines(false);
    return new Yaml(options);
  }
}
