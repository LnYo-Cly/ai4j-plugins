# ai4j-plugins

[![Validate](https://github.com/LnYo-Cly/ai4j-plugins/actions/workflows/validate.yml/badge.svg)](https://github.com/LnYo-Cly/ai4j-plugins/actions/workflows/validate.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-1F6FEB)](https://www.apache.org/licenses/LICENSE-2.0.txt)
![JDK 8+](https://img.shields.io/badge/JDK-8%2B-2EA043)
[![Maven Central](https://img.shields.io/badge/Maven%20Central-io.github.lnyo--cly.community-2E86C1)](https://central.sonatype.com/search?q=io.github.lnyo-cly.community)

AI4J SDK 官方维护的社区插件索引：统一收录、统一审核、统一发布到 Maven Central。

[English README](README-EN.md)

AI4J 插件是普通的 Maven jar：通过 `ServiceLoader` 发现，由 `ExtensionRegistry` 的
enable / allow / expose 三段门禁控制，**引入依赖不等于启用**。插件面向**所有基于 AI4J
构建的 agent 宿主** —— `ai4j`、`ai4j-agent`、`ai4j-coding`、Spring Boot starter，以及开发者
自己的 agent 应用，不限于官方 CLI / TUI。本仓库由 AI4J 项目官方维护：插件经 review 收录、
CI 验证，以 `io.github.lnyo-cly.community` groupId 统一发布到 Maven Central —— 对用户是
可发现、可审计的插件入口，对作者则无需自建发布管线。

## 插件目录

| 插件 | Extension id | 能力 | 说明 | 最低 ai4j | 最新版 | Maintainer | 状态 |
|---|---|---|---|---|---|---|---|
| [you-search](plugins/you-search) | `you-search` | tool + command + skill + prompt | You.com 网络搜索 | 2.4.2 | 0.1.0 | [@mouse-value-add](https://github.com/mouse-value-add) | active |

> 「最低 ai4j」= 插件编译时依赖的 `ai4j-extension-api` 基线，规则见[版本与兼容性](#版本与兼容性)。

## 使用插件

### 1. 添加依赖

Maven：

```xml
<dependency>
  <groupId>io.github.lnyo-cly.community</groupId>
  <artifactId>ai4j-plugin-you-search</artifactId>
  <version>0.1.0</version>
</dependency>
```

Gradle：

```groovy
implementation 'io.github.lnyo-cly.community:ai4j-plugin-you-search:0.1.0'
```

### 2. 启用与暴露

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

### 同时使用多个插件

多个插件共存于同一个 registry —— `enable` 按插件、`exposeTool` 按工具，互不影响：

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("you-search")
        .exposeTool("you_web_search")
        .enable("weather")
        .exposeTool("weather_query")
        .enable("db-tools")
        .exposeTool("db_lookup");
```

未 `enable` 的插件即使在 classpath 上也不会加载；已启用但未 `expose` 的工具对模型不可见。

### Spring Boot

同样的门禁通过配置声明（`ai4j-spring-boot-starter`）：

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

### CLI 检查

引入插件前可以先审查：

```bash
ai4j-cli extension list
ai4j-cli extension inspect you-search --runtime
ai4j-cli extension validate you-search
ai4j-cli extension check you-search --enable --expose-tool you_web_search --strict
ai4j-cli extension run --enable you-search --allow-command you-search you-search "latest Java LTS"
```

完整门禁语义（含 `requireExplicitResourceActivation()` 与 Spring Boot / CLI 配置路径）见
[plugin packages 文档](https://github.com/LnYo-Cly/ai4j/tree/main/docs-site/docs/extending/plugins/plugin-packages.md)。

## 版本与兼容性

三个版本各自独立：**SDK 版本**、**extension-api 契约版本**、**插件版本**。插件 pom 固定
依赖一个已发布的 `ai4j-extension-api` 基线 —— 这个基线就是索引表里的「最低 ai4j」。

- **宿主版本优先。** 应用自身的 `ai4j` / `ai4j-extension-api` 版本在依赖解析时覆盖插件的
  编译期基线。宿主升级到 ai4j 2.4.3+ **无需插件重发** —— 扩展 SPI 是稳定契约。
- **老宿主是风险。** 宿主版本低于插件基线时，插件可能引用宿主没有的 SPI 面。添加插件前
  先核对索引表的「最低 ai4j」列。
- **API 演进承诺。** `ai4j-extension-api` 的 minor 版本只做增量扩展，不破坏既有契约，
  插件不必跟随 SDK 每个 minor 重发。若未来出现 breaking change，会在索引表与发版说明中
  显式标注受影响插件。
- **插件可以追新。** 某插件开始使用更新的 API 能力（例如未来 2.6 新增的 SPI）时，只上调
  它自己那一行的「最低 ai4j」；旧宿主用户可以改用该插件发布在 Central 上的旧版本。
- **基线变更可见。** 共享 parent 提升 `ai4j-extension-api.version` 时，全仓最低版本随之上移，
  在索引表中公示而非静默继承。
- 插件只依赖 `ai4j-extension-api`（SPI 契约），不依赖完整 `ai4j` 运行时 —— 可同时用于
  `ai4j`、`ai4j-agent`、`ai4j-coding` 与 Spring Boot starter。

## 插件归属

三层刻意分工，不是「官方插件在别处」：

| 归属 | 插件 | 定位 |
|---|---|---|
| 本仓 `io.github.lnyo-cly.community` | you-search 等社区插件 | 社区提交、统一审核、统一 Central 发版 |
| [ai4j](https://github.com/LnYo-Cly/ai4j) reactor | `ai4j-plugin-ask-user` | 官方样例，同时充当 extension-api 的契约一致性回归（同 reactor 编译即验证） |
| 独立仓库 | [`ai4j-plugin-dynamic-workflow`](https://github.com/LnYo-Cly/ai4j-plugin-dynamic-workflow) | 官方旗舰参考，独立 groupId / 版本 / 发版节奏 |

## 仓库结构

```text
pom.xml                  # ai4j-plugins-parent: shared depMgmt, Java 8, release profile
plugins/
  <extension-id>/        # one directory per plugin; directory name == extension id
    pom.xml              # artifactId ai4j-plugin-<extension-id>, independent version
    README.md            # coordinates, extension id, tools, env vars, maintainer
    src/...
```

每个插件**独立版本、独立发布** —— 发布一个插件不会 bump 其他插件。社区插件刻意
**不进入 `ai4j-bom`**，用户显式声明版本号。

## 提交插件

开 PR 添加 `plugins/<extension-id>/`，目录名必须等于 `manifest().getId()`，并在索引表加一行。

收录门槛（review + CI 强制执行）：

- `mvn -f plugins/<id>/pom.xml test` 离线通过 —— 不依赖真实 API key 或 endpoint
- `ExtensionValidator` 报告 `pass`（脚手架测试已覆盖；用
  `ai4j-cli extension init <artifact> --id <extension-id>` 在 `plugins/` 内生成骨架）
- `apply(...)` 不做网络、IO、读密钥或阻塞操作 —— 只做注册
- 宿主无关性：插件不得假设宿主有终端、UI 或交互面（禁用 `System.console`、GUI、直接阻塞式
  用户提问）。需要用户输入时走 tool/approval 机制，由宿主决定如何呈现 —— 插件要对
  CLI、TUI、Spring Boot 服务、无头 worker 同样可用
- 密钥只走环境变量或宿主配置，绝不硬编码；manifest 声明 `permissions`
- tool / command / skill / prompt 命名遵守
  [命名规则](https://github.com/LnYo-Cly/ai4j/tree/main/docs-site/docs/extending/plugins/plugin-packages.md)
- 运行时依赖默认只允许 `ai4j-extension-api` + JDK；引入第三方库（HTTP client、JSON 解析等）
  需在 PR 中说明理由
- 有具名 maintainer 承诺跟进上游 API 变更；maintainer 失联的插件会在索引中标记 `deprecated`
- 插件 README 需写明：坐标、extension id、工具/资源清单、所需环境变量、最低 ai4j 版本

要求 Java 8 兼容。共享 parent 通过 `ai4j-extension-api.version` 统一 pin 契约版本，
插件不得覆盖。

## 发布

按插件 tag 触发：

```text
plugins/<extension-id>/v<version>
```

tag 版本必须等于插件 pom 的 `<version>`（CI 校验，不一致即失败）。release workflow 只发布
该插件到 Maven Central；若共享 parent 的版本尚未在 Central，会一并发布。插件作者不接触
发布凭据 —— 由 maintainer 在合并后打 tag。

## License

Apache-2.0，与 AI4J SDK 一致。
