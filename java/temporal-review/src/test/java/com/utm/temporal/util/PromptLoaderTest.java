package com.utm.temporal.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptLoaderTest {

    @Test
    void loadPrompt_codeQuality_returnsContent() {
        String prompt = PromptLoader.loadPrompt("code-quality");
        assertNotNull(prompt);
        assertFalse(prompt.isEmpty());
        assertTrue(prompt.contains("Code Quality"));
    }

    @Test
    void loadPrompt_security_returnsContent() {
        String prompt = PromptLoader.loadPrompt("security");
        assertNotNull(prompt);
        assertTrue(prompt.contains("Security"));
    }

    @Test
    void loadPrompt_testQuality_returnsContent() {
        String prompt = PromptLoader.loadPrompt("test-quality");
        assertNotNull(prompt);
        assertTrue(prompt.contains("Test Quality"));
    }

    @Test
    void loadPrompt_complexity_returnsContent() {
        String prompt = PromptLoader.loadPrompt("complexity");
        assertNotNull(prompt);
        assertTrue(prompt.contains("Complexity"));
    }

    @Test
    void loadPrompt_priority_returnsContent() {
        String prompt = PromptLoader.loadPrompt("priority");
        assertNotNull(prompt);
        assertTrue(prompt.contains("Priority"));
    }

    @Test
    void loadPrompt_missingFile_throwsRuntimeException() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            PromptLoader.loadPrompt("nonexistent-agent")
        );
        assertTrue(ex.getMessage().contains("Prompt file not found"));
        assertTrue(ex.getMessage().contains("nonexistent-agent"));
    }

    @Test
    void loadPrompt_allPromptsContainJsonResponseFormat() {
        String[] agents = {"code-quality", "security", "test-quality", "complexity", "priority"};
        for (String agent : agents) {
            String prompt = PromptLoader.loadPrompt(agent);
            assertTrue(prompt.contains("agentName"), agent + " prompt missing agentName in JSON format");
            assertTrue(prompt.contains("riskLevel"), agent + " prompt missing riskLevel in JSON format");
            assertTrue(prompt.contains("recommendation"), agent + " prompt missing recommendation in JSON format");
            assertTrue(prompt.contains("findings"), agent + " prompt missing findings in JSON format");
        }
    }
}
