# daily-standapp-skainet

Kleines Java-25-Maven-Projekt fuer eine lokale Standup-Anwendung mit SKaiNET.
Der Branch `step1` zeigt die einfache Variante: Die Commits werden zuerst mit JGit gelesen
und danach direkt an das eingebettete Modell uebergeben.

## Build

```bash
./mvnw compile
```

## Start auf der Kommandozeile

Zuerst die lokale Konfiguration anlegen:

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Danach in `src/main/resources/application.properties` den Modellpfad setzen:

```properties
embedded.model.path=path/to/llama-3.2-1b-instruct-q8_0.gguf
```

Dann die Anwendung starten:

```bash
java -Xms2g -Xmx16g --enable-preview --add-modules jdk.incubator.vector \
  -cp "target/classes:$(cat target/classpath.txt)" \
  daily.standapp.App
```

`target/classpath.txt` wird beim `compile`-Schritt automatisch erzeugt.

## Von `step1` nach `step2`

Ziel von `step2`:

- nicht mehr zuerst mit JGit lesen
- stattdessen ein Tool `git_log` fuer das Modell registrieren
- `git log` ueber die Kommandozeile auf `../example.git` ausfuehren
- die Tool-Antwort erst dann an das Modell geben, wenn das Modell sie anfordert

### 1. `git log` in eine eigene Klasse auslagern

Lege `src/main/java/daily/standapp/git/GitLogCommand.java` an. Das ist bewusst einfach gehalten
und kann fast 1:1 uebernommen werden:

```java
package daily.standapp.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class GitLogCommand {
    private static final int GIT_TIMEOUT_SECONDS = 10;

    public String run(Path repositoryPath, int maxCount) {
        List<String> command = List.of(
                "git",
                "--no-pager",
                "-C",
                repositoryPath.toAbsolutePath().normalize().toString(),
                "log",
                "--max-count=" + maxCount,
                "--pretty=format:Committer: %an%nE-Mail: %ae%nMessage: %s%n---"
        );

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        System.out.println("[git-log] Executing command: " + String.join(" ", command));

        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("git log timed out");
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                throw new IllegalStateException("git log failed: " + output);
            }

            System.out.println("[git-log] Command result:");
            System.out.println(output.isBlank() ? "Keine Commits gefunden." : output);
            return output.isBlank() ? "Keine Commits gefunden." : output;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to run git log.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running git log.", exception);
        }
    }
}
```

### 2. Neue Klasse `ToolCallingSummary` anlegen

Ersetze den direkten `EmbeddedLLMSummary`-Aufruf durch eine Klasse, die das Tool beim Agenten registriert.

Wichtige Bausteine:

```java
private static final String SYSTEM_PROMPT = """
        Du bist ein knapper Assistent für Daily-Standup-Zusammenfassungen.
        Antworte auf Deutsch.
        Wenn der Nutzer ein Git-Repository zusammengefasst haben möchte,
        rufe vor deiner Antwort genau einmal das Tool git_log auf.
        """;
```

```java
private String buildPrompt() {
    return """
            Ich bin ein erfahrener Java Senior Developer. Für mein Daily Stand-Up morgen früh muss ich zusammenfassen, was ich bisher gemacht habe.
            Fasse mir das Git-Repository kurz zusammen, so dass ich es meinen Kollegen erzählen kann.
            Wenn du Commit-Informationen brauchst, nutze das Tool git_log.
            """.trim();
}
```

Das Tool selbst kann so registriert werden:

```java
JavaTool gitLogTool = new JavaTool() {
    @Override
    public ToolDefinition getDefinition() {
        return JavaTools.definition(
                "git_log",
                "Read recent git commits from the configured repository using the git command line.",
                """
                {
                  "type": "object",
                  "properties": {
                    "max_count": {
                      "type": "integer",
                      "description": "Maximum number of recent commits to read"
                    }
                  }
                }
                """
        );
    }

    @Override
    public String execute(Map<String, ?> arguments) {
        int maxCount = positiveInt(arguments.get("max_count"), 20);
        System.out.println("[tool] Calling git_log with max_count=" + maxCount);
        return gitLogCommand.run(repositoryPath, maxCount);
    }
};
```

Der eigentliche Agent-Loop sieht dann in etwa so aus:

```java
try (KLlamaSession session = KLlamaJava.loadGGUF(Path.of(modelPath), SYSTEM_PROMPT)) {
    JavaAgentLoop agent = JavaAgentLoop.builder()
            .session(session)
            .tool(gitLogTool)
            .systemPrompt(SYSTEM_PROMPT)
            .config(new AgentConfig())
            .template("llama3")
            .metadata(new ModelMetadata())
            .build();

    String finalResponse = agent.chat(buildPrompt(), token -> { });
    return finalResponse.trim();
}
```

### 3. `App.java` auf Tool Calling umstellen

In `step1` liest `App.java` die Commits noch direkt per `GitHistoryReader`. Fuer `step2`
solltest du diesen Teil auskommentieren und durch `ToolCallingSummary` ersetzen:

```java
Path repositoryPath = Path.of("..", "example.git");

System.out.println("=== Tool Calling ===");
ToolCallingSummary toolCallingSummary = new ToolCallingSummary();
ToolCallingSummary.ToolCallingResult toolCallingResult =
        toolCallingSummary.summarize(modelPath, repositoryPath);

System.out.println("Zusammenfassung:");
System.out.println(toolCallingResult.summary());
```

Der bisherige `EmbeddedLLMSummary`-Block kann als Referenz im Code stehen bleiben, aber auskommentiert sein.

### 4. Kleinen Test nur fuer den Kommandozeilenaufruf ergaenzen

Fuer `step2` ist ein isolierter Test fuer `git log` sinnvoll. Dafuer brauchst du in `pom.xml`
zusaetzlich JUnit 5:

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.13.4</version>
    <scope>test</scope>
</dependency>
```

und das Surefire-Plugin:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.4</version>
</plugin>
```

Der Test selbst kann sehr klein bleiben:

```java
@Test
void runsGitLogAgainstExampleRepository() {
    GitLogCommand gitLogCommand = new GitLogCommand();

    String output = gitLogCommand.run(Path.of("..", "example.git"), 3);

    assertFalse(output.isBlank());
    assertTrue(output.contains("Committer:"));
    assertTrue(output.contains("Message:"));
}
```
