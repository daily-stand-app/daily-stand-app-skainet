package daily.standapp.git;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLogCommandTest {

    @Test
    void runsGitLogAgainstExampleRepository() {
        GitLogCommand gitLogCommand = new GitLogCommand();

        String output = gitLogCommand.run(Path.of("..", "example.git"), 3);

        assertFalse(output.isBlank());
        assertTrue(output.contains("Committer:"));
        assertTrue(output.contains("E-Mail:"));
        assertTrue(output.contains("Message:"));
        assertTrue(output.contains("STANDUP-"));
    }
}
