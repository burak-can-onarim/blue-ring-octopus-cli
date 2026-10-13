package com.bcoworks.blueringoctopuscli;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This project must be developed and used for free. These tests fail when something that could be billed creeps in: a paid
 * GitHub runner, a secret or API key, or a cloud service in the application. If one of them fails, do not "fix" the test:
 * decide with the maintainer whether the cost is acceptable.
 */
class FreeOfChargeTest {

    /** Standard runners are free for a public repository; macOS, larger, GPU and ARM runners are not (or not always). */
    private static final Set<String> FREE_RUNNERS = Set.of("ubuntu-latest", "windows-latest");
    private static final Pattern RUNS_ON = Pattern.compile("(?m)^\\s*runs-on:\\s*(.+?)\\s*$");
    private static final Pattern OS_MATRIX = Pattern.compile("(?m)^\\s*os:\\s*(.+?)\\s*$");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern SECRET = Pattern.compile("secrets\\.([A-Za-z0-9_]+)");

    /** Names of paid services that the application must never talk to. */
    private static final List<String> CLOUD_NAMES = List.of(
            "ollama.com", "api.openai.com", "api.anthropic.com", "generativelanguage.googleapis.com",
            "OLLAMA_API_KEY", "OPENAI_API_KEY", "ANTHROPIC_API_KEY");
    /** The only places that may name Ollama's cloud, because they refuse it. */
    private static final Set<String> MAY_NAME_THE_CLOUD = Set.of("ModelCatalog.java");

    private static List<Path> workflows() throws IOException {
        try (Stream<Path> files = Files.list(Path.of(".github", "workflows"))) {
            return files.filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml")).sorted().toList();
        }
    }

    @Test
    void everyWorkflowRunsOnAFreeStandardRunner() throws IOException {
        List<Path> files = workflows();
        assertTrue(files.size() >= 3, "the workflows were not found, is the working directory the project folder? " + files);
        for (Path file : files) {
            String text = Files.readString(file);
            Matcher runsOn = RUNS_ON.matcher(text);
            while (runsOn.find()) {
                String value = runsOn.group(1);
                assertTrue(FREE_RUNNERS.contains(value) || value.equals("${{ matrix.os }}"),
                        file + " runs on '" + value + "', which may be billed");
            }
            Matcher os = OS_MATRIX.matcher(text);
            while (os.find()) {
                Matcher quoted = QUOTED.matcher(os.group(1));
                while (quoted.find()) {
                    assertTrue(FREE_RUNNERS.contains(quoted.group(1)), file + " has the runner '" + quoted.group(1) + "' in its matrix");
                }
            }
        }
    }

    @Test
    void workflowsUseNoSecretOtherThanTheAutomaticToken() throws IOException {
        List<String> found = new ArrayList<>();
        for (Path file : workflows()) {
            Matcher secret = SECRET.matcher(Files.readString(file));
            while (secret.find()) {
                if (!secret.group(1).equals("GITHUB_TOKEN")) {
                    found.add(file.getFileName() + ": " + secret.group(1));
                }
            }
        }
        assertEquals(List.of(), found, "an API key or another secret in a workflow can lead to a bill");
    }

    @Test
    void theApplicationNeverNamesACloudServiceOrAnApiKey() throws IOException {
        List<String> found = new ArrayList<>();
        for (String root : List.of("src/main", "pom.xml", "Dockerfile")) {
            Path path = Path.of(root);
            try (Stream<Path> files = Files.isDirectory(path) ? Files.walk(path) : Stream.of(path)) {
                for (Path file : files.filter(Files::isRegularFile).filter(FreeOfChargeTest::isText).toList()) {
                    if (MAY_NAME_THE_CLOUD.contains(file.getFileName().toString())) {
                        continue;
                    }
                    String text = Files.readString(file);
                    for (String name : CLOUD_NAMES) {
                        if (text.contains(name)) {
                            found.add(file + " mentions " + name);
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), found);
    }

    private static boolean isText(Path file) {
        String name = file.getFileName().toString();
        return !(name.endsWith(".png") || name.endsWith(".ico") || name.endsWith(".jar") || name.endsWith(".exe"));
    }
}
