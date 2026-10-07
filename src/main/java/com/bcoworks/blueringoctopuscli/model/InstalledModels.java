package com.bcoworks.blueringoctopuscli.model;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class InstalledModels {

    private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final String LATEST_SUFFIX = ":latest";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final URI tagsUri;

    /**
     * -- GETTER --
     *  En az bir kez denendiyse true.
     */
    @Getter
    private volatile boolean checked;
    private volatile List<String> names; // null: Ollama'ya ulaşılamadı

    public InstalledModels(
            @Value("${langchain4j.ollama.chat-model.base-url:http://localhost:11434}") String baseUrl) {
        this.tagsUri = URI.create(baseUrl.strip().replaceAll("/+$", "") + "/api/tags");
    }

    /**
     * Bloklayıcıdır, UI thread'inden çağrılmamalıdır.
     */
    public void refresh() {
        List<String> result = null;
        try {
            HttpRequest request = HttpRequest.newBuilder(tagsUri).timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                result = new ArrayList<>();
                Matcher matcher = NAME.matcher(response.body());
                while (matcher.find()) {
                    result.add(matcher.group(1));
                }
            }
        } catch (IOException e) {
            log.debug("Could not fetch the Ollama model list: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        names = result == null ? null : List.copyOf(result);
        checked = true;
    }

    /**
     * Liste başarıyla alındıysa true, Ollama'ya ulaşılamadıysa false.
     */
    public boolean isKnown() {
        return names != null;
    }

    public List<String> names() {
        List<String> current = names;
        return current == null ? List.of() : current;
    }

    public boolean isInstalled(String name) {
        return names().stream().anyMatch(installed -> normalize(installed).equals(normalize(name)));
    }

    /**
     * "llama3.1" ile "llama3.1:latest" aynı modeldir.
     */
    public static String normalize(String name) {
        return name.contains(":") ? name : name + LATEST_SUFFIX;
    }

    public static String display(String name) {
        return name.endsWith(LATEST_SUFFIX) ? name.substring(0, name.length() - LATEST_SUFFIX.length()) : name;
    }
}