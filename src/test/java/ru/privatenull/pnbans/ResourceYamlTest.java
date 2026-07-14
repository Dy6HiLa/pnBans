package ru.privatenull.pnbans;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ResourceYamlTest {

    @Test
    void bundledConfigurationFilesAreValidYaml() {
        parse("config.yml");
        parse("messages.yml");
        parse("plugin.yml");
    }

    private void parse(String resource) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource + " must be present in test resources");
            assertNotNull(new Yaml(new SafeConstructor(options)).load(stream), resource + " must contain YAML");
        } catch (java.io.IOException exception) {
            throw new AssertionError("Could not read " + resource, exception);
        }
    }
}
