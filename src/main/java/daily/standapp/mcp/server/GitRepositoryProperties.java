package daily.standapp.mcp.server;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "standapp.git")
public record GitRepositoryProperties(Path repositoryPath) {
}
