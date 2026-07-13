package dev.builderai.claudebridge;

import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Runs work on the Minecraft client thread and returns the result to the MCP server thread. */
public final class ClientThread {
    private ClientThread() {}

    public static <T> T call(Supplier<T> work) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) {
            return work.get();
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                future.complete(work.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted waiting for client thread", e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (java.util.concurrent.TimeoutException e) {
            throw new RuntimeException("Timed out waiting for client thread (30s)", e);
        }
    }

    public static void run(Runnable work) {
        call(() -> {
            work.run();
            return null;
        });
    }
}
