package io.github.lnyocly.ai4j.plugin.dynamicworkflow;

import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandRequest;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandSpec;
import io.github.lnyocly.ai4j.extension.prompt.ExtensionPromptResource;
import io.github.lnyocly.ai4j.extension.skill.ExtensionSkillResource;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;

/**
 * Sample extension for host-mediated dynamic workflow orchestration.
 */
public final class DynamicWorkflowExtension implements Ai4jExtension {

    public static final String EXTENSION_ID = "dynamic-workflow";
    public static final String TOOL_NAME = "workflow";
    public static final String COMMAND_NAME = "workflow";
    public static final String SKILL_NAME = "dynamic-workflow-orchestration";
    public static final String PROMPT_NAME = "dynamic-workflow-script";

    private static final String VERSION = "0.1.0";

    public ExtensionManifest manifest() {
        return ExtensionManifest.builder()
                .id(EXTENSION_ID)
                .name("Dynamic Workflow")
                .version(VERSION)
                .vendor("ai4j")
                .capability(ExtensionCapability.TOOL)
                .capability(ExtensionCapability.COMMAND)
                .capability(ExtensionCapability.SKILL)
                .capability(ExtensionCapability.PROMPT)
                .permission("agent.workflow.request")
                .configPrefix("ai4j.extensions.dynamic-workflow")
                .build();
    }

    public void apply(ExtensionContext context) {
        context.tools().register(toolSpec(), new ExtensionToolExecutor() {
            public String execute(ExtensionToolCall call) {
                String arguments = call == null ? null : call.getArguments();
                return DynamicWorkflowPayloads.toolRequest(arguments);
            }
        });

        context.commands().register(commandSpec(), request -> DynamicWorkflowPayloads.commandRequest(commandArguments(request)));

        context.skills().register(ExtensionSkillResource.builder()
                .name(SKILL_NAME)
                .description("Workflow for deciding when to request host-mediated dynamic workflow orchestration.")
                .resourcePath("skills/dynamic-workflow/SKILL.md")
                .build());

        context.prompts().register(ExtensionPromptResource.builder()
                .name(PROMPT_NAME)
                .description("Prompt guidance for producing deterministic dynamic workflow scripts.")
                .resourcePath("prompts/dynamic-workflow-script.md")
                .build());
    }

    public static ExtensionToolSpec toolSpec() {
        return ExtensionToolSpec.builder()
                .name(TOOL_NAME)
                .description("Request host-mediated execution of a deterministic JavaScript workflow that can fan out work to isolated subagents. The plugin returns a workflow request envelope; the host owns actual subagent execution, tools, approvals, and isolation.")
                .inputSchema("{\"type\":\"object\",\"properties\":{\"script\":{\"type\":\"string\",\"description\":\"Raw JavaScript workflow script. First statement should be: export const meta = { name, description, phases }.\"},\"args\":{\"description\":\"Optional JSON value exposed to the workflow script as args.\"},\"background\":{\"type\":\"boolean\",\"description\":\"Whether the host may run the workflow out of band.\"},\"maxAgents\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":1000,\"description\":\"Optional host-enforced maximum number of subagents.\"},\"tokenBudget\":{\"type\":\"integer\",\"minimum\":1,\"description\":\"Optional host-enforced token budget.\"}},\"required\":[\"script\"]}")
                .build();
    }

    public static ExtensionCommandSpec commandSpec() {
        return ExtensionCommandSpec.builder()
                .name(COMMAND_NAME)
                .description("Create a host-mediated dynamic workflow request from CLI command arguments.")
                .usage("/workflow <goal>")
                .build();
    }

    private static String commandArguments(ExtensionCommandRequest request) {
        return request == null ? null : request.getArguments();
    }
}

