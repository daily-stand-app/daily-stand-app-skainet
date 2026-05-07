package daily.standapp;

import daily.standapp.summary.ToolCallingSummary;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;

public final class App {

    private App() {
    }

    public static void main(String[] args) {
        Properties properties = loadProperties();
        String modelPath = requiredProperty(properties, "embedded.model.path");
        Path repositoryPath = Path.of("..", "example.git");

        // GitHistoryReader historyReader = new GitHistoryReader();
        // List<GitCommitEntry> commits = historyReader.readHistory(repositoryPath);
        //
        // System.out.println("=== Embedded LLM ===");
        // EmbeddedLLMSummary embeddedLLMSummary = new EmbeddedLLMSummary();
        // System.out.println(embeddedLLMSummary.summarize(modelPath, commits));
        // System.out.println();

        System.out.println("=== Tool Calling ===");
        ToolCallingSummary toolCallingSummary = new ToolCallingSummary();
        ToolCallingSummary.ToolCallingResult toolCallingResult = toolCallingSummary.summarize(
                modelPath,
                repositoryPath,
                """
                        Ich bin ein erfahrener Java Senior Developer. Für mein Daily Stand-Up morgen früh muss ich zusammenfassen, was ich bisher gemacht habe.
                        Fasse mir das Git-Repository kurz zusammen, so dass ich es meinen Kollegen erzählen kann.
                        Wenn du Commit-Informationen brauchst, nutze das Tool git_log.
                        """
        );
        System.out.println("Zusammenfassung:");
        System.out.println(toolCallingResult.summary());
        printToolCallingStats(toolCallingResult);
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

    private static void printToolCallingStats(ToolCallingSummary.ToolCallingResult toolCallingResult) {
        System.out.println();
        System.out.println("Statistik:");
        System.out.println("- tool calls: " + toolCallingResult.toolCalls());
        System.out.println("- output tokens: " + toolCallingResult.outputTokens());
        System.out.println("- wall seconds: " + toolCallingResult.wallSeconds());
        System.out.println("- output tokens/s: " + toolCallingResult.wallTokensPerSecond());
        System.out.println("- decode seconds: " + toolCallingResult.decodeSeconds());
        System.out.println("- decode tokens/s: " + toolCallingResult.decodeTokensPerSecond());
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
