package daily.standapp.summary;

import sk.ainet.apps.kllama.chat.AgentConfig;
import sk.ainet.apps.kllama.chat.ModelMetadata;
import sk.ainet.apps.kllama.chat.ToolDefinition;
import sk.ainet.apps.kllama.chat.java.JavaAgentLoop;
import sk.ainet.apps.kllama.chat.java.JavaTool;
import sk.ainet.apps.kllama.chat.java.JavaTools;
import sk.ainet.apps.kllama.java.KLlamaJava;
import sk.ainet.apps.kllama.java.KLlamaSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class ToolCallingSummary {
    private static final int DEFAULT_MAX_COMMITS = 20;
    private static final int GIT_TIMEOUT_SECONDS = 10;

    public ToolCallingResult summarize(String modelPath, Path repositoryPath, String userPrompt) {
        Path ggufPath = Path.of(modelPath);
        if (!Files.exists(ggufPath)) {
            throw new IllegalStateException("Model file not found: " + ggufPath.toAbsolutePath());
        }

        AtomicInteger toolCalls = new AtomicInteger();
        JavaTool gitLogTool = gitLogTool(repositoryPath, toolCalls);

        try (KLlamaSession session = KLlamaJava.loadGGUF(ggufPath, null)) {
            JavaAgentLoop agent = JavaAgentLoop.builder()
                    .session(session)
                    .tool(gitLogTool)
                    .systemPrompt("""
                            You are a concise assistant for daily standup summaries.
                            Answer in German.
                            When the user asks to summarize a git repository, call the git_log tool exactly once before answering.
                            """)
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
                return runGitLog(repositoryPath, maxCount);
            }
        };
    }

    private String runGitLog(Path repositoryPath, int maxCount) {
        List<String> command = List.of(
                "git",
                "--no-pager",
                "-C",
                repositoryPath.toAbsolutePath().normalize().toString(),
                "log",
                "--max-count=" + maxCount,
                "--pretty=format:Committer: %an%nE-Mail: %ae%nMessage: %s%n---"
        );

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("git log timed out for repository: "
                        + repositoryPath.toAbsolutePath().normalize());
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                throw new IllegalStateException("git log failed: " + output);
            }
            if (output.isBlank()) {
                return "Keine Commits gefunden.";
            }
            return output;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to run git log.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running git log.", exception);
        }
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
