package daily.standapp.mcp.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class GitLogMcpServerApplication {

    public static void main(String[] args) {
        if (args.length == 0 || args[0].isBlank()) {
            System.err.println("Usage: daily.standapp.mcp.server.GitLogMcpServerApplication <path-to-local-git-repo> [spring-boot-args]");
            System.exit(1);
        }

        Path repositoryPath = Path.of(args[0]).toAbsolutePath().normalize();
        SpringApplication application = new SpringApplication(GitLogMcpServerApplication.class);

        Map<String, Object> defaults = new HashMap<>();
        defaults.put("server.port", "8085");
        defaults.put("spring.main.banner-mode", "off");
        defaults.put("spring.ai.mcp.server.name", "git-log-mcp-server");
        defaults.put("spring.ai.mcp.server.version", "1.0.0");
        defaults.put("spring.ai.mcp.server.type", "SYNC");
        defaults.put("spring.ai.mcp.server.annotation-scanner.enabled", "false");
        defaults.put("spring.ai.mcp.server.instructions",
                "This server exposes tools for reading commit history from a local Git repository.");
        defaults.put("standapp.git.repository-path", repositoryPath.toString());
        application.setDefaultProperties(defaults);

        String[] springArgs = Arrays.copyOfRange(args, 1, args.length);
        application.run(springArgs);
    }
}
