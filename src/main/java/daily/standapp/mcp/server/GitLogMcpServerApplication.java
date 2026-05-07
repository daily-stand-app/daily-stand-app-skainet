package daily.standapp.mcp.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class GitLogMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(GitLogMcpServerApplication.class);

        Map<String, Object> defaults = new HashMap<>();
        defaults.put("server.port", "8085");
        defaults.put("spring.main.banner-mode", "off");
        defaults.put("spring.ai.mcp.server.name", "git-log-mcp-server");
        defaults.put("spring.ai.mcp.server.version", "1.0.0");
        defaults.put("spring.ai.mcp.server.type", "SYNC");
        defaults.put("spring.ai.mcp.server.annotation-scanner.enabled", "false");
        defaults.put("spring.ai.mcp.server.instructions",
                "This server exposes tools for reading commit history from a local Git repository. "
                        + "The repository path must be provided as a tool argument.");
        application.setDefaultProperties(defaults);
        application.run(args);
    }
}
