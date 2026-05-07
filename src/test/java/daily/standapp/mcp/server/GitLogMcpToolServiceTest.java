package daily.standapp.mcp.server;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLogMcpToolServiceTest {

    @Test
    void readsCommitsViaJGitForConfiguredRepository() {
        GitRepositoryProperties properties = new GitRepositoryProperties(Path.of("..", "example.git"));
        GitLogMcpToolService service = new GitLogMcpToolService(properties);

        String output = service.gitLog(3);

        assertFalse(output.isBlank());
        assertTrue(output.contains("Committer:"));
        assertTrue(output.contains("E-Mail:"));
        assertTrue(output.contains("Message:"));
    }
}
