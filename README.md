# daily-standapp-skainet

Kleines Java-25-Maven-Projekt als Startpunkt fuer eine lokale Standup-Anwendung mit SKaiNET.

## Build

```bash
./mvnw compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
```

## Start auf der Kommandozeile

Zuerst die lokale Konfiguration anlegen:

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Dann die Anwendung starten:

```bash
java -Xms2g -Xmx16g --enable-preview --add-modules jdk.incubator.vector \
  -cp "target/classes:$(cat target/classpath.txt)" \
  daily.standapp.App
```

## Von `initial` nach `step1`

Ziel von `step1`:

- SKaiNET als Dependencies einbinden
- ein lokales GGUF-Modell direkt im Prozess laden
- die Git-Commits mit einer neuen Klasse `EmbeddedLLMSummary` zusammenfassen

Die vollstaendige Loesung liegt spaeter auf dem Branch `step1`. Die folgenden Snippets sollen den Weg dorthin deutlich verkuerzen.

### 1. `application.properties.example` erweitern

Ergaenze den Modellpfad:

```properties
embedded.model.path=path/to/llama-3.2-1b-instruct-q8_0.gguf
```

### 2. SKaiNET-Dependencies in `pom.xml`

Ergaenze zuerst die Property:

```xml
<skainet.transformers.version>0.21.1</skainet.transformers.version>
```

Dann diese Dependencies:

```xml
<dependency>
    <groupId>sk.ainet.transformers</groupId>
    <artifactId>skainet-transformers-runtime-kllama-jvm</artifactId>
    <version>${skainet.transformers.version}</version>
    <exclusions>
        <exclusion>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-stdlib-common</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>sk.ainet.transformers</groupId>
    <artifactId>skainet-transformers-agent-jvm</artifactId>
    <version>${skainet.transformers.version}</version>
    <exclusions>
        <exclusion>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-stdlib-common</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>sk.ainet.transformers</groupId>
    <artifactId>skainet-transformers-inference-llama-jvm</artifactId>
    <version>${skainet.transformers.version}</version>
    <exclusions>
        <exclusion>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-stdlib-common</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

Optional kannst du noch das lokale Maven-Repository ergaenzen:

```xml
<repositories>
    <repository>
        <id>maven-local-cache</id>
        <url>file://${user.home}/.m2/repository</url>
    </repository>
</repositories>
```

### 3. Neue Klasse `EmbeddedLLMSummary`

Lege `src/main/java/daily/standapp/summary/EmbeddedLLMSummary.java` an. Der Kern sieht so aus:

```java
try (KLlamaSession session = KLlamaJava.loadGGUF(Path.of(modelPath), systemPrompt)) {
    return session.generate(prompt, GenerationConfig.builder()
            .maxTokens(300)
            .temperature(0.2f)
            .build()).trim();
}
```

Fuer den Prompt kannst du dieselbe Commit-Liste verwenden wie in den anderen Summary-Klassen:

```java
builder.append("- Committer: ")
        .append(commit.committer())
        .append(", E-Mail: ")
        .append(commit.emailAddress())
        .append(", Message: ")
        .append(commit.commitMessage())
        .append(System.lineSeparator());
```

### 4. `App.java` verdrahten

Lade den Modellpfad aus `application.properties`:

```java
Properties properties = loadProperties();
String modelPath = requiredProperty(properties, "embedded.model.path");
```

Dann die neue Klasse aufrufen:

```java
System.out.println("=== Embedded LLM ===");
EmbeddedLLMSummary embeddedLLMSummary = new EmbeddedLLMSummary();
System.out.println(embeddedLLMSummary.summarize(modelPath, commits));
System.out.println();
```

Fuer das Laden der Properties reicht in `App.java` z. B.:

```java
try (InputStream inputStream = App.class.getClassLoader().getResourceAsStream("application.properties")) {
    Properties properties = new Properties();
    properties.load(inputStream);
    return properties;
}
```
