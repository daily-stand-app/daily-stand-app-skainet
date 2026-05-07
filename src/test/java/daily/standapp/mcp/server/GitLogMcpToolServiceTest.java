package daily.standapp.mcp.server;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLogMcpToolServiceTest {

    @Test
    void readsCommitsViaJGitForConfiguredRepository() {
        GitLogMcpToolService service = new GitLogMcpToolService();

        String output = service.gitLog(Path.of("..", "example.git").toString(), 3);

        assertFalse(output.isBlank());
        assertTrue(output.contains("Committer:"));
        assertTrue(output.contains("E-Mail:"));
        assertTrue(output.contains("Message:"));
    }
}
