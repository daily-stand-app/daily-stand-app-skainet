package daily.standapp.summary;

import daily.standapp.mcp.client.McpToolClient;
import sk.ainet.apps.kllama.chat.AgentConfig;
import sk.ainet.apps.kllama.chat.ModelMetadata;
import sk.ainet.apps.kllama.chat.java.JavaAgentLoop;
import sk.ainet.apps.kllama.chat.java.JavaTool;
import sk.ainet.apps.kllama.java.KLlamaJava;
import sk.ainet.apps.kllama.java.KLlamaSession;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class McpToolCallingSummary {
    private static final String SYSTEM_PROMPT = """
            Du bist ein knapper Assistent für Daily-Standup-Zusammenfassungen.
            Antworte auf Deutsch.
            Wenn der Nutzer ein Git-Repository zusammengefasst haben möchte,
            rufe vor deiner Antwort genau einmal das Tool git_log auf.
            """;

    public ToolCallingResult summarize(String modelPath, String serverUrl, Path repositoryPath) {
        Path ggufPath = Path.of(modelPath);
        if (!Files.exists(ggufPath)) {
            throw new IllegalStateException("Model file not found: " + ggufPath.toAbsolutePath());
        }
        Path resolvedRepositoryPath = repositoryPath.toAbsolutePath().normalize();

        AtomicInteger toolCalls = new AtomicInteger();
        try (McpToolClient mcpToolClient = McpToolClient.connect(serverUrl);
             KLlamaSession session = KLlamaJava.loadGGUF(ggufPath, SYSTEM_PROMPT)) {

            List<JavaTool> tools = mcpToolClient.loadTools(toolCalls);
            if (tools.isEmpty()) {
                throw new IllegalStateException("No MCP tools discovered from server: " + serverUrl);
            }

            JavaAgentLoop.Builder builder = JavaAgentLoop.builder()
                    .session(session)
                    .systemPrompt(SYSTEM_PROMPT)
                    .config(new AgentConfig())
                    .template("llama3")
                    .metadata(new ModelMetadata());

            for (JavaTool tool : tools) {
                builder.tool(tool);
            }

            JavaAgentLoop agent = builder.build();

            final long[] firstTokenNanos = {0L};
            final long[] lastTokenNanos = {0L};
            final int[] tokenCount = {0};

            Consumer<String> meter = token -> {
                long now = System.nanoTime();
                if (firstTokenNanos[0] == 0L) {
                    firstTokenNanos[0] = now;
                }
                lastTokenNanos[0] = now;
                tokenCount[0]++;
            };

            long startWallNanos = System.nanoTime();
            String finalResponse = agent.chat(buildPrompt(resolvedRepositoryPath), meter);
            long endWallNanos = System.nanoTime();

            int outputTokens = tokenCount[0];
            double wallSeconds = (endWallNanos - startWallNanos) / 1_000_000_000.0;
            double decodeSeconds = firstTokenNanos[0] == 0L
                    ? 0.0
                    : (lastTokenNanos[0] - firstTokenNanos[0]) / 1_000_000_000.0;
            double wallTokensPerSecond = wallSeconds > 0.0 ? outputTokens / wallSeconds : 0.0;
            double decodeTokensPerSecond = (outputTokens > 1 && decodeSeconds > 0.0)
                    ? (outputTokens - 1) / decodeSeconds
                    : 0.0;

            return new ToolCallingResult(
                    finalResponse.trim(),
                    toolCalls.get(),
                    outputTokens,
                    wallSeconds,
                    wallTokensPerSecond,
                    decodeSeconds,
                    decodeTokensPerSecond
            );
        }
    }

    private String buildPrompt(Path repositoryPath) {
        return ("""
                Ich bin ein erfahrener Java Senior Developer. Für mein Daily Stand-Up morgen früh muss ich zusammenfassen, was ich bisher gemacht habe.
                Fasse mir das Git-Repository kurz zusammen, so dass ich es meinen Kollegen erzählen kann.
                Das zu analysierende lokale Git-Repository liegt unter: %s
                Wenn du Commit-Informationen brauchst, nutze das Tool git_log und übergib diesen Repository-Pfad.
                """.formatted(repositoryPath)).trim();
    }

    public record ToolCallingResult(String summary,
                                    int toolCalls,
                                    int outputTokens,
                                    double wallSeconds,
                                    double wallTokensPerSecond,
                                    double decodeSeconds,
                                    double decodeTokensPerSecond) {
    }
}
