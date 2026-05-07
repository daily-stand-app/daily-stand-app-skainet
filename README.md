# daily-standapp-skainet

Kleines Java-25-Maven-Projekt fuer eine lokale Standup-Anwendung mit SKaiNET.
Der aktuelle Stand auf `main` nutzt Tool Calling: Das Modell ruft bei Bedarf `git log`
fuer `../example.git` ueber die Kommandozeile auf und fasst die Commits danach zusammen.

## Uebungen

Die Uebung ist ueber mehrere Branches aufgebaut.

- `initial`: Startpunkt mit JGit. Die Anwendung liest `git log` aus einem lokalen Repository und kann das Ergebnis an ein oeffentliches LLM oder an einen lokalen LM-Server uebergeben.
- `step1`: Die Git-Log-Ausgabe wird an ein mit SKaiNET direkt im Prozess geladenes lokales Modell uebergeben.
- `step2`: Das lokale LLM bekommt ein Tool fuer `git log` ueber die Kommandozeile registriert und kann selbst entscheiden, wann es dieses Tool aufruft.
- `step3`: Der lokale Tool-Call wird in einen MCP-Call verpackt. Dazu gibt es einen mit Spring AI implementierten MCP-Server, der `git_log` als Tool bereitstellt. Das Modell darf diesen MCP-Server ueber den Client aufrufen.
- `main`: Enthält alles bis einschliesslich `step2`. Das MCP-Beispiel liegt bewusst nur auf `step3`.

Empfohlener Ablauf:

- Starte auf `initial` und baue die Loesung fuer `step1` selbst.
- In der README von `initial` gibt es dafuer bereits Hinweise.
- Wenn du festhaengst, kannst du jederzeit auf die Loesung in `step1` schauen.

## Beispiel-Repository

Parallel zum Uebungs-Repository solltest du ein Beispiel-Repository unter `../example.git` haben. Falls es noch fehlt:

```bash
git clone https://github.com/daily-stand-app/example example.git
```

## Konfiguration

Auf `initial` musst du zuerst die lokale Konfiguration aus der Beispiel-Datei erzeugen:

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Wichtige Werte in `application.properties`:

- `openai.api.key`: optional, falls du ein oeffentliches LLM ausprobieren willst
- `embedded.model.path`: Pfad zu deinem lokalen GGUF-Modell fuer SKaiNET
- `lmstudio.base.url`: URL fuer einen lokalen LM-Studio-Server
- `mcp.server.url`: erst fuer `step3` relevant

Je nach Branch wird nicht jeder Eintrag sofort verwendet, aber du kannst die Datei von Anfang an vollstaendig befuellen.

## Build

```bash
./mvnw compile
```

## Start auf der Kommandozeile

Zuerst die lokale Konfiguration anlegen:

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Danach in `src/main/resources/application.properties` mindestens den Modellpfad setzen:

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
