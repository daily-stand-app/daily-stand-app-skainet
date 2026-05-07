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
    private final GitRepositoryProperties gitRepositoryProperties;

    public GitLogMcpToolService(GitRepositoryProperties gitRepositoryProperties) {
        this.gitHistoryReader = new GitHistoryReader();
        this.gitRepositoryProperties = gitRepositoryProperties;
    }

    @Tool(name = "git_log", description = "Liest aktuelle Commits aus einem lokalen Git-Repository.")
    public String gitLog(@ToolParam(description = "Maximale Anzahl der letzten Commits") Integer maxCount) {
        Path repositoryPath = gitRepositoryProperties.repositoryPath();
        int effectiveMaxCount = maxCount != null && maxCount > 0 ? maxCount : DEFAULT_MAX_COUNT;
        List<GitCommitEntry> commits = gitHistoryReader.readHistory(repositoryPath, effectiveMaxCount);

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
