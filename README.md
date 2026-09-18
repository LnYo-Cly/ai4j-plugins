# ai4j-plugins

Community-maintained extension plugins for the [AI4J SDK](https://github.com/LnYo-Cly/ai4j).

AI4J plugins are ordinary Maven jars discovered through `ServiceLoader` and gated by the
`ExtensionRegistry` enable / allow / expose contract. This repository is the shared home for
plugins whose authors prefer not to run their own Maven Central publishing — contributions are
reviewed here, built in CI, and released under the `io.github.lnyo-cly.community` groupId.

Official sample plugins live elsewhere:

- `ai4j-plugin-ask-user` — inside the [ai4j](https://github.com/LnYo-Cly/ai4j) monorepo reactor
- `ai4j-plugin-dynamic-workflow` — standalone repo [LnYo-Cly/ai4j-plugin-dynamic-workflow](https://github.com/LnYo-Cly/ai4j-plugin-dynamic-workflow)

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

## Plugin index

| Plugin | Extension id | Capabilities | Description | Maintainer | Latest | Status |
|---|---|---|---|---|---|---|
| [you-search](plugins/you-search) | `you-search` | tool + command + skill + prompt | You.com web search tool for agents | [@mouse-value-add](https://github.com/mouse-value-add) | 0.1.0 | active |

## Using a plugin

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

Java — discover, enable, expose:

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

Spring Boot (`ai4j-spring-boot-starter`) — same gates via configuration:

```yaml
ai:
  extensions:
    enabled:
      - you-search
    tools:
      expose:
        - you_web_search
```

CLI — inspect a plugin before trusting it:

```bash
ai4j-cli extension list
ai4j-cli extension inspect you-search --runtime
ai4j-cli extension validate you-search
ai4j-cli extension check you-search --enable --expose-tool you_web_search --strict
ai4j-cli extension run --enable you-search --allow-command you-search you-search "latest Java LTS"
```

Nothing is enabled by classpath presence alone — the host must explicitly `enable(...)`
and `exposeTool(...)`. See the
[plugin packages doc](https://github.com/LnYo-Cly/ai4j/tree/main/docs-site/docs/extending/plugins/plugin-packages.md)
for the full gate semantics, including `requireExplicitResourceActivation()` and the
Spring Boot / CLI configuration paths.

## Compatibility

Plugins in this repo compile against a **released** `ai4j-extension-api` baseline (currently
`2.4.2`, pinned once in the shared parent). That baseline is the plugin's **minimum ai4j
version**:

| Plugin | Minimum ai4j version |
|---|---|
| `ai4j-plugin-you-search` 0.1.0 | ai4j ≥ 2.4.2 |

Rules that make this safe:

- **Host version wins.** Your application's own `ai4j` / `ai4j-extension-api` version
  overrides the plugin's compile-time baseline at resolution time. Running ai4j 2.4.3+
  needs **no plugin rebuild** — the extension SPI is the stable contract.
- **Older hosts are the risk.** If your app runs ai4j older than the plugin's baseline,
  the plugin may reference SPI surface the host doesn't have. Check the minimum-version
  column before adding a plugin.
- **Baseline bumps are deliberate.** When the shared parent raises
  `ai4j-extension-api.version`, every plugin's minimum version moves — that's announced in
  the index table, not silently inherited by users.
- Plugins only depend on `ai4j-extension-api` (the SPI contract), not the full `ai4j`
  runtime — they stay usable from `ai4j`, `ai4j-agent`, `ai4j-coding`, and the Spring Boot
  starter alike.

## Submitting a plugin

Open a PR that adds `plugins/<extension-id>/` with the layout above. The directory name must
equal the extension `manifest().getId()`, and the PR must add a row to the index table.

Acceptance bar (enforced in review + CI):

- `mvn -f plugins/<id>/pom.xml test` passes offline — no live API keys or endpoints required
- `ExtensionValidator` report is `pass` (the generated scaffold test already covers this;
  scaffold with `ai4j-cli extension init <artifact> --id <extension-id>` inside `plugins/`)
- `apply(...)` performs no network, IO, key reading, or blocking work — registration only
- Secrets come from env vars or host config, never hardcoded; manifest declares `permissions`
- Tool / command / skill / prompt names follow the
  [naming rules](https://github.com/LnYo-Cly/ai4j/tree/main/docs-site/docs/extending/plugins/plugin-packages.md)
- Runtime dependencies: `ai4j-extension-api` + JDK only by default. Third-party libraries
  (HTTP clients, JSON parsers, etc.) need explicit justification in the PR
- A named maintainer who commits to tracking upstream API changes. Plugins whose maintainer
  goes unresponsive may be marked `deprecated` in the index

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

The parent (`ai4j-plugins-parent`) is published independently when it changes; plugin poms
reference it by `<parent><version>`.

## License

Apache-2.0, same as the AI4J SDK.
