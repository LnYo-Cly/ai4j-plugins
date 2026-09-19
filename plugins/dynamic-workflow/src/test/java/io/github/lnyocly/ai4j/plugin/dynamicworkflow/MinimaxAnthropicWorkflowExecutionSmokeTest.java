package io.github.lnyocly.ai4j.plugin.dynamicworkflow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
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
public class MinimaxAnthropicWorkflowExecutionSmokeTest {

    private static final String DYNAMIC_WORKFLOW_PACKAGE = "io.github.lnyocly.ai4j.agent.dynamicworkflow.";
    private static final String AGENT_PACKAGE = "io.github.lnyocly.ai4j.agent.";

    @Test
    public void minimaxGeneratedWorkflowScriptExecutesInAi4jRuntime() throws Exception {
        String apiKey = MinimaxLiveSmokeSupport.apiKey();
        Assume.assumeTrue("MINIMAX_API_KEY is required for live smoke", apiKey != null && !apiKey.trim().isEmpty());
        Assume.assumeTrue("ai4j-agent with dynamicworkflow host runtime is required for execution smoke",
                classAvailable("NashornDynamicWorkflowExecutor"));
        Assume.assumeTrue("ai4j-agent Agents builder is required for full live execution smoke",
                baseClassAvailable("Agents"));
        Assume.assumeTrue("ai4j-agent Agents builder must support anthropicMessages(apiKey, baseUrl)",
                agentBuilderSupportsAnthropicMessages());

        String prompt = "Return only raw JavaScript for an AI4J dynamic workflow runtime.\n"
                + "Requirements:\n"
                + "- First statement must be: export const meta = { name, description, phases }.\n"
                + "- Use a linear script, not callback style.\n"
                + "- Put phase('Plan') on its own line.\n"
                + "- Put phase('Run') on its own line.\n"
                + "- Use var, not let or const, except for the required export const meta statement.\n"
                + "- Make exactly one awaited agent call after phase('Run'): var result = await agent('LIVE_AGENT_TASK: summarize repository risk in one sentence', { label: 'planner' }).\n"
                + "- Last line must be: return result.\n"
                + "- Use only these globals: args, phase, log, agent.\n"
                + "- Do not pass functions or callbacks into phase().\n"
                + "- Do not import modules, do not call fs/path/fetch, do not use markdown, and do not wrap the answer in code fences.";

        MinimaxLiveSmokeSupport.HttpResult response = MinimaxLiveSmokeSupport.postAnthropicMessage(prompt);

        Assert.assertEquals("status=" + response.status + " body=" + response.body, 200, response.status);

        String script = stripCodeFence(extractAnthropicText(response.body)).trim();
        Assert.assertTrue("script=" + script, script.contains("export const meta"));
        Assert.assertTrue("script=" + script, script.contains("phase("));
        Assert.assertTrue("script=" + script, script.contains("agent("));
        Assert.assertFalse("script=" + script, script.contains("```"));

        ExtensionRegistry registry = ExtensionRegistry.of(new DynamicWorkflowExtension())
                .enable(DynamicWorkflowExtension.EXTENSION_ID)
                .exposeTool(DynamicWorkflowExtension.TOOL_NAME);
        ExtensionToolExecutor tool = registry.snapshot().getToolExecutors().get(DynamicWorkflowExtension.TOOL_NAME);

        JSONObject arguments = new JSONObject();
        arguments.put("script", script);
        arguments.put("maxAgents", Integer.valueOf(2));
        String envelope = tool.execute(new ExtensionToolCall(DynamicWorkflowExtension.TOOL_NAME, arguments.toJSONString()));

        Assert.assertTrue(envelope.contains("\"hostAction\":\"execute_dynamic_workflow\""));

        Object request = parseWorkflowRequest(envelope);
        Object result = executeWorkflowRequest(request, apiKey);
        String resultJson = (String) call(result, "toJson");
        List<?> agentCalls = (List<?>) call(result, "getAgentCalls");
        Object firstAgentCall = agentCalls.get(0);

        Assert.assertEquals(resultJson, "completed", call(result, "getStatus"));
        Assert.assertTrue(resultJson, ((String) call(result, "getOutput")).contains("FULL_LIVE_AGENT_OK"));
        Assert.assertEquals(resultJson, 1, agentCalls.size());
        Assert.assertEquals(resultJson, "planner", call(firstAgentCall, "getLabel"));
        Assert.assertTrue(resultJson, ((String) call(firstAgentCall, "getPrompt")).contains("LIVE_AGENT_TASK"));
    }

    private static boolean classAvailable(String simpleName) {
        try {
            Class.forName(DYNAMIC_WORKFLOW_PACKAGE + simpleName);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static boolean baseClassAvailable(String simpleName) {
        try {
            Class.forName(AGENT_PACKAGE + simpleName);
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
                            return runRealAi4jAgent(apiKey, String.valueOf(call(request, "getPrompt")));
                        }
                        if ("toString".equals(method.getName())) {
                            return "MinimaxAnthropicWorkflowExecutionSmokeTestFullLiveBridge";
                        }
                        return null;
                    }
                });
        Object executor = executorClass.getConstructor(bridgeClass).newInstance(bridge);
        return executorClass.getMethod("execute", requestClass).invoke(executor, request);
    }

    private static String runRealAi4jAgent(String apiKey, String prompt) throws Exception {
        Class<?> agentsClass = Class.forName(AGENT_PACKAGE + "Agents");
        Object builder = agentsClass.getMethod("builder").invoke(null);
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
                new Object[]{"You are the real AI4J agent inside a dynamic workflow. Reply with exactly one line starting with FULL_LIVE_AGENT_OK, then a concise summary of the task. Do not mention tests or mocks."});
        Object agent = builder.getClass().getMethod("build").invoke(builder);
        Object session = agent.getClass().getMethod("newSession").invoke(agent);
        Object result = session.getClass().getMethod("run", String.class).invoke(session, prompt);
        String output = (String) result.getClass().getMethod("getOutputText").invoke(result);
        Assert.assertNotNull("real AI4J Agent returned null output", output);
        Assert.assertTrue("real AI4J Agent output=" + output, output.contains("FULL_LIVE_AGENT_OK"));
        return output;
    }

    private static Object invokeBuilder(Object builder, String methodName, Class<?>[] parameterTypes, Object[] args) throws Exception {
        return builder.getClass().getMethod(methodName, parameterTypes).invoke(builder, args);
    }

    private static Object call(Object target, String methodName) throws Exception {
        return target.getClass().getMethod(methodName).invoke(target);
    }

    private static String extractAnthropicText(String body) {
        JSONObject json = JSON.parseObject(body);
        JSONArray content = json.getJSONArray("content");
        Assert.assertNotNull("missing content: " + body, content);
        for (int i = 0; i < content.size(); i++) {
            JSONObject item = content.getJSONObject(i);
            if ("text".equals(item.getString("type")) && item.getString("text") != null) {
                return item.getString("text");
            }
        }
        Assert.fail("missing text content: " + body);
        return "";
    }

    private static String stripCodeFence(String text) {
        String value = text == null ? "" : text.trim();
        if (!value.startsWith("```")) {
            return value;
        }
        int firstLineEnd = value.indexOf('\n');
        if (firstLineEnd < 0) {
            return value;
        }
        int lastFence = value.lastIndexOf("```");
        if (lastFence <= firstLineEnd) {
            return value.substring(firstLineEnd + 1).trim();
        }
        return value.substring(firstLineEnd + 1, lastFence).trim();
    }
}
