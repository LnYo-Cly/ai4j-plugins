package io.github.lnyocly.ai4j.plugin.dynamicworkflow;

import io.github.lnyocly.ai4j.test.LiveProviderTest;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(LiveProviderTest.class)
public class MinimaxAnthropicSmokeTest {

    @Test
    public void anthropicCompatibleBaseResponds() throws Exception {
        String apiKey = MinimaxLiveSmokeSupport.apiKey();
        Assume.assumeTrue("MINIMAX_API_KEY is required for live smoke", apiKey != null && !apiKey.trim().isEmpty());

        String prompt = "Reply with exactly: MINIMAX_M3_OK";

        MinimaxLiveSmokeSupport.HttpResult result = MinimaxLiveSmokeSupport.postAnthropicMessage(prompt);

        Assert.assertEquals("status=" + result.status + " body=" + result.body, 200, result.status);
        Assert.assertTrue("body=" + result.body, result.body.contains("MINIMAX_M3_OK"));
    }
}
