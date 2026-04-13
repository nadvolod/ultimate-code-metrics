package com.utm.temporal.llm;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiLlmClientTest {

    // -------------------------------------------------------------------------
    // truncateDiff – static helper tests (no LLM or network required)
    // -------------------------------------------------------------------------

    @Test
    void truncateDiff_contentWithinLimit_returnsUnchanged() {
        String content = "small diff";
        assertEquals(content, OpenAiLlmClient.truncateDiff(content, 100));
    }

    @Test
    void truncateDiff_contentExactlyAtLimit_returnsUnchanged() {
        String content = "a".repeat(100);
        assertEquals(content, OpenAiLlmClient.truncateDiff(content, 100));
    }

    @Test
    void truncateDiff_contentExceedsLimit_startsWithTruncatedPrefix() {
        String content = "a".repeat(200);
        String result = OpenAiLlmClient.truncateDiff(content, 100);
        assertTrue(result.startsWith("a".repeat(100)), "Result should start with first 100 chars");
    }

    @Test
    void truncateDiff_contentExceedsLimit_appendsTruncationNotice() {
        String content = "x".repeat(500);
        String result = OpenAiLlmClient.truncateDiff(content, 200);
        assertTrue(result.contains("[TRUNCATED:"), "Result should contain truncation notice");
        assertTrue(result.contains("200"), "Truncation notice should mention the configured limit");
    }

    @Test
    void truncateDiff_nullContent_returnsNull() {
        assertNull(OpenAiLlmClient.truncateDiff(null, 100));
    }

    // -------------------------------------------------------------------------
    // applyDiffSizeLimit – tests using the package-private test constructor
    // -------------------------------------------------------------------------

    @Test
    void applyDiffSizeLimit_userMessageWithinLimit_isUnchanged() {
        OpenAiLlmClient client = new OpenAiLlmClient(50);
        String content = "PR Title: test\n\nDiff:\nsmall change";
        List<Message> messages = Arrays.asList(
                new Message("system", "You are a code quality reviewer."),
                new Message("user", content)
        );
        List<Message> result = client.applyDiffSizeLimit(messages);
        assertEquals(content, result.get(1).content);
    }

    @Test
    void applyDiffSizeLimit_userMessageExceedsLimit_isTruncatedWithNotice() {
        OpenAiLlmClient client = new OpenAiLlmClient(20);
        String content = "a".repeat(100);
        List<Message> messages = Arrays.asList(
                new Message("system", "system prompt"),
                new Message("user", content)
        );
        List<Message> result = client.applyDiffSizeLimit(messages);
        String userContent = result.get(1).content;
        assertTrue(userContent.startsWith("a".repeat(20)), "Truncated content should start with first 20 chars");
        assertTrue(userContent.contains("[TRUNCATED:"), "Truncated content should include a notice");
    }

    @Test
    void applyDiffSizeLimit_systemMessageNotTruncated() {
        OpenAiLlmClient client = new OpenAiLlmClient(10);
        String systemContent = "s".repeat(100);
        List<Message> messages = Arrays.asList(
                new Message("system", systemContent),
                new Message("user", "short")
        );
        List<Message> result = client.applyDiffSizeLimit(messages);
        assertEquals(systemContent, result.get(0).content, "System message must not be truncated");
    }

    @Test
    void applyDiffSizeLimit_preservesMessageCount() {
        OpenAiLlmClient client = new OpenAiLlmClient(5);
        List<Message> messages = Arrays.asList(
                new Message("system", "system"),
                new Message("user", "u".repeat(50))
        );
        List<Message> result = client.applyDiffSizeLimit(messages);
        assertEquals(2, result.size());
    }

    @Test
    void defaultMaxDiffChars_isPositiveValue() {
        assertTrue(OpenAiLlmClient.DEFAULT_MAX_DIFF_CHARS > 0);
    }

    @Test
    void testConstructor_negativeMaxDiffChars_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new OpenAiLlmClient(-1));
    }

    @Test
    void testConstructor_zeroMaxDiffChars_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new OpenAiLlmClient(0));
    }
}
