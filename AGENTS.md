# AGENTS.md

This file gives coding agents repo-specific guidance for working in this repository.

## Repo Snapshot

- Repository folder: `BWUJavaScriptingFramework`
- Gradle root project name: `JBotWithUsV2`
- Java toolchain: `25`
- Build system: Gradle Kotlin DSL
- Platform bias: Windows-first because the runtime talks to the game through named pipes
- Main runtime entrypoint: `com.botwithus.bot.cli.gui.ImGuiApp`

## Project Structure

The repo is a modular Java application with four Gradle subprojects:

- `api` - Public scripting API, models, constants, query builders, wrappers, logging facade, script SPI
- `core` - Pipe/RPC transport, runtime, loader, lifecycle management, profiling, reconnect/retry support
- `cli` - ImGui desktop host, command system, log capture, script management, external runtime shell
- `example-script` - Example scripts that build into JARs and get copied into `scripts/`

Dependency direction:

```text
api <- core <- cli
api <- example-script
```

Important directories:

- `api/src/main/java` - Public API surface exposed to scripts
- `core/src/main/java` - Runtime, loader, RPC, pipe, implementations
- `cli/src/main/java` - Desktop host, GUI, commands, log plumbing
- `example-script/src/main/java` - Sample scripts and sample script UI usage
- `scripts/` - Discovered script JARs loaded by the runtime

## Working Rules

- Do not add or update tests unless the user explicitly asks for tests.
- Prefer updating production code, docs, or build logic without introducing new test coverage by default.
- Keep changes modular. New script-facing contracts belong in `api`; runtime behavior belongs in `core`; host/UI behavior belongs in `cli`.
- Preserve Java module boundaries. If reflection-based libraries need access, update `module-info.java` deliberately.
- Prefer the Gradle wrapper (`./gradlew` or `gradlew.bat`) over a system Gradle install.
- On Windows, the running CLI can lock built JARs. If a build fails on a locked artifact, stop the running app before retrying.

## Build And Verification

Use targeted Gradle tasks for the area you changed:

```bash
./gradlew :api:compileJava
./gradlew :core:compileJava
./gradlew :cli:compileJava
./gradlew :example-script:build
./gradlew :cli:run
```

Useful full builds:

```bash
./gradlew build
./gradlew clean build
```

Notes:

- `example-script:build` copies its output JAR into `scripts/`.
- `:cli:run` launches the desktop host from the repo root.
- Prefer compile/package verification over adding tests when validating normal changes.

## Repo-Specific Implementation Notes

- The project is fully modularized. Check `module-info.java` before adding dependencies or cross-module access.
- `core` uses `org.gradlex.extra-java-module-info` for non-modular dependencies like MessagePack.
- `cli` uses LWJGL and ImGui native dependencies and extracts natives before `run`.
- Logging is SLF4J-based with Logback as the runtime backend.
- Script discovery is handled from the `scripts/` directory via the runtime loader in `core`.

## Change Placement Guide

- Add new scripting interfaces, helpers, constants, and wrappers in `api`.
- Add loader/runtime/reconnect/profiling behavior in `core`.
- Add GUI panels, log presentation, command behavior, and host-side script UI handling in `cli`.
- Keep `example-script` as a reference consumer of the public API, not as the place for framework logic.
