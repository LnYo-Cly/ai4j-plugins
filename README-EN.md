# ai4j-plugins

[![Validate](https://github.com/LnYo-Cly/ai4j-plugins/actions/workflows/validate.yml/badge.svg)](https://github.com/LnYo-Cly/ai4j-plugins/actions/workflows/validate.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-1F6FEB)](https://www.apache.org/licenses/LICENSE-2.0.txt)
![JDK 8+](https://img.shields.io/badge/JDK-8%2B-2EA043)
[![Maven Central](https://img.shields.io/badge/Maven%20Central-io.github.lnyo--cly.community-2E86C1)](https://central.sonatype.com/search?q=io.github.lnyo-cly.community)

The official community plugin index for the [AI4J SDK](https://github.com/LnYo-Cly/ai4j) — curated submissions, unified Maven Central releases.

[中文 README](README.md)

AI4J plugins are ordinary Maven jars discovered through `ServiceLoader` and gated by the
`ExtensionRegistry` enable / allow / expose contract — **adding a dependency never enables
a plugin**. Plugins target **any agent host built on AI4J** — `ai4j`, `ai4j-agent`,
`ai4j-coding`, the Spring Boot starter, and custom agent applications alike — not just the
official CLI / TUI. This repository is maintained by the AI4J project: submissions are
reviewed and CI-verified, then released under `io.github.lnyo-cly.community` on Maven
Central — one discoverable, auditable entry point for users, and no self-managed release
pipeline for authors.

## Plugin index

| Plugin | Extension id | Capabilities | Description | Min ai4j | Latest | Maintainer | Status |
|---|---|---|---|---|---|---|---|
| [you-search](plugins/you-search) | `you-search` | tool + command + skill + prompt | You.com web search | 2.4.2 | 0.1.0 | [@mouse-value-add](https://github.com/mouse-value-add) | active |

> "Min ai4j" is the `ai4j-extension-api` baseline the plugin compiles against — see
> [Compatibility](#compatibility).

## Using a plugin

### 1. Add the dependency

Maven:

```xml
<dependency>
  <groupId>io.github.lnyo-cly.community</groupId>
  <artifactId>ai4j-plugin-you-search</artifactId>
  <version>0.1.0</version>
</dependency>
```

Gradle:

```groovy
implementation 'io.github.lnyo-cly.community:ai4j-plugin-you-search:0.1.0'
```

### 2. Enable and expose

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("you-search")
        .exposeTool("you_web_search");

Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("glm-4.5-flash")
        .extensions(registry)
        .build();
```

### Using multiple plugins together

Multiple plugins share one registry — `enable` is per-plugin, `exposeTool` is per-tool,
and they compose independently:

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("you-search")
        .exposeTool("you_web_search")
        .enable("weather")
        .exposeTool("weather_query")
        .enable("db-tools")
        .exposeTool("db_lookup");
```

A plugin on the classpath that is not `enable`d is never loaded; a tool that is not
`expose`d stays invisible to the model even when its plugin is enabled.

### Spring Boot

The same gates are declared via configuration (`ai4j-spring-boot-starter`):

```yaml
ai:
  extensions:
    enabled:
      - you-search
      - weather
    tools:
      expose:
        - you_web_search
        - weather_query
```

### CLI inspection

Audit a plugin before trusting it:

```bash
ai4j-cli extension list
ai4j-cli extension inspect you-search --runtime
ai4j-cli extension validate you-search
ai4j-cli extension check you-search --enable --expose-tool you_web_search --strict
ai4j-cli extension run --enable you-search --allow-command you-search you-search "latest Java LTS"
```

See the
[plugin packages doc](https://github.com/LnYo-Cly/ai4j/tree/main/docs-site/docs/extending/plugins/plugin-packages.md)
for the full gate semantics, including `requireExplicitResourceActivation()` and the
Spring Boot / CLI configuration paths.

## Compatibility

Three versions move independently: the **SDK version**, the **extension-api contract
version**, and each **plugin's version**. A plugin pom pins a released
`ai4j-extension-api` baseline — that baseline is the "Min ai4j" column in the index.

- **Host version wins.** Your application's own `ai4j` / `ai4j-extension-api` version
  overrides the plugin's compile-time baseline at resolution time. Running ai4j 2.4.3+
  needs **no plugin rebuild** — the extension SPI is the stable contract.
- **Older hosts are the risk.** If your app runs ai4j older than the plugin's baseline,
  the plugin may reference SPI surface the host doesn't have. Check the "Min ai4j"
  column before adding a plugin.
- **API evolution promise.** `ai4j-extension-api` minor releases are additive only —
  plugins don't need to re-release for every SDK minor. If a breaking change ever
  ships, affected plugins are called out in the index and release notes.
- **Plugins may move forward.** When a plugin starts using newer API capabilities (say,
  SPI added in a future 2.6), only its own "Min ai4j" cell rises; older hosts can keep
  using that plugin's earlier releases still published on Central.
- **Baseline bumps are deliberate.** When the shared parent raises
  `ai4j-extension-api.version`, every plugin's minimum moves — announced in the index
  table, never silently inherited.
- Plugins only depend on `ai4j-extension-api` (the SPI contract), not the full `ai4j`
  runtime — they stay usable from `ai4j`, `ai4j-agent`, `ai4j-coding`, and the Spring
  Boot starter alike.

## Where plugins live

A deliberate three-tier split, not "official plugins live elsewhere":

| Home | Plugin | Role |
|---|---|---|
| This repo, `io.github.lnyo-cly.community` | you-search and other community plugins | Community submissions, shared review, unified Central releases |
| [ai4j](https://github.com/LnYo-Cly/ai4j) reactor | `ai4j-plugin-ask-user` | Official sample, and the conformance regression for `ai4j-extension-api` (compiling in the same reactor validates the contract) |
| Standalone repo | [`ai4j-plugin-dynamic-workflow`](https://github.com/LnYo-Cly/ai4j-plugin-dynamic-workflow) | Official flagship reference, independent groupId / version / release cadence |

## Layout

```text
pom.xml                  # ai4j-plugins-parent: shared depMgmt, Java 8, release profile
plugins/
  <extension-id>/        # one directory per plugin; directory name == extension id
    pom.xml              # artifactId ai4j-plugin-<extension-id>, independent version
    README.md            # coordinates, extension id, tools, env vars, maintainer
    src/...
```

Every plugin has an **independent version** and an **independent release** — releasing one
plugin never bumps the others. Community plugins are intentionally **not** part of the
`ai4j-bom`; users pin versions explicitly.

## Submitting a plugin

Open a PR that adds `plugins/<extension-id>/`. The directory name must equal the extension
`manifest().getId()`, and the PR must add a row to the index table.

Acceptance bar (enforced in review + CI):

- `mvn -f plugins/<id>/pom.xml test` passes offline — no live API keys or endpoints required
- `ExtensionValidator` report is `pass` (the generated scaffold test already covers this;
  scaffold with `ai4j-cli extension init <artifact> --id <extension-id>` inside `plugins/`)
- `apply(...)` performs no network, IO, key reading, or blocking work — registration only
- Host-agnostic: plugins must not assume the host has a terminal, UI, or interaction surface
  (no `System.console`, GUI, or blocking user prompts). When user input is needed, go through
  the tool/approval mechanism and let the host decide how to render it — a plugin must work
  equally well in a CLI, a TUI, a Spring Boot service, or a headless worker
- Secrets come from env vars or host config, never hardcoded; manifest declares `permissions`
- Tool / command / skill / prompt names follow the
  [naming rules](https://github.com/LnYo-Cly/ai4j/tree/main/docs-site/docs/extending/plugins/plugin-packages.md)
- Runtime dependencies: `ai4j-extension-api` + JDK only by default. Third-party libraries
  (HTTP clients, JSON parsers, etc.) need explicit justification in the PR
- A named maintainer who commits to tracking upstream API changes. Plugins whose maintainer
  goes unresponsive may be marked `deprecated` in the index
- The plugin README documents: coordinates, extension id, tool/resource list, required
  env vars, and minimum ai4j version

Java 8 compatibility is required. The shared parent pins `ai4j-extension-api` via
`ai4j-extension-api.version` — plugins must not override it.

## Releases

Releases are tag-driven and per-plugin:

```text
plugins/<extension-id>/v<version>
```

The tag version must equal the plugin pom's `<version>` (CI fails otherwise). The release
workflow publishes that single plugin to Maven Central and also publishes the shared parent
if its version is not on Central yet. Plugin authors never touch release credentials — a
maintainer tags after merge.

## License

Apache-2.0, same as the AI4J SDK.
