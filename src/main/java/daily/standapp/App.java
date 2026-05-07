package daily.standapp;

import daily.standapp.summary.McpToolCallingSummary;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class App {

    private App() {
    }

    public static void main(String[] args) {
        Properties properties = loadProperties();
        String modelPath = requiredProperty(properties, "embedded.model.path");
        String mcpServerUrl = requiredProperty(properties, "mcp.server.url");

        // Path repositoryPath = Path.of("..", "example.git");
        //
        // System.out.println("=== Tool Calling ===");
        // ToolCallingSummary toolCallingSummary = new ToolCallingSummary();
        // ToolCallingSummary.ToolCallingResult toolCallingResult = toolCallingSummary.summarize(
        //         modelPath,
        //         repositoryPath
        // );
        // System.out.println("Zusammenfassung:");
        // System.out.println(toolCallingResult.summary());
        // printToolCallingStats(toolCallingResult.toolCalls(),
        //         toolCallingResult.outputTokens(),
        //         toolCallingResult.wallSeconds(),
        //         toolCallingResult.wallTokensPerSecond(),
        //         toolCallingResult.decodeSeconds(),
        //         toolCallingResult.decodeTokensPerSecond());
        // System.out.println();
        //
        System.out.println("=== MCP Tool Calling ===");
        McpToolCallingSummary mcpToolCallingSummary = new McpToolCallingSummary();
        McpToolCallingSummary.ToolCallingResult toolCallingResult = mcpToolCallingSummary.summarize(
                modelPath,
                mcpServerUrl
        );
        System.out.println("Zusammenfassung:");
        System.out.println(toolCallingResult.summary());
        printToolCallingStats(
                toolCallingResult.toolCalls(),
                toolCallingResult.outputTokens(),
                toolCallingResult.wallSeconds(),
                toolCallingResult.wallTokensPerSecond(),
                toolCallingResult.decodeSeconds(),
                toolCallingResult.decodeTokensPerSecond()
        );
        System.out.println();

        // System.out.println("=== Public API ===");
        // PublicAPISummary publicAPISummary = new PublicAPISummary();
        // System.out.println(publicAPISummary.summarize(commits));
        // System.out.println();
        // System.out.println("=== Local API ===");
        // LocalAPISummary localAPISummary = new LocalAPISummary();
        // List<String> modelIds = localAPISummary.listSupportedModels();
        // System.out.println("Unterstuetzte Modelle:");
        // for (String modelId : modelIds) {
        //     System.out.println("- " + modelId);
        // }
        // System.out.println();
        //
        // for (String modelId : modelIds) {
        //     System.out.println("Starte Zusammenfassung fuer Modell: " + modelId);
        //     LocalAPISummary.ModelSummary modelSummary = localAPISummary.summarizeModel(commits, modelId, 300);
        //     if (modelSummary.error() != null) {
        //         System.out.println("Fehler: " + modelSummary.error());
        //     } else {
        //         System.out.println("Zusammenfassung:");
        //         System.out.println(modelSummary.summary());
        //         printGenerationStats(modelSummary.generationStats(), 300);
        //     }
        //     System.out.println();
        // }
    }

    private static void printToolCallingStats(int toolCalls,
                                              int outputTokens,
                                              double wallSeconds,
                                              double wallTokensPerSecond,
                                              double decodeSeconds,
                                              double decodeTokensPerSecond) {
        System.out.println();
        System.out.println("Statistik:");
        System.out.println("- tool calls: " + toolCalls);
        System.out.println("- output tokens: " + outputTokens);
        System.out.println("- wall seconds: " + wallSeconds);
        System.out.println("- output tokens/s: " + wallTokensPerSecond);
        System.out.println("- decode seconds: " + decodeSeconds);
        System.out.println("- decode tokens/s: " + decodeTokensPerSecond);
    }

    private static Properties loadProperties() {
        try (InputStream inputStream = App.class.getClassLoader().getResourceAsStream("application.properties")) {
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
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing property: " + key);
        }
        return value.trim();
    }
}
