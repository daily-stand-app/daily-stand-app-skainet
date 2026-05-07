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

Dann zuerst den MCP-Server starten:

```bash
java -cp "target/classes:$(cat target/classpath.txt)" \
  daily.standapp.mcp.server.GitLogMcpServerApplication ../example.git
```

Danach die eigentliche Anwendung starten:

```bash
java -Xms2g -Xmx16g --enable-preview --add-modules jdk.incubator.vector \
  -cp "target/classes:$(cat target/classpath.txt)" \
  daily.standapp.App
```

`target/classpath.txt` wird beim `compile`-Schritt automatisch erzeugt.

Zusatzkonfiguration in `application.properties`:

```properties
mcp.server.url=http://localhost:8085
```
