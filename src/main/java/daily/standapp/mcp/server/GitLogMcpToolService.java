package daily.standapp.mcp.server;

import daily.standapp.git.GitCommitEntry;
import daily.standapp.git.GitHistoryReader;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class GitLogMcpToolService {
    private static final int DEFAULT_MAX_COUNT = 20;

    private final GitHistoryReader gitHistoryReader;

    public GitLogMcpToolService() {
        this.gitHistoryReader = new GitHistoryReader();
    }

    @Tool(name = "git_log", description = "Liest aktuelle Commits aus einem lokalen Git-Repository.")
    public String gitLog(
            @ToolParam(description = "Pfad zu einem lokalen Git-Repository") String repositoryPath,
            @ToolParam(description = "Maximale Anzahl der letzten Commits") Integer maxCount) {
        Path resolvedRepositoryPath = Path.of(repositoryPath).toAbsolutePath().normalize();
        int effectiveMaxCount = maxCount != null && maxCount > 0 ? maxCount : DEFAULT_MAX_COUNT;
        List<GitCommitEntry> commits = gitHistoryReader.readHistory(resolvedRepositoryPath, effectiveMaxCount);

        if (commits.isEmpty()) {
            return "Keine Commits gefunden.";
        }

        StringBuilder builder = new StringBuilder();
        for (GitCommitEntry commit : commits) {
            builder.append("Committer: ")
                    .append(commit.committer())
                    .append(System.lineSeparator())
                    .append("E-Mail: ")
                    .append(commit.emailAddress())
                    .append(System.lineSeparator())
                    .append("Message: ")
                    .append(commit.commitMessage())
                    .append(System.lineSeparator())
                    .append("---")
                    .append(System.lineSeparator());
        }

        return builder.toString().trim();
    }
}
