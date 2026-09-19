package io.github.lnyocly.ai4j.plugin.dynamicworkflow;

import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

@Category(LiveProviderTest.class)
public class MinimaxAnthropicWorkflowE2BSandboxSmokeTest {

    private static final String DYNAMIC_WORKFLOW_PACKAGE = "io.github.lnyocly.ai4j.agent.dynamicworkflow.";
    private static final String AGENT_PACKAGE = "io.github.lnyocly.ai4j.agent.";
    private static final String SANDBOX_PACKAGE = "io.github.lnyocly.ai4j.agent.sandbox.";

    @Test
    public void workflowAgentCallUsesRealE2BSandboxAndRealAi4jAgent() throws Exception {
        String apiKey = MinimaxLiveSmokeSupport.apiKey();
        String e2bApiKey = System.getenv("E2B_API_KEY");
        Assume.assumeTrue("MINIMAX_API_KEY is required for live smoke", apiKey != null && !apiKey.trim().isEmpty());
        Assume.assumeTrue("E2B_API_KEY is required for sandbox smoke", e2bApiKey != null && !e2bApiKey.trim().isEmpty());
        Assume.assumeTrue("ai4j-agent with dynamicworkflow host runtime is required",
                classAvailable(DYNAMIC_WORKFLOW_PACKAGE + "NashornDynamicWorkflowExecutor"));
        Assume.assumeTrue("ai4j-agent Agents builder with anthropicMessages(apiKey, baseUrl) is required",
                agentBuilderSupportsAnthropicMessages());
        Assume.assumeTrue("ai4j-agent E2BSandboxProvider is required",
                classAvailable(SANDBOX_PACKAGE + "e2b.E2BSandboxProvider"));

        String script = "export const meta = { name: 'full-live-e2b-sandbox', description: 'Full live E2B sandbox workflow', phases: [{ title: 'Sandbox' }, { title: 'Agent' }] }\n"
                + "phase('Sandbox')\n"
                + "phase('Agent')\n"
                + "var result = await agent('Use the live sandbox marker to answer: E2B_FULL_LIVE_SANDBOX_OK', { label: 'sandbox-agent' })\n"
                + "return result";

        ExtensionRegistry registry = ExtensionRegistry.of(new DynamicWorkflowExtension())
                .enable(DynamicWorkflowExtension.EXTENSION_ID)
                .exposeTool(DynamicWorkflowExtension.TOOL_NAME);
        ExtensionToolExecutor tool = registry.snapshot().getToolExecutors().get(DynamicWorkflowExtension.TOOL_NAME);

        JSONObject arguments = new JSONObject();
        arguments.put("script", script);
        arguments.put("maxAgents", Integer.valueOf(1));
        String envelope = tool.execute(new ExtensionToolCall(DynamicWorkflowExtension.TOOL_NAME, arguments.toJSONString()));

        Object request = parseWorkflowRequest(envelope);
        Object result = executeWorkflowRequest(request, apiKey);
        String resultJson = (String) call(result, "toJson");
        List<?> agentCalls = (List<?>) call(result, "getAgentCalls");

        Assert.assertEquals(resultJson, "completed", call(result, "getStatus"));
        Assert.assertEquals(resultJson, 1, agentCalls.size());
        Assert.assertTrue(resultJson, ((String) call(result, "getOutput")).contains("FULL_LIVE_AGENT_OK"));
        Assert.assertTrue(resultJson, ((String) call(result, "getOutput")).contains("E2B_FULL_LIVE_SANDBOX_OK"));
    }

    private static Object parseWorkflowRequest(String envelope) throws Exception {
        Class<?> parserClass = Class.forName(DYNAMIC_WORKFLOW_PACKAGE + "DynamicWorkflowRequestParser");
        return parserClass.getMethod("parse", String.class).invoke(null, envelope);
    }

    private static Object executeWorkflowRequest(Object request, final String apiKey) throws Exception {
        Class<?> requestClass = Class.forName(DYNAMIC_WORKFLOW_PACKAGE + "DynamicWorkflowRequest");
        Class<?> bridgeClass = Class.forName(DYNAMIC_WORKFLOW_PACKAGE + "DynamicWorkflowAgentBridge");
        Class<?> executorClass = Class.forName(DYNAMIC_WORKFLOW_PACKAGE + "NashornDynamicWorkflowExecutor");
        Object bridge = Proxy.newProxyInstance(
                bridgeClass.getClassLoader(),
                new Class<?>[]{bridgeClass},
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("runAgent".equals(method.getName())) {
                            Object request = args[0];
                            String sandboxStdout = runRealE2BSandboxCommand();
                            return runRealAi4jAgent(apiKey, String.valueOf(call(request, "getPrompt")), sandboxStdout);
                        }
                        if ("toString".equals(method.getName())) {
                            return "MinimaxAnthropicWorkflowE2BSandboxSmokeTestBridge";
                        }
                        return null;
                    }
                });
        Object executor = executorClass.getConstructor(bridgeClass).newInstance(bridge);
        return executorClass.getMethod("execute", requestClass).invoke(executor, request);
    }

    private static String runRealE2BSandboxCommand() throws Exception {
        Object specBuilder = Class.forName(SANDBOX_PACKAGE + "SandboxSpec").getMethod("builder").invoke(null);
        specBuilder = invokeBuilder(specBuilder, "providerId",
                new Class<?>[]{String.class},
                new Object[]{"e2b"});
        specBuilder = invokeBuilder(specBuilder, "config",
                new Class<?>[]{String.class, Object.class},
                new Object[]{"templateID", "base"});
        specBuilder = invokeBuilder(specBuilder, "config",
                new Class<?>[]{String.class, Object.class},
                new Object[]{"timeoutSeconds", Integer.valueOf(300)});
        Object spec = specBuilder.getClass().getMethod("build").invoke(specBuilder);

        Object provider = Class.forName(SANDBOX_PACKAGE + "e2b.E2BSandboxProvider").getConstructor().newInstance();
        Object session = null;
        try {
            session = provider.getClass()
                    .getMethod("createSession", Class.forName(SANDBOX_PACKAGE + "SandboxSpec"))
                    .invoke(provider, spec);
            Object commandBuilder = Class.forName(SANDBOX_PACKAGE + "SandboxCommand").getMethod("builder").invoke(null);
            commandBuilder = invokeBuilder(commandBuilder, "commandId",
                    new Class<?>[]{String.class},
                    new Object[]{"plugin-full-live-e2b-sandbox"});
            commandBuilder = invokeBuilder(commandBuilder, "command",
                    new Class<?>[]{String.class},
                    new Object[]{"printf E2B_FULL_LIVE_SANDBOX_OK"});
            commandBuilder = invokeBuilder(commandBuilder, "timeoutMillis",
                    new Class<?>[]{Long.class},
                    new Object[]{Long.valueOf(30000L)});
            Object command = commandBuilder.getClass().getMethod("build").invoke(commandBuilder);
            Object sandboxResult = session.getClass()
                    .getMethod("execute", Class.forName(SANDBOX_PACKAGE + "SandboxCommand"))
                    .invoke(session, command);
            Assert.assertEquals(Integer.valueOf(0), call(sandboxResult, "getExitCode"));
            String stdout = (String) call(sandboxResult, "getStdout");
            Assert.assertTrue("sandbox stdout=" + stdout, stdout != null && stdout.contains("E2B_FULL_LIVE_SANDBOX_OK"));
            return stdout;
        } finally {
            if (session != null) {
                session.getClass().getMethod("close").invoke(session);
            }
        }
    }

    private static String runRealAi4jAgent(String apiKey, String prompt, String sandboxStdout) throws Exception {
        Object builder = Class.forName(AGENT_PACKAGE + "Agents").getMethod("builder").invoke(null);
        builder = invokeBuilder(builder, "anthropicMessages",
                new Class<?>[]{String.class, String.class},
                new Object[]{apiKey, MinimaxLiveSmokeSupport.baseUrl()});
        builder = invokeBuilder(builder, "model",
                new Class<?>[]{String.class},
                new Object[]{MinimaxLiveSmokeSupport.model()});
        builder = invokeBuilder(builder, "temperature",
                new Class<?>[]{Double.class},
                new Object[]{Double.valueOf(0)});
        builder = invokeBuilder(builder, "maxOutputTokens",
                new Class<?>[]{Integer.class},
                new Object[]{Integer.valueOf(128)});
        builder = invokeBuilder(builder, "instructions",
                new Class<?>[]{String.class},
                new Object[]{"You are the real AI4J agent inside a dynamic workflow. Reply with exactly one line containing FULL_LIVE_AGENT_OK and E2B_FULL_LIVE_SANDBOX_OK."});
        Object agent = builder.getClass().getMethod("build").invoke(builder);
        Object session = agent.getClass().getMethod("newSession").invoke(agent);
        String expectedLine = "FULL_LIVE_AGENT_OK " + sandboxStdout.trim();
        Object result = session.getClass().getMethod("run", String.class)
                .invoke(session, prompt + "\nLive E2B sandbox stdout: " + sandboxStdout
                        + "\nCopy this exact line and nothing else:\n" + expectedLine);
        String output = (String) result.getClass().getMethod("getOutputText").invoke(result);
        Assert.assertTrue("real AI4J Agent output=" + output, output != null && output.contains("FULL_LIVE_AGENT_OK"));
        Assert.assertTrue("real AI4J Agent output=" + output, output.contains("E2B_FULL_LIVE_SANDBOX_OK"));
        return output;
    }

    private static boolean classAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static boolean agentBuilderSupportsAnthropicMessages() {
        try {
            Object builder = Class.forName(AGENT_PACKAGE + "Agents").getMethod("builder").invoke(null);
            builder.getClass().getMethod("anthropicMessages", String.class, String.class);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Object invokeBuilder(Object builder, String methodName, Class<?>[] parameterTypes, Object[] args) throws Exception {
        return builder.getClass().getMethod(methodName, parameterTypes).invoke(builder, args);
    }

    private static Object call(Object target, String methodName) throws Exception {
        return target.getClass().getMethod(methodName).invoke(target);
    }
}
