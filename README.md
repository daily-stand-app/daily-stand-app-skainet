# daily-standapp-skainet

Kleines Java-25-Maven-Projekt als Startpunkt fuer eine lokale Standup-Anwendung mit SKaiNET.

## Build

```bash
./mvnw compile
```

## Start auf der Kommandozeile

Zuerst die lokale Konfiguration anlegen:

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Dann die Anwendung starten:

```bash
java -cp target/classes:$HOME/.m2/repository/org/eclipse/jgit/org.eclipse.jgit/7.6.0.202603022253-r/org.eclipse.jgit-7.6.0.202603022253-r.jar:$HOME/.m2/repository/com/googlecode/javaewah/JavaEWAH/1.2.3/JavaEWAH-1.2.3.jar:$HOME/.m2/repository/commons-codec/commons-codec/1.21.0/commons-codec-1.21.0.jar:$HOME/.m2/repository/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar daily.standapp.App
```
