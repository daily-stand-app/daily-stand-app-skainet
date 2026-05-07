package daily.standapp.summary;

import daily.standapp.git.GitCommitEntry;

import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient.Version;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocalAPISummary {
    private static final Pattern MODEL_ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final String FINAL_PREFIX = "FINAL:";
    private static final Pattern OUTPUT_TOKENS_PATTERN = Pattern.compile("\"output_tokens\"\\s*:\\s*(\\d+)");
    private static final Pattern INPUT_TOKENS_PATTERN = Pattern.compile("\"input_tokens\"\\s*:\\s*(\\d+)");
    private static final Pattern TOTAL_TOKENS_PATTERN = Pattern.compile("\"total_tokens\"\\s*:\\s*(\\d+)");
    private static final Pattern REASONING_TOKENS_PATTERN = Pattern.compile("\"reasoning_tokens\"\\s*:\\s*(\\d+)");
    private static final Pattern CREATED_AT_PATTERN = Pattern.compile("\"created_at\"\\s*:\\s*(\\d+)");
    private static final Pattern COMPLETED_AT_PATTERN = Pattern.compile("\"completed_at\"\\s*:\\s*(\\d+)");
    private static final Pattern STATUS_PATTERN = Pattern.compile("\"status\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern MODEL_PATTERN = Pattern.compile("\"model\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private final HttpClient httpClient;
    private final URI modelsEndpoint;
    private final URI responsesEndpoint;

    public LocalAPISummary() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .proxy(new NoProxySelector())
                .version(Version.HTTP_1_1)
                .build(), loadProperties());
    }

    LocalAPISummary(HttpClient httpClient, Properties properties) {
        this.httpClient = httpClient;
        String baseUrl = normalizeBaseUrl(requiredProperty(properties, "lmstudio.base.url"));
        this.modelsEndpoint = URI.create(baseUrl + "/models");
        this.responsesEndpoint = URI.create(baseUrl + "/responses");
    }

    public List<String> listSupportedModels() {
        return loadModelIds();
    }

    public ModelSummary summarizeModel(List<GitCommitEntry> commits, String modelId, int maxOutputTokens) {
        try {
            return summarizeWithModel(commits, modelId, maxOutputTokens);
        } catch (IllegalStateException exception) {
            return new ModelSummary(modelId, null, null, exception.getMessage());
        }
    }

    private List<String> loadModelIds() {
        HttpRequest request = HttpRequest.newBuilder(modelsEndpoint)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("LM Studio model listing failed with status " + response.statusCode() + ": " + response.body());
            }

            List<String> modelIds = new ArrayList<>();
            Matcher matcher = MODEL_ID_PATTERN.matcher(response.body());
            while (matcher.find()) {
                modelIds.add(unescapeJson(matcher.group(1)));
            }
            if (modelIds.isEmpty()) {
                throw new IllegalStateException("No models found in LM Studio response: " + response.body());
            }
            return modelIds;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to call LM Studio model endpoint " + modelsEndpoint + ".", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LM Studio model request was interrupted for " + modelsEndpoint + ".", exception);
        }
    }

    private ModelSummary summarizeWithModel(List<GitCommitEntry> commits, String modelId, int maxOutputTokens) {
        if (commits.isEmpty()) {
            return new ModelSummary(modelId, "Keine Commits gefunden.", null, null);
        }

        String prompt = buildPrompt(commits);
        return executeSummaryRequest(modelId, prompt, maxOutputTokens);
    }

    private ModelSummary executeSummaryRequest(String modelId, String prompt, int maxOutputTokens) {
        HttpRequest request = buildResponsesRequest(modelId, prompt, maxOutputTokens);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("LM Studio responses request failed with status " + response.statusCode() + ": " + response.body());
            }
            String responseBody = response.body();
            String summary = extractOutputText(responseBody, false);
            GenerationStats generationStats = extractGenerationStats(responseBody);
            return new ModelSummary(modelId, summary, generationStats, null);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to call LM Studio responses endpoint " + responsesEndpoint + ".", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LM Studio responses request was interrupted for " + responsesEndpoint + ".", exception);
        } catch (ReasoningOnlyException exception) {
            int retryTokens = Math.max(maxOutputTokens, 1200);
            String retryPrompt = prompt + """

                    
                    Wichtig: Beginne deine Antwort mit FINAL: und gib sofort nur die kurze Zusammenfassung aus.
                    """;
            HttpRequest retryRequest = buildResponsesRequest(modelId, retryPrompt, retryTokens);
            try {
                HttpResponse<String> retryResponse = httpClient.send(retryRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (retryResponse.statusCode() < 200 || retryResponse.statusCode() >= 300) {
                    throw new IllegalStateException("LM Studio responses retry failed with status " + retryResponse.statusCode() + ": " + retryResponse.body());
                }
                String retryBody = retryResponse.body();
                String summary = extractOutputText(retryBody, true);
                GenerationStats generationStats = extractGenerationStats(retryBody);
                return new ModelSummary(modelId, summary, generationStats, null);
            } catch (IOException retryException) {
                throw new IllegalStateException("Failed to call LM Studio responses retry endpoint " + responsesEndpoint + ".", retryException);
            } catch (InterruptedException retryException) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("LM Studio responses retry was interrupted for " + responsesEndpoint + ".", retryException);
            }
        }
    }

    private HttpRequest buildResponsesRequest(String modelId, String prompt, int maxOutputTokens) {
        String requestBody = """
                {
                  "model": "%s",
                  "input": "%s",
                  "temperature": 0.2,
                  "max_output_tokens": %d
                }
                """.formatted(
                escapeJson(modelId),
                escapeJson(prompt),
                maxOutputTokens
        );

        return HttpRequest.newBuilder(responsesEndpoint)
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
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
        try (InputStream inputStream = LocalAPISummary.class.getClassLoader().getResourceAsStream("application.properties")) {
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

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.startsWith("http://localhost:")) {
            return "http://127.0.0.1:" + baseUrl.substring("http://localhost:".length());
        }
        if (baseUrl.equals("http://localhost")) {
            return "http://127.0.0.1";
        }
        return baseUrl;
    }

    private static String extractOutputText(String responseBody, boolean allowReasoningFallback) {
        String outputText = findTextByType(responseBody, "output_text");
        if (outputText != null) {
            return cleanupExtractedAnswer(outputText);
        }

        String reasoningText = findTextByType(responseBody, "reasoning_text");
        if (allowReasoningFallback) {
            String extractedAnswer = extractDraftAnswer(reasoningText);
            if (extractedAnswer != null) {
                return extractedAnswer;
            }
        }

        if (reasoningText != null) {
            throw new ReasoningOnlyException("Model returned only reasoning output, but no final answer text.");
        }

        throw new IllegalStateException("Could not extract output text from LM Studio response: " + responseBody);
    }

    private static String extractDraftAnswer(String reasoningText) {
        if (reasoningText == null || reasoningText.isBlank()) {
            return null;
        }

        String latestFinal = null;
        String latestDraft = null;
        for (String line : reasoningText.split("\\R")) {
            String trimmedLine = line.trim();
            String normalized = trimmedLine.toLowerCase();

            int finalIndex = normalized.indexOf("final answer");
            if (finalIndex < 0) {
                finalIndex = normalized.indexOf("final summary");
            }
            if (finalIndex >= 0) {
                String candidate = extractLineValue(trimmedLine);
                if (candidate != null && !candidate.isBlank()) {
                    latestFinal = candidate;
                }
            }

            if (normalized.contains("draft")) {
                String candidate = extractLineValue(trimmedLine);
                if (candidate != null && !candidate.isBlank()) {
                    latestDraft = candidate;
                }
            }
        }

        if (latestFinal != null) {
            return cleanupExtractedAnswer(latestFinal);
        }
        if (latestDraft != null) {
            return cleanupExtractedAnswer(latestDraft);
        }
        return null;
    }

    private static String cleanupExtractedAnswer(String answer) {
        return answer
                .replace(FINAL_PREFIX, "")
                .replace("*", "")
                .trim();
    }

    private static String extractLineValue(String line) {
        int colonIndex = line.indexOf(':');
        if (colonIndex < 0 || colonIndex + 1 >= line.length()) {
            return null;
        }
        return line.substring(colonIndex + 1).trim();
    }

    private static String findTextByType(String responseBody, String type) {
        String marker = "\"type\"";
        int searchFrom = 0;
        while (true) {
            int typeKeyIndex = responseBody.indexOf(marker, searchFrom);
            if (typeKeyIndex < 0) {
                return null;
            }

            int typeValueIndex = responseBody.indexOf('\"', typeKeyIndex + marker.length());
            if (typeValueIndex < 0) {
                return null;
            }

            JsonStringResult typeResult = extractJsonString(responseBody, typeValueIndex);
            if (type.equals(typeResult.value())) {
                int textKeyIndex = responseBody.indexOf("\"text\"", typeResult.nextIndex());
                if (textKeyIndex < 0) {
                    return null;
                }

                int textStartQuote = responseBody.indexOf('\"', textKeyIndex + "\"text\"".length());
                if (textStartQuote < 0) {
                    return null;
                }

                return extractJsonString(responseBody, textStartQuote).value();
            }

            searchFrom = typeResult.nextIndex();
        }
    }

    private static JsonStringResult extractJsonString(String input, int quoteIndex) {
        if (quoteIndex < 0 || quoteIndex >= input.length() || input.charAt(quoteIndex) != '\"') {
            throw new IllegalArgumentException("Expected JSON string at index " + quoteIndex);
        }

        StringBuilder raw = new StringBuilder();
        boolean escaping = false;
        for (int index = quoteIndex + 1; index < input.length(); index++) {
            char current = input.charAt(index);
            if (escaping) {
                raw.append('\\').append(current);
                escaping = false;
                continue;
            }
            if (current == '\\') {
                escaping = true;
                continue;
            }
            if (current == '\"') {
                return new JsonStringResult(unescapeJson(raw.toString()), index + 1);
            }
            raw.append(current);
        }

        throw new IllegalStateException("Unterminated JSON string in LM Studio response.");
    }

    private static GenerationStats extractGenerationStats(String responseBody) {
        int inputTokens = extractInt(responseBody, INPUT_TOKENS_PATTERN, 0);
        int outputTokens = extractInt(responseBody, OUTPUT_TOKENS_PATTERN, 0);
        int totalTokens = extractInt(responseBody, TOTAL_TOKENS_PATTERN, 0);
        int reasoningTokens = extractInt(responseBody, REASONING_TOKENS_PATTERN, 0);
        long createdAt = extractLong(responseBody, CREATED_AT_PATTERN, 0L);
        long completedAt = extractLong(responseBody, COMPLETED_AT_PATTERN, createdAt);
        String status = extractString(responseBody, STATUS_PATTERN, "unknown");
        String model = extractString(responseBody, MODEL_PATTERN, "unknown");

        double durationSeconds = Math.max(0.0, completedAt - createdAt);
        double outputTokensPerSecond = durationSeconds > 0.0 ? outputTokens / durationSeconds : 0.0;

        return new GenerationStats(
                model,
                status,
                inputTokens,
                outputTokens,
                totalTokens,
                reasoningTokens,
                createdAt > 0 ? Instant.ofEpochSecond(createdAt) : null,
                completedAt > 0 ? Instant.ofEpochSecond(completedAt) : null,
                durationSeconds,
                outputTokensPerSecond
        );
    }

    private static int extractInt(String responseBody, Pattern pattern, int defaultValue) {
        Matcher matcher = pattern.matcher(responseBody);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : defaultValue;
    }

    private static long extractLong(String responseBody, Pattern pattern, long defaultValue) {
        Matcher matcher = pattern.matcher(responseBody);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : defaultValue;
    }

    private static String extractString(String responseBody, Pattern pattern, String defaultValue) {
        Matcher matcher = pattern.matcher(responseBody);
        return matcher.find() ? unescapeJson(matcher.group(1)) : defaultValue;
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
                        throw new IllegalStateException("Invalid unicode escape in LM Studio response.");
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

    public record ModelSummary(
            String modelId,
            String summary,
            GenerationStats generationStats,
            String error
    ) {
    }

    public record GenerationStats(
            String model,
            String status,
            int inputTokens,
            int outputTokens,
            int totalTokens,
            int reasoningTokens,
            Instant createdAt,
            Instant completedAt,
            double durationSeconds,
            double outputTokensPerSecond
    ) {
    }

    private static final class NoProxySelector extends ProxySelector {

        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, java.net.SocketAddress sa, IOException ioe) {
        }
    }

    private static final class ReasoningOnlyException extends IllegalStateException {
        private ReasoningOnlyException(String message) {
            super(message);
        }
    }

    private record JsonStringResult(String value, int nextIndex) {
    }
}
