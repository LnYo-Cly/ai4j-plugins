package io.github.lnyocly.ai4j.plugin.dynamicworkflow;

import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandHandler;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandRequest;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(LiveProviderTest.class)
public class MinimaxAnthropicWorkflowSynthesisSmokeTest {

    @Test
    public void commandGoalCanBeSynthesizedIntoWorkflowScript() throws Exception {
        String apiKey = MinimaxLiveSmokeSupport.apiKey();
        Assume.assumeTrue("MINIMAX_API_KEY is required for live smoke", apiKey != null && !apiKey.trim().isEmpty());

        String goal = "Audit every controller for missing auth checks and summarize the highest-risk findings.";
        ExtensionRegistry registry = ExtensionRegistry.of(new DynamicWorkflowExtension()).enable("dynamic-workflow");
        ExtensionCommandHandler handler = registry.snapshot().getCommandHandlers().get("workflow");
        String envelope = handler.handle(new ExtensionCommandRequest("workflow", goal));

        Assert.assertTrue(envelope.contains("\"hostAction\":\"synthesize_dynamic_workflow\""));

        String prompt = "Write a short deterministic AI4J dynamic workflow script for this goal:\n"
                + goal + "\n\n"
                + "Return only raw JavaScript.\n"
                + "First statement must be export const meta = { name, description, phases }.\n"
                + "Use phase('Scan'), phase('Review'), and phase('Synthesize').\n"
                + "Do not import fs/path or add commentary.";

        MinimaxLiveSmokeSupport.HttpResult result = MinimaxLiveSmokeSupport.postAnthropicMessage(prompt);

        Assert.assertEquals("status=" + result.status + " body=" + result.body, 200, result.status);
        Assert.assertTrue("body=" + result.body, result.body.contains("export const meta"));
        Assert.assertTrue("body=" + result.body, result.body.contains("phase("));
    }
}
