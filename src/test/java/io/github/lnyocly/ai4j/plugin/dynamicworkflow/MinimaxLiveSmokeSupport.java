package io.github.lnyocly.ai4j.plugin.dynamicworkflow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class MinimaxLiveSmokeSupport {

    private MinimaxLiveSmokeSupport() {
    }

    static String apiKey() {
        return env("MINIMAX_API_KEY");
    }

    static String baseUrl() {
        return trimTrailingSlash(env("MINIMAX_BASE_URL", "https://api.minimaxi.com/anthropic"));
    }

    static String model() {
        return env("MINIMAX_MODEL", "MiniMax-M3");
    }

    static HttpResult postAnthropicMessage(String prompt) throws IOException {
        return post(baseUrl() + "/v1/messages", apiKey(), buildBody(model(), prompt));
    }

    private static HttpResult post(String url, String apiKey, String body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("x-api-key", apiKey);
        connection.setRequestProperty("Content-Type", "application/json");

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(bytes);
        }

        int status = connection.getResponseCode();
        String response = readBody(status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream());
        connection.disconnect();
        return new HttpResult(status, response == null ? "" : response);
    }

    private static String buildBody(String model, String prompt) {
        return "{"
                + "\"model\":\"" + escape(model) + "\","
                + "\"max_tokens\":1024,"
                + "\"temperature\":0,"
                + "\"messages\":[{"
                + "\"role\":\"user\","
                + "\"content\":[{"
                + "\"type\":\"text\","
                + "\"text\":\"" + escape(prompt) + "\""
                + "}]"
                + "}]"
                + "}";
    }

    private static String readBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private static String env(String key) {
        return System.getenv(key);
    }

    private static String env(String key, String fallback) {
        String value = env(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\':
                    builder.append("\\\\");
                    break;
                case '"':
                    builder.append("\\\"");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        String hex = Integer.toHexString(ch);
                        builder.append("\\u");
                        for (int pad = hex.length(); pad < 4; pad++) {
                            builder.append('0');
                        }
                        builder.append(hex);
                    } else {
                        builder.append(ch);
                    }
                    break;
            }
        }
        return builder.toString();
    }

    static final class HttpResult {
        final int status;
        final String body;

        private HttpResult(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
