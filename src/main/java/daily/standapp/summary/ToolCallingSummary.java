package daily.standapp.summary;

import daily.standapp.git.GitLogCommand;
import sk.ainet.apps.kllama.chat.AgentConfig;
import sk.ainet.apps.kllama.chat.ModelMetadata;
import sk.ainet.apps.kllama.chat.ToolDefinition;
import sk.ainet.apps.kllama.chat.java.JavaAgentLoop;
import sk.ainet.apps.kllama.chat.java.JavaTool;
import sk.ainet.apps.kllama.chat.java.JavaTools;
import sk.ainet.apps.kllama.java.KLlamaJava;
import sk.ainet.apps.kllama.java.KLlamaSession;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class ToolCallingSummary {
    private static final int DEFAULT_MAX_COMMITS = 20;
    private static final String SYSTEM_PROMPT = """
            Du bist ein knapper Assistent für Daily-Standup-Zusammenfassungen.
            Antworte auf Deutsch.
            Wenn der Nutzer ein Git-Repository zusammengefasst haben möchte,
            rufe vor deiner Antwort genau einmal das Tool git_log auf.
            """;
    private final GitLogCommand gitLogCommand;

    public ToolCallingSummary() {
        this(new GitLogCommand());
    }

    ToolCallingSummary(GitLogCommand gitLogCommand) {
        this.gitLogCommand = gitLogCommand;
    }

    public ToolCallingResult summarize(String modelPath, Path repositoryPath) {
        Path ggufPath = Path.of(modelPath);
        if (!Files.exists(ggufPath)) {
            throw new IllegalStateException("Model file not found: " + ggufPath.toAbsolutePath());
        }

        AtomicInteger toolCalls = new AtomicInteger();
        JavaTool gitLogTool = gitLogTool(repositoryPath, toolCalls);
        String userPrompt = buildPrompt();

        try (KLlamaSession session = KLlamaJava.loadGGUF(ggufPath, SYSTEM_PROMPT)) {
            JavaAgentLoop agent = JavaAgentLoop.builder()
                    .session(session)
                    .tool(gitLogTool)
                    .systemPrompt(SYSTEM_PROMPT)
                    .config(new AgentConfig())
                    .template("llama3")
                    .metadata(new ModelMetadata())
                    .build();

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
            String finalResponse = agent.chat(userPrompt.trim(), meter);
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

    private String buildPrompt() {
        return """
                Ich bin ein erfahrener Java Senior Developer. Für mein Daily Stand-Up morgen früh muss ich zusammenfassen, was ich bisher gemacht habe.
                Fasse mir das Git-Repository kurz zusammen, so dass ich es meinen Kollegen erzählen kann.
                Wenn du Commit-Informationen brauchst, nutze das Tool git_log.
                """.trim();
    }

    private JavaTool gitLogTool(Path repositoryPath, AtomicInteger toolCalls) {
        return new JavaTool() {
            @Override
            public ToolDefinition getDefinition() {
                return JavaTools.definition(
                        "git_log",
                        "Read recent git commits from the configured repository using the git command line.",
                        """
                        {
                          "type": "object",
                          "properties": {
                            "max_count": {
                              "type": "integer",
                              "description": "Maximum number of recent commits to read"
                            }
                          }
                        }
                        """
                );
            }

            @Override
            public String execute(Map<String, ?> arguments) {
                toolCalls.incrementAndGet();
                int maxCount = positiveInt(arguments.get("max_count"), DEFAULT_MAX_COMMITS);
                System.out.println("[tool] Calling git_log for repository "
                        + repositoryPath.toAbsolutePath().normalize()
                        + " with max_count=" + maxCount);
                String output = gitLogCommand.run(repositoryPath, maxCount);
                System.out.println("[tool] git_log result:");
                System.out.println(output);
                return output;
            }
        };
    }

    private int positiveInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }

        try {
            return Math.max(1, Integer.parseInt(value.toString().trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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
