package daily.standapp;

import daily.standapp.git.GitCommitEntry;
import daily.standapp.git.GitHistoryReader;
import daily.standapp.summary.EmbeddedLLMSummary;
import daily.standapp.summary.LocalAPISummary;
import daily.standapp.summary.PublicAPISummary;

import java.nio.file.Path;
import java.util.List;

public final class App {

    private App() {
    }

    public static void main(String[] args) {
        String modelPath = "path/to/llama-3.2-1b-instruct-q8_0.gguf";
        int maxOutputTokens = 300;

        GitHistoryReader historyReader = new GitHistoryReader();
        List<GitCommitEntry> commits = historyReader.readHistory(Path.of("..", "example.git"));

        System.out.println("=== Embedded LLM ===");
        EmbeddedLLMSummary embeddedLLMSummary = new EmbeddedLLMSummary();
        System.out.println(embeddedLLMSummary.summarize(modelPath, commits));
        System.out.println();

        // System.out.println("=== Public API ===");
        // PublicAPISummary publicAPISummary = new PublicAPISummary();
        // System.out.println(publicAPISummary.summarize(commits));
        // System.out.println();

        System.out.println("=== Local API ===");
        LocalAPISummary localAPISummary = new LocalAPISummary();
        List<String> modelIds = localAPISummary.listSupportedModels();
        System.out.println("Unterstuetzte Modelle:");
        for (String modelId : modelIds) {
            System.out.println("- " + modelId);
        }
        System.out.println();

        for (String modelId : modelIds) {
            System.out.println("Starte Zusammenfassung fuer Modell: " + modelId);
            LocalAPISummary.ModelSummary modelSummary = localAPISummary.summarizeModel(commits, modelId, maxOutputTokens);
            if (modelSummary.error() != null) {
                System.out.println("Fehler: " + modelSummary.error());
            } else {
                System.out.println("Zusammenfassung:");
                System.out.println(modelSummary.summary());
                printGenerationStats(modelSummary.generationStats(), maxOutputTokens);
            }
            System.out.println();
        }
    }

    private static void printGenerationStats(LocalAPISummary.GenerationStats generationStats,
                                             int maxOutputTokens) {
        if (generationStats == null) {
            return;
        }

        System.out.println();
        System.out.println("Statistik:");
        System.out.println("- status: " + generationStats.status());
        System.out.println("- input tokens: " + generationStats.inputTokens());
        System.out.println("- output tokens: " + generationStats.outputTokens());
        System.out.println("- total tokens: " + generationStats.totalTokens());
        System.out.println("- reasoning tokens: " + generationStats.reasoningTokens());
        System.out.println("- duration seconds: " + generationStats.durationSeconds());
        System.out.println("- output tokens/s: " + generationStats.outputTokensPerSecond());
        System.out.println("- max output tokens: " + maxOutputTokens);
    }
}
