package daily.standapp.mcp.server;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GitLogMcpServerConfiguration {

    @Bean
    ToolCallbackProvider gitLogTools(GitLogMcpToolService gitLogMcpToolService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(gitLogMcpToolService)
                .build();
    }
}
