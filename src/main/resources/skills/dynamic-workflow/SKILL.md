# Dynamic Workflow Orchestration

Use this Skill only when the user explicitly asks for a workflow, multi-agent fan-out, repository-wide audit, broad research, or another decomposable task that benefits from isolated subagents.

## When to call `workflow`

Call the `workflow` tool when all of these are true:

1. The task can be split into independent checks, perspectives, files, or research branches.
2. The final answer needs synthesis across those branches.
3. A normal single-agent turn would be slower, less complete, or context-heavy.

Do not call `workflow` for one quick file read, one small edit, or a task that can be answered directly.

## Tool contract

Pass one JSON object with a raw JavaScript `script` string. The script should start with:

```js
export const meta = {
  name: 'short_snake_case',
  description: 'What this workflow does',
  phases: [{ title: 'Scan' }, { title: 'Synthesize' }],
}
```

After the metadata, use host-provided globals such as `phase(title)`, `agent(prompt, opts)`, `parallel(thunks)`, `pipeline(items, ...stages)`, `log(message)`, `args`, and `budget` when the host supports them.

The plugin returns a host-mediated JSON envelope. It does not execute JavaScript, create worktrees, call providers, or bypass host approval. The host decides how to execute or reject the workflow request.

## Safety rules

- Do not include Markdown fences around the script.
- Keep metadata literal and deterministic.
- Avoid `Date`, randomness, imports, direct file system APIs, or network APIs inside the script.
- Put enough context in each `agent(...)` prompt; subagents should not depend on hidden parent context.
- Give each subagent a short unique label.
- Add a final synthesis step that checks null or failed branch results before reporting conclusions.
