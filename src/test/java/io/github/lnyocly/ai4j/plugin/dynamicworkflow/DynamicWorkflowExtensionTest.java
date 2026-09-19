package io.github.lnyocly.ai4j.plugin.dynamicworkflow;

import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.ExtensionRuntimeSnapshot;
import io.github.lnyocly.ai4j.extension.ServiceLoaderExtensionLoader;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandHandler;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandRequest;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidationReport;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidator;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class DynamicWorkflowExtensionTest {

    @Test
    public void manifestDeclaresOfficialDynamicWorkflowCapabilities() {
        ExtensionManifest manifest = new DynamicWorkflowExtension().manifest();

        Assert.assertEquals("dynamic-workflow", manifest.getId());
        Assert.assertEquals("Dynamic Workflow", manifest.getName());
        Assert.assertEquals("0.1.0", manifest.getVersion());
        Assert.assertEquals("ai4j", manifest.getVendor());
        Assert.assertTrue(manifest.hasCapability(ExtensionCapability.TOOL));
        Assert.assertTrue(manifest.hasCapability(ExtensionCapability.COMMAND));
        Assert.assertTrue(manifest.hasCapability(ExtensionCapability.SKILL));
        Assert.assertTrue(manifest.hasCapability(ExtensionCapability.PROMPT));
        Assert.assertFalse(manifest.hasCapability(ExtensionCapability.GUARDRAIL));
        Assert.assertEquals("ai4j.extensions.dynamic-workflow", manifest.getConfigPrefix());
        Assert.assertTrue(manifest.getPermissions().contains("agent.workflow.request"));
    }

    @Test
    public void extensionContractIsValid() {
        ExtensionRegistry registry = ExtensionRegistry.of(new DynamicWorkflowExtension());

        ExtensionValidationReport report = ExtensionValidator.validate(registry, "dynamic-workflow");

        Assert.assertTrue(report.getIssues().toString(), report.isValid());
        Assert.assertEquals("pass", report.getStatus());
    }

    @Test
    public void toolReturnsHostMediatedWorkflowRequest() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.of(new DynamicWorkflowExtension())
                .enable("dynamic-workflow")
                .exposeTool("workflow");

        ExtensionRuntimeSnapshot snapshot = registry.snapshot();
        ExtensionToolExecutor executor = snapshot.getToolExecutors().get("workflow");

        String result = executor.execute(new ExtensionToolCall("workflow",
                "{\"script\":\"export const meta = { name: 'audit', description: 'Audit' }\\nreturn await agent('scan', { label: 'scan' })\",\"background\":true}"));

        Assert.assertTrue(result.contains("\"type\":\"ai4j.dynamic_workflow.request\""));
        Assert.assertTrue(result.contains("\"workflowSpecVersion\":\"ai4j.dynamic-workflow/v1\""));
        Assert.assertTrue(result.contains("\"source\":\"tool\""));
        Assert.assertTrue(result.contains("\"hostAction\":\"execute_dynamic_workflow\""));
        Assert.assertTrue(result.contains("\"status\":\"pending_host_workflow_execution\""));
        Assert.assertTrue(result.contains("\"scriptRuntime\":\"host_mediated\""));
        Assert.assertTrue(result.contains("argumentsRaw"));
        Assert.assertTrue(result.contains("export const meta"));
        Assert.assertTrue(result.contains("\\nreturn await agent"));
    }

    @Test
    public void commandReturnsWorkflowSynthesisRequest() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.of(new DynamicWorkflowExtension()).enable("dynamic-workflow");
        ExtensionRuntimeSnapshot snapshot = registry.snapshot();
        ExtensionCommandHandler handler = snapshot.getCommandHandlers().get("workflow");

        String result = handler.handle(new ExtensionCommandRequest("workflow", "Audit src for missing auth checks"));

        Assert.assertTrue(result.contains("\"source\":\"command\""));
        Assert.assertTrue(result.contains("\"workflowSpecVersion\":\"ai4j.dynamic-workflow/v1\""));
        Assert.assertTrue(result.contains("\"command\":\"workflow\""));
        Assert.assertTrue(result.contains("\"hostAction\":\"synthesize_dynamic_workflow\""));
        Assert.assertTrue(result.contains("\"goal\":\"Audit src for missing auth checks\""));
        Assert.assertTrue(result.contains("\"argumentsRaw\":\"Audit src for missing auth checks\""));
    }

    @Test
    public void toolEscapesMalformedArgumentsIntoStableEnvelope() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.of(new DynamicWorkflowExtension())
                .enable("dynamic-workflow")
                .exposeTool("workflow");
        ExtensionToolExecutor executor = registry.snapshot().getToolExecutors().get("workflow");

        String result = executor.execute(new ExtensionToolCall("workflow", "{bad\njson"));

        Assert.assertTrue(result.contains("\"argumentsRaw\":\"{bad\\njson\""));
        Assert.assertTrue(result.contains("\"status\":\"pending_host_workflow_execution\""));
    }

    @Test
    public void toolCapsOversizedArgumentsRaw() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.of(new DynamicWorkflowExtension())
                .enable("dynamic-workflow")
                .exposeTool("workflow");
        ExtensionToolExecutor executor = registry.snapshot().getToolExecutors().get("workflow");

        String result = executor.execute(new ExtensionToolCall("workflow", repeat('x', 100000)));

        Assert.assertTrue(result.contains("\"argumentsTruncated\":true"));
        Assert.assertTrue(result.length() < 70000);
    }

    @Test
    public void serviceLoaderDiscoversDynamicWorkflowExtension() {
        ServiceLoaderExtensionLoader loader = new ServiceLoaderExtensionLoader(DynamicWorkflowExtension.class.getClassLoader());

        List<Ai4jExtension> extensions = loader.load();

        boolean found = false;
        for (Ai4jExtension extension : extensions) {
            if ("dynamic-workflow".equals(extension.manifest().getId())) {
                found = true;
            }
        }
        Assert.assertTrue("dynamic-workflow extension should be discoverable by ServiceLoader", found);
    }

    @Test
    public void packagedSkillAndPromptResourcesArePresent() throws Exception {
        String skill = readResource("skills/dynamic-workflow/SKILL.md");
        String prompt = readResource("prompts/dynamic-workflow-script.md");

        Assert.assertTrue(skill.contains("Dynamic Workflow Orchestration"));
        Assert.assertTrue(skill.contains("The plugin returns a host-mediated JSON envelope."));
        Assert.assertTrue(prompt.contains("First statement must be `export const meta = ...`"));
        Assert.assertTrue(prompt.contains("Return a compact JSON-serializable final value"));
    }

    @Test
    public void usageDemoPrintsTheWorkflowEnvelope() throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream capture = new PrintStream(out, true, StandardCharsets.UTF_8.name());
        try {
            System.setOut(capture);
            DynamicWorkflowUsageDemo.main(new String[0]);
        } finally {
            System.setOut(originalOut);
            capture.close();
        }

        String result = new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
        Assert.assertTrue(result, result.contains("\"type\":\"ai4j.dynamic_workflow.request\""));
        Assert.assertTrue(result, result.contains("\"workflowSpecVersion\":\"ai4j.dynamic-workflow/v1\""));
        Assert.assertTrue(result, result.contains("\"hostAction\":\"execute_dynamic_workflow\""));
    }

    private static String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private static String readResource(String path) throws IOException {
        InputStream in = DynamicWorkflowExtension.class.getClassLoader().getResourceAsStream(path);
        Assert.assertNotNull("missing classpath resource: " + path, in);
        try {
            byte[] bytes = new byte[4096];
            StringBuilder builder = new StringBuilder();
            int read;
            while ((read = in.read(bytes)) != -1) {
                builder.append(new String(bytes, 0, read, StandardCharsets.UTF_8));
            }
            return builder.toString();
        } finally {
            in.close();
        }
    }
}

