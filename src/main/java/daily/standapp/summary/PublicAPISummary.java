package daily.standapp.summary;

import daily.standapp.git.GitCommitEntry;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PublicAPISummary {
    private static final Pattern OUTPUT_TEXT_PATTERN = Pattern.compile(
            "\"type\"\\s*:\\s*\"output_text\".*?\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
            Pattern.DOTALL
    );
    private static final String PLACEHOLDER_API_KEY = "YOUR_OPENAI_API_KEY_HERE";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final URI endpoint;

    public PublicAPISummary() {
        this(HttpClient.newHttpClient(), loadProperties());
    }

    PublicAPISummary(HttpClient httpClient, Properties properties) {
        this.httpClient = httpClient;
        this.apiKey = requiredProperty(properties, "openai.api.key");
        this.model = requiredProperty(properties, "openai.model");
        this.endpoint = URI.create(requiredProperty(properties, "openai.base.url"));
    }

    public String summarize(List<GitCommitEntry> commits) {
        if (apiKey.isBlank() || PLACEHOLDER_API_KEY.equals(apiKey)) {
            throw new IllegalStateException("""
                    OpenAI API key is missing.
                    Please set openai.api.key in src/main/resources/application.properties.
                    """);
        }
        if (commits.isEmpty()) {
            return "Keine Commits gefunden.";
        }

        String prompt = buildPrompt(commits);
        String requestBody = """
                {
                  "model": "%s",
                  "input": "%s"
                }
                """.formatted(escapeJson(model), escapeJson(prompt));

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI API request failed with status " + response.statusCode() + ": " + response.body());
            }
            return extractOutputText(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to call OpenAI API.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI API request was interrupted.", exception);
        }
    }

    private String buildPrompt(List<GitCommitEntry> commits) {
        StringBuilder builder = new StringBuilder("""
                Ich bin ein erfahrener Java Senior Developer. Für mein Daily Stand-Up morgen früh muss ich zusammenfassen, was ich bisher gemacht habe. Hier sind eine Liste von Git-Commits. Fasse es mir kurz zusammen, so dass ich es meinen Kollegen erzählen kann.

                Git-Commits:
                """);

        for (GitCommitEntry commit : commits) {
            builder.append("- Committer: ")
                    .append(commit.committer())
                    .append(", E-Mail: ")
                    .append(commit.emailAddress())
                    .append(", Message: ")
                    .append(commit.commitMessage())
                    .append(System.lineSeparator());
        }
        return builder.toString().trim();
    }

    private static Properties loadProperties() {
        try (InputStream inputStream = PublicAPISummary.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing application.properties on the classpath.");
            }
            Properties properties = new Properties();
            properties.load(inputStream);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load application.properties.", exception);
        }
    }

    private static String requiredProperty(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Missing property: " + key);
        }
        return value.trim();
    }

    private static String extractOutputText(String responseBody) {
        Matcher matcher = OUTPUT_TEXT_PATTERN.matcher(responseBody);
        if (!matcher.find()) {
            throw new IllegalStateException("Could not extract summary text from OpenAI response: " + responseBody);
        }
        return unescapeJson(matcher.group(1));
    }

    private static String escapeJson(String input) {
        StringBuilder builder = new StringBuilder();
        for (char current : input.toCharArray()) {
            switch (current) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (current < 0x20) {
                        builder.append("\\u%04x".formatted((int) current));
                    } else {
                        builder.append(current);
                    }
                }
            }
        }
        return builder.toString();
    }

    private static String unescapeJson(String input) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if (current != '\\') {
                builder.append(current);
                continue;
            }

            if (index + 1 >= input.length()) {
                builder.append(current);
                continue;
            }

            char escaped = input.charAt(++index);
            switch (escaped) {
                case '"', '\\', '/' -> builder.append(escaped);
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'u' -> {
                    if (index + 4 >= input.length()) {
                        throw new IllegalStateException("Invalid unicode escape in OpenAI response.");
                    }
                    String hex = input.substring(index + 1, index + 5);
                    builder.append((char) Integer.parseInt(hex, 16));
                    index += 4;
                }
                default -> builder.append(escaped);
            }
        }
        return builder.toString();
    }
}
