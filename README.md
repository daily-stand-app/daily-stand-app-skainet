# daily-standapp-skainet

Kleines Java-25-Maven-Projekt fuer eine lokale Standup-Anwendung mit SKaiNET.
Der Stand auf `step3` nutzt MCP: Ein separater Spring-AI-MCP-Server stellt `git_log`
bereit und der SKaiNET-Client bindet dieses Tool ueber einen MCP-Client ein.

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

Dann zuerst den MCP-Server starten.

Unix/macOS:

```bash
java -cp "target/classes:$(cat target/classpath.txt)" \
  daily.standapp.mcp.server.GitLogMcpServerApplication
```

Windows PowerShell:

```powershell
java -cp "target/classes;$((Get-Content target/classpath.txt -Raw).Trim())" `
  daily.standapp.mcp.server.GitLogMcpServerApplication
```

Danach die eigentliche Anwendung starten.

Unix/macOS:

```bash
java -Xms2g -Xmx16g --enable-preview --add-modules jdk.incubator.vector \
  -cp "target/classes:$(cat target/classpath.txt)" \
  daily.standapp.App
```

Windows PowerShell:

```powershell
java -Xms2g -Xmx16g --enable-preview --add-modules jdk.incubator.vector `
  -cp "target/classes;$((Get-Content target/classpath.txt -Raw).Trim())" `
  daily.standapp.App
```

`target/classpath.txt` wird beim `compile`-Schritt automatisch erzeugt.

Zusatzkonfiguration in `application.properties`:

```properties
mcp.server.url=http://localhost:8085
```

Der Pfad zum lokalen Git-Repository wird in `step3` nicht mehr beim Serverstart gesetzt.
Stattdessen uebergibt der Client ihn beim Tool-Aufruf an `git_log`.

## MCP-Server manuell mit `curl` testen

Wenn der MCP-Server laeuft, kannst du ihn auch ohne die eigentliche App direkt per HTTP ansprechen.

### 1. SSE-Endpoint oeffnen

In einem ersten Terminal:

```bash
curl -sS -N http://localhost:8085/sse
```

Die Antwort sieht ungefaehr so aus:

```text
event:endpoint
data:/mcp/message?sessionId=262b8951-23ec-4f9f-a17c-17f78d5d375e
```

Die `sessionId` aus dieser Ausgabe brauchst du fuer die naechsten Requests.

### 2. MCP initialisieren

In einem zweiten Terminal:

```bash
curl -sS -X POST 'http://localhost:8085/mcp/message?sessionId=262b8951-23ec-4f9f-a17c-17f78d5d375e' \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"curl","version":"1.0"}}}'
```

Danach noch die `initialized`-Notification schicken:

```bash
curl -sS -X POST 'http://localhost:8085/mcp/message?sessionId=262b8951-23ec-4f9f-a17c-17f78d5d375e' \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'
```

### 3. `git_log` aufrufen

```bash
curl -sS -X POST 'http://localhost:8085/mcp/message?sessionId=262b8951-23ec-4f9f-a17c-17f78d5d375e' \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"git_log","arguments":{"repositoryPath":"../example.git","maxCount":3}}}'
```

Die eigentliche Tool-Antwort erscheint wieder im ersten Terminal auf dem offenen `/sse`-Stream.

Wenn du nur pruefen willst, ob der Server lebt, reicht bereits dieser Aufruf:

```bash
curl -sS -N http://localhost:8085/sse
```
