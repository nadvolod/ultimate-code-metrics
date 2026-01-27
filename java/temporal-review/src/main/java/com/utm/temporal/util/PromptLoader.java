package com.utm.temporal.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for loading agent prompts from markdown files in resources/prompts/.
 * This allows prompts to be version-controlled as readable markdown files
 * instead of being embedded as Java string literals.
 */
public class PromptLoader {

    /**
     * Loads a prompt from resources/prompts/{agentName}.md
     *
     * @param agentName The name of the agent (e.g., "code-quality", "security")
     * @return The prompt content as a string
     * @throws RuntimeException if the prompt file is not found or cannot be read
     */
    public static String loadPrompt(String agentName) {
        String resourcePath = "/prompts/" + agentName + ".md";
        try (InputStream is = PromptLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Prompt file not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt: " + resourcePath, e);
        }
    }
}
