package daily.standapp.mcp.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import sk.ainet.apps.kllama.chat.java.JavaTool;
import sk.ainet.apps.kllama.chat.java.JavaTools;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class McpToolClient implements AutoCloseable {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ConfigurableApplicationContext context;
    private final SyncMcpToolCallbackProvider toolCallbackProvider;

    private McpToolClient(ConfigurableApplicationContext context,
                          SyncMcpToolCallbackProvider toolCallbackProvider) {
        this.context = context;
        this.toolCallbackProvider = toolCallbackProvider;
    }

    public static McpToolClient connect(String serverUrl) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(McpClientBootstrapConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties(Map.of(
                        "spring.main.banner-mode", "off",
                        "spring.ai.mcp.server.enabled", "false",
                        "spring.ai.mcp.client.type", "SYNC",
                        "spring.ai.mcp.client.sse.connections.gitserver.url", serverUrl
                ))
                .run();

        SyncMcpToolCallbackProvider toolCallbackProvider = context.getBean(SyncMcpToolCallbackProvider.class);
        return new McpToolClient(context, toolCallbackProvider);
    }

    public List<JavaTool> loadTools(AtomicInteger toolCalls) {
        return Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .map(toolCallback -> adaptTool(toolCallback, toolCalls))
                .toList();
    }

    private JavaTool adaptTool(ToolCallback toolCallback, AtomicInteger toolCalls) {
        ToolDefinition toolDefinition = toolCallback.getToolDefinition();
        return new JavaTool() {
            @Override
            public sk.ainet.apps.kllama.chat.ToolDefinition getDefinition() {
                return JavaTools.definition(
                        toolDefinition.name(),
                        toolDefinition.description(),
                        toolDefinition.inputSchema()
                );
            }

            @Override
            public String execute(Map<String, ?> arguments) {
                toolCalls.incrementAndGet();
                String toolInput = toJson(arguments);
                System.out.println("[mcp-client] Calling tool " + toolDefinition.name() + " with input: " + toolInput);
                String result = normalizeToolResult(toolCallback.call(toolInput));
                System.out.println("[mcp-client] Tool result from " + toolDefinition.name() + ":");
                System.out.println(result);
                return result;
            }
        };
    }

    private String toJson(Map<String, ?> arguments) {
        try {
            return OBJECT_MAPPER.writeValueAsString(arguments);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize MCP tool input to JSON.", exception);
        }
    }

    private String normalizeToolResult(String rawResult) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawResult);
            if (root.isArray() && !root.isEmpty() && root.get(0).has("text")) {
                String text = root.get(0).get("text").asText();
                if (text.startsWith("\"") && text.endsWith("\"")) {
                    return OBJECT_MAPPER.readValue(text, String.class);
                }
                return text;
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to the raw transport payload if the server returned plain text.
        }
        return rawResult;
    }

    @Override
    public void close() {
        context.close();
    }
}
