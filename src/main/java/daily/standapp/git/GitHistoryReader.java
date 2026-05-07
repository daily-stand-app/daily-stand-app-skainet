package daily.standapp.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GitHistoryReader {

    public List<GitCommitEntry> readHistory(Path repositoryPath) {
        return readHistory(repositoryPath, Integer.MAX_VALUE);
    }

    public List<GitCommitEntry> readHistory(Path repositoryPath, int maxCount) {
        try (Repository repository = openRepository(repositoryPath);
             Git git = new Git(repository)) {

            List<GitCommitEntry> commits = new ArrayList<>();
            int effectiveMaxCount = maxCount > 0 ? maxCount : Integer.MAX_VALUE;
            Iterable<org.eclipse.jgit.revwalk.RevCommit> logEntries = git.log()
                    .setMaxCount(effectiveMaxCount)
                    .call();

            for (org.eclipse.jgit.revwalk.RevCommit commit : logEntries) {
                commits.add(new GitCommitEntry(
                        commit.getCommitterIdent().getName(),
                        commit.getCommitterIdent().getEmailAddress(),
                        commit.getShortMessage()
                ));
            }
            return commits;
        } catch (IOException | GitAPIException exception) {
            throw new IllegalStateException("Failed to read git history from " + repositoryPath.toAbsolutePath(), exception);
        }
    }

    private Repository openRepository(Path repositoryPath) throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        builder.findGitDir(repositoryPath.toFile());
        Repository repository = builder.build();
        if (repository.getDirectory() == null) {
            throw new IllegalArgumentException("Path is not a git repository: " + repositoryPath.toAbsolutePath());
        }
        return repository;
    }
}
