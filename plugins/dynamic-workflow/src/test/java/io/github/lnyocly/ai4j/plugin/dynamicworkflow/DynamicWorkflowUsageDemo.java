package io.github.lnyocly.ai4j.plugin.dynamicworkflow;

import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;

/**
 * Minimal runnable usage demo: build a registry, expose workflow, call the tool,
 * and print the host-mediated envelope that an AI4J host would schedule/approve.
 */
public final class DynamicWorkflowUsageDemo {

    private DynamicWorkflowUsageDemo() {
    }

    public static void main(String[] args) throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.of(new DynamicWorkflowExtension())
                .enable("dynamic-workflow")
                .exposeTool("workflow");

        ExtensionToolExecutor workflow = registry.snapshot().getToolExecutors().get("workflow");
        String script = "export const meta = { name: 'demo', description: 'Demo workflow', phases: [{ title: 'Run' }] }\n"
                + "phase('Run')\n"
                + "return await agent('Say hello from an AI4J workflow demo.', { label: 'hello' })";

        String result = workflow.execute(new ExtensionToolCall("workflow", "{\"script\":\"" + escape(script) + "\"}"));
        System.out.println(result);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
