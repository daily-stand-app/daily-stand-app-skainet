package daily.standapp.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class GitLogCommand {
    private static final int GIT_TIMEOUT_SECONDS = 10;

    public String run(Path repositoryPath, int maxCount) {
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
        System.out.println("[git-log] Executing command: " + String.join(" ", command));

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
                System.out.println("[git-log] Command result:");
                System.out.println("Keine Commits gefunden.");
                return "Keine Commits gefunden.";
            }
            System.out.println("[git-log] Command result:");
            System.out.println(output);
            return output;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to run git log.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running git log.", exception);
        }
    }
}
