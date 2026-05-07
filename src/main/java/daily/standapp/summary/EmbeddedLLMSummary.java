package daily.standapp.summary;

import daily.standapp.git.GitCommitEntry;
import sk.ainet.apps.kllama.java.GenerationConfig;
import sk.ainet.apps.kllama.java.KLlamaJava;
import sk.ainet.apps.kllama.java.KLlamaSession;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class EmbeddedLLMSummary {
    private static final GenerationConfig GENERATION_CONFIG = GenerationConfig.builder()
            .maxTokens(300)
            .temperature(0.2f)
            .build();

    public String summarize(String modelPath, List<GitCommitEntry> commits) {
        if (commits.isEmpty()) {
            return "Keine Commits gefunden.";
        }

        Path ggufPath = Path.of(modelPath);
        if (!Files.exists(ggufPath)) {
            throw new IllegalStateException("Model file not found: " + ggufPath.toAbsolutePath());
        }

        String prompt = buildPrompt(commits);
        String systemPrompt = "You are a concise assistant for daily standup summaries. Answer in German.";

        try (KLlamaSession session = KLlamaJava.loadGGUF(ggufPath, systemPrompt)) {
            return session.generate(prompt, GENERATION_CONFIG).trim();
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
}
