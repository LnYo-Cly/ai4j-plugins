# ai4j-plugin-dynamic-workflow

[![Java Regression](https://github.com/LnYo-Cly/ai4j-plugin-dynamic-workflow/actions/workflows/java-regression.yml/badge.svg)](https://github.com/LnYo-Cly/ai4j-plugin-dynamic-workflow/actions/workflows/java-regression.yml)
[![Java 8](https://img.shields.io/badge/Java-8%2B-blue.svg)](pom.xml)
[![AI4J](https://img.shields.io/badge/AI4J-extension--api-7c3aed.svg)](https://github.com/LnYo-Cly/ai4j)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

> **Claude Code-style dynamic workflows for AI4J.**
> Turn one prompt into a host-approved workflow script that can fan out across subagents, phases, and parallel checks — while the Java plugin stays small, auditable, and safe.

`ai4j-plugin-dynamic-workflow` adds a `workflow` tool, a `/workflow` command, a Skill, and a Prompt to AI4J's extension ecosystem. The plugin does **not** execute JavaScript, spawn agents, touch git worktrees, or call providers. It emits a stable JSON envelope so the AI4J host can parse, approve, schedule, cancel, persist, or reject the request.

Built for:

- repository-wide audits
- multi-perspective review
- large refactor planning
- fan-out research and synthesis
- adversarial checks where independent branches should cross-check each other

## Install

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-plugin-dynamic-workflow</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The plugin depends on `io.github.lnyo-cly:ai4j-extension-api:2.4.0`.
Until that API artifact is available from your configured Maven repository, install it from an AI4J SDK checkout first:

```bash
git clone https://github.com/LnYo-Cly/ai4j ../ai4j-sdk
mvn -f ../ai4j-sdk/pom.xml -Droot.publish.skip=false -pl ai4j-extension-api -am -DskipTests install
```

## Enable

Basic Java usage:

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("dynamic-workflow")
        .exposeTool("workflow");
```

Strict resource activation:

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("dynamic-workflow")
        .requireExplicitResourceActivation()
        .allowCommand("workflow")
        .allowSkill("dynamic-workflow-orchestration")
        .allowPrompt("dynamic-workflow-script")
        .exposeTool("workflow");
```

## Try it

Ask the AI4J agent for a workflow in plain language:

```text
Run a workflow to audit every controller for missing auth checks and summarize the highest-risk findings.
```

The model should produce a deterministic workflow script and call the `workflow` tool with JSON like this:

```json
{
  "script": "export const meta = { name: 'auth_audit', description: 'Audit controllers for missing auth checks', phases: [{ title: 'Scan' }, { title: 'Review' }, { title: 'Synthesize' }] }\n\nphase('Scan')\nconst files = await agent('List controller files that handle authenticated routes.', { label: 'controller inventory' })\n\nphase('Review')\nconst findings = await parallel(files.split('\\n').filter(Boolean).map(file => () => agent('Audit ' + file + ' for missing auth checks. Return concise evidence.', { label: 'audit ' + file })))\n\nphase('Synthesize')\nreturn await agent('Deduplicate and rank these findings:\\n' + findings.join('\\n\\n'), { label: 'final synthesis' })",
  "background": true,
  "maxAgents": 16,
  "tokenBudget": 50000
}
```

The plugin returns a host-mediated request envelope. The host decides whether and how to execute it.

## Workflow script shape

A workflow is plain JavaScript. The first statement should export literal metadata:

```js
export const meta = {
  name: 'inspect_project',
  description: 'Inspect a repository and summarize the main modules',
  phases: [
    { title: 'Scan' },
    { title: 'Analyze' },
  ],
}

phase('Scan')
const inventory = await agent('Inspect the repository structure.', {
  label: 'repo inventory',
})

phase('Analyze')
return await agent('Summarize the main modules from this inventory:\n' + inventory, {
  label: 'module summary',
})
```

The script is a **contract for the host runtime**. AI4J may provide these globals when it implements workflow execution:

| Global | Purpose |
| --- | --- |
| `agent(prompt, opts)` | Run an isolated subagent and return its final result. |
| `parallel(thunks)` | Run `() => agent(...)` thunks concurrently and return results in input order. |
| `pipeline(items, ...stages)` | Fan items through ordered stages. |
| `phase(title)` | Mark the current phase for progress grouping. |
| `log(message)` | Emit a workflow-level progress note. |
| `args` | Optional JSON value passed through the tool call. |
| `budget` | Host-owned budget tracker, if configured. |

## Tool input

`workflow` accepts one JSON object:

| Field | Required | Meaning |
| --- | --- | --- |
| `script` | yes | Raw workflow JavaScript. First statement should be `export const meta = ...`. |
| `args` | no | JSON value the host may expose to the script as `args`. |
| `background` | no | Whether the host may run this out of band. |
| `maxAgents` | no | Host-enforced maximum number of subagents. |
| `tokenBudget` | no | Host-enforced token budget. |

## Tool output

The tool returns valid JSON even when the model passes malformed arguments:

```json
{
  "type": "ai4j.dynamic_workflow.request",
  "workflowSpecVersion": "ai4j.dynamic-workflow/v1",
  "source": "tool",
  "tool": "workflow",
  "status": "pending_host_workflow_execution",
  "hostAction": "execute_dynamic_workflow",
  "scriptRuntime": "host_mediated",
  "blocking": "host_decides",
  "argumentsRaw": "{... original tool arguments ...}"
}
```

`argumentsRaw` is capped at 64 KiB. When capped, the envelope adds:

```json
{ "argumentsTruncated": true }
```

The `/workflow <goal>` command returns the same request type with `source: "command"` and `hostAction: "synthesize_dynamic_workflow"`, so the host can ask an agent to synthesize a script from the goal.

## Determinism rules

Keep workflow scripts reproducible and easy to review:

- start with literal `export const meta = { name, description, phases }`
- do not use `Date`, randomness, `import`, `require`, direct file system APIs, or network APIs
- pass thunks to `parallel`, not already-running promises
- give every `agent()` call a short unique `label`
- include enough context in each subagent prompt; subagents should not depend on hidden parent context
- finish with a synthesis step that handles empty or failed branch results

## Safety model

This repository is a Java plugin package, not a workflow runner.

```text
AI4J model
  -> writes workflow script
  -> calls workflow tool
  -> plugin returns JSON request envelope
  -> AI4J host validates policy + approvals
  -> host runtime may execute, queue, persist, cancel, or reject
```

That split keeps the extension safe by default: plugin installation cannot grant JavaScript execution, provider access, shell access, worktree isolation, or background scheduling by itself.

## What is included

| Path | Purpose |
| --- | --- |
| `src/main/java/.../DynamicWorkflowExtension.java` | AI4J extension entrypoint and resource registration. |
| `src/main/java/.../DynamicWorkflowPayloads.java` | Stable JSON envelope construction and 64 KiB argument cap. |
| `src/main/resources/skills/dynamic-workflow/SKILL.md` | Agent guidance for deciding when to request a workflow. |
| `src/main/resources/prompts/dynamic-workflow-script.md` | Prompt guidance for deterministic workflow script generation. |
| `examples/repository-audit.workflow.js` | Copyable workflow script example for host/runtime experiments. |
| `src/test/java/.../DynamicWorkflowUsageDemo.java` | Complete Java envelope demo compiled by `mvn test`. |
| `.github/workflows/java-regression.yml` | Java 8 Maven regression gate. |

## Current boundary

Implemented now:

- AI4J `workflow` tool
- AI4J `/workflow` command
- Skill + Prompt classpath resources
- ServiceLoader discovery
- deterministic request envelope
- oversized argument truncation
- Java 8 tests

Left to AI4J host/runtime layers:

- JavaScript sandbox and parser
- subagent sessions
- progress rendering
- cancellation
- background run manager
- resume journal
- model routing
- git worktree isolation
- saved workflows

## Complete Java envelope demo

`examples/repository-audit.workflow.js` is a workflow-script example. The complete Java usage demo is `src/test/java/.../DynamicWorkflowUsageDemo.java`: it wires the extension registry, exposes `workflow`, sends a script, and prints the JSON envelope.

Run the normal test command to compile the demo:

```bash
mvn -DskipTests=false test
```

Expected envelope fields include `type: ai4j.dynamic_workflow.request`, `workflowSpecVersion: ai4j.dynamic-workflow/v1`, and `hostAction: execute_dynamic_workflow`.
## Validate

```bash
mvn -DskipTests=false test
```

Expected result:

```text
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Optional live MiniMax smoke

Set `MINIMAX_API_KEY`, then run:

```powershell
$env:MINIMAX_API_KEY='...'
$env:MINIMAX_BASE_URL='https://api.minimaxi.com/anthropic'
$env:MINIMAX_MODEL='MiniMax-M3'
mvn "-DskipTests=false" "-Dtest=MinimaxAnthropicSmokeTest" "-Dtest.excludedGroups=" test
```

Optional overrides:

- `MINIMAX_BASE_URL` (default: `https://api.minimaxi.com/anthropic`)
- `MINIMAX_MODEL` (default: `MiniMax-M3`)

### Optional live MiniMax synthesis smoke

This checks the host-side synthesis step that turns a workflow goal into a deterministic workflow script:

```powershell
$env:MINIMAX_API_KEY='...'
$env:MINIMAX_BASE_URL='https://api.minimaxi.com/anthropic'
$env:MINIMAX_MODEL='MiniMax-M3'
mvn "-DskipTests=false" "-Dtest=MinimaxAnthropicWorkflowSynthesisSmokeTest" "-Dtest.excludedGroups=" test
```

### Optional live MiniMax execution smoke

This is the full live closed-loop smoke: MiniMax M3 produces raw workflow JavaScript, the plugin wraps it in a host-mediated `workflow` envelope, the AI4J agent runtime parses and executes it, and each workflow `agent(...)` call invokes a real AI4J `Agent` backed by the MiniMax Anthropic-compatible endpoint.

```powershell
$env:MINIMAX_API_KEY='...'
$env:MINIMAX_BASE_URL='https://api.minimaxi.com/anthropic'
$env:MINIMAX_MODEL='MiniMax-M3'
mvn -Plive-ai4j-agent-tests "-DskipTests=false" "-Dtest=MinimaxAnthropicWorkflowExecutionSmokeTest" "-Dtest.excludedGroups=" test
```

To run the normal plugin tests plus all live MiniMax smokes:

```powershell
mvn -Plive-ai4j-agent-tests "-Dtest.excludedGroups=" -DskipTests=false test
```

### Optional full live MiniMax + E2B sandbox smoke

This smoke keeps the same dynamic-workflow path and additionally opens a real E2B sandbox from `E2B_API_KEY`, executes a command inside it, then passes the sandbox stdout into a real AI4J `Agent` backed by MiniMax M3.

```powershell
$env:MINIMAX_API_KEY='...'
$env:MINIMAX_BASE_URL='https://api.minimaxi.com/anthropic'
$env:MINIMAX_MODEL='MiniMax-M3'
$env:E2B_API_KEY='...'
mvn -Plive-ai4j-agent-tests "-DskipTests=false" "-Dtest=MinimaxAnthropicWorkflowE2BSandboxSmokeTest" "-Dtest.excludedGroups=" test
```

## License

Apache-2.0 — see [LICENSE](LICENSE).
