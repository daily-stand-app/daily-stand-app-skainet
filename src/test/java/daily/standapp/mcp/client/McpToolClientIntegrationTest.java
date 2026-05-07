package daily.standapp.mcp.client;

import daily.standapp.mcp.server.GitLogMcpServerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import sk.ainet.apps.kllama.chat.java.JavaTool;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolClientIntegrationTest {

    @Test
    void discoversAndCallsGitLogToolFromMcpServer() {
        try (ConfigurableApplicationContext serverContext = new SpringApplicationBuilder(GitLogMcpServerApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(
                        "server.port=18085",
                        "server.shutdown=immediate",
                        "spring.lifecycle.timeout-per-shutdown-phase=1s",
                        "spring.main.banner-mode=off",
                        "spring.ai.mcp.server.annotation-scanner.enabled=false",
                        "spring.ai.mcp.server.name=git-log-mcp-server",
                        "spring.ai.mcp.server.version=1.0.0",
                        "spring.ai.mcp.server.type=SYNC"
                )
                .run();
             McpToolClient mcpToolClient = McpToolClient.connect("http://localhost:18085")) {

            List<JavaTool> tools = mcpToolClient.loadTools(new AtomicInteger());

            assertFalse(tools.isEmpty());

            String output = tools.getFirst().execute(Map.of(
                    "repositoryPath", Path.of("..", "example.git").toAbsolutePath().normalize().toString(),
                    "maxCount", 3
            ));

            assertTrue(output.contains("Committer:"));
            assertTrue(output.contains("E-Mail:"));
            assertTrue(output.contains("Message:"));
        }
    }
}
