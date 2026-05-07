package daily.standapp.git;

public record GitCommitEntry(
        String committer,
        String emailAddress,
        String commitMessage
) {
}
