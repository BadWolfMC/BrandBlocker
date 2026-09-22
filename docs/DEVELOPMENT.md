# Development workspace notes

## Required toolchain

Guardian/Cerberus targets Java 25 and uses the repository Gradle wrapper.

Use:

```powershell
.\gradlew.bat clean test :guardian-paper:jar :cerberus-fabric:build
```

Do not replace the wrapper with a system Gradle installation. The authoritative wrapper is:

- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`

The wrapper currently resolves Gradle 9.7.1.

## VS Code Java project import

The repository contains a Gradle multi-project build. `guardian-protocol` is a normal project dependency of both platform adapters; it should not be added manually as a referenced JAR.

If VS Code shows unresolved `com.badwolfmc.guardian.protocol.*` imports while Gradle builds successfully:

1. Confirm `java -version` is Java 25 and `JAVA_HOME` points to that JDK.
2. Open the **repository root** in VS Code, not an individual subproject.
3. Run **Java: Clean Java Language Server Workspace** and choose **Restart and delete**.
4. Run **Java: Import Java Projects into Workspace** (or **Java: Reload Projects**, depending on the installed extension version).
5. Run **Java: Update Project Configuration** if the Java language server still shows a stale classpath.

The checked-in `.vscode/settings.json` forces Standard mode, automatic Gradle project updates, and use of the repository wrapper. It intentionally does not hard-code a machine-specific JDK path.

If the language server still launches on the wrong JDK, set both `java.jdt.ls.java.home` and `java.import.gradle.java.home` in your **user** settings to the absolute path of your local JDK 25 installation.

## Local Gradle/Loom caches

`.gradle/` and every `build/` directory are local generated state and are ignored by Git. They are safe to delete when Gradle/VS Code is not actively using them.

The `.gradle/8.9`, `.gradle/9.2.0`, and `.gradle/9.7.1` folders are **not bundled Gradle distributions**. They are per-project caches from builds run with those Gradle versions. Loom also stores large mapped Minecraft artifacts under `.gradle/loom-cache/`.

To reclaim the space on Windows:

```powershell
.\gradlew.bat --stop
Remove-Item -Recurse -Force .gradle, build -ErrorAction SilentlyContinue
Get-ChildItem -Directory -Recurse -Filter build |
    Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
```

The next build will regenerate the caches it needs. Do **not** delete `gradle/wrapper/` unless intentionally regenerating the wrapper.
