package dev.builderai.claudebridge;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Queue of /claude prompts typed by the player, consumed by the MCP tool await_chat_prompt. */
public final class PromptQueue {
    private static final LinkedBlockingQueue<String> QUEUE = new LinkedBlockingQueue<>();

    private PromptQueue() {}

    public static void push(String prompt) {
        QUEUE.offer(prompt);
    }

    /** Blocks up to timeoutSeconds; returns null on timeout. */
    public static String poll(int timeoutSeconds) throws InterruptedException {
        return QUEUE.poll(timeoutSeconds, TimeUnit.SECONDS);
    }

    public static int pending() {
        return QUEUE.size();
    }
}
