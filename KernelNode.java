import java.util.concurrent.*;
import java.util.function.Function;

public final class KernelNode<I, O> {
    private final String id;
    private final Function<I, O> computation;
    private final Function<I, O> fallbackComputation;
    private final int maxRetries;
    private final long timeoutMillis;

    public KernelNode(String id, Function<I, O> computation, int maxRetries) {
        this(id, computation, null, maxRetries, 3000); // default 3s timeout
    }

    public KernelNode(String id, Function<I, O> computation, Function<I, O> fallbackComputation, int maxRetries) {
        this(id, computation, fallbackComputation, maxRetries, 3000);
    }

    public KernelNode(String id, Function<I, O> computation, Function<I, O> fallbackComputation, int maxRetries, long timeoutMillis) {
        this.id = id;
        this.computation = computation;
        this.fallbackComputation = fallbackComputation;
        this.maxRetries = maxRetries;
        this.timeoutMillis = timeoutMillis;
    }

    public String getId() {
        return id;
    }

    public O execute(I input) {
        int attempts = 0;
        Throwable lastError = null;

        while (attempts <= maxRetries) {
            ExecutorService watchdog = Executors.newSingleThreadExecutor();
            Future<O> task = watchdog.submit(() -> computation.apply(input));

            try {
                // Hard timeout guard
                return task.get(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                task.cancel(true);
                attempts++;
                lastError = new TimeoutException("Execution exceeded time budget (" + timeoutMillis + "ms)");
                System.out.println("⏳ Sandbox Alert: Node [" + id + "] timed out on attempt " + attempts);
            } catch (Throwable t) {
                attempts++;
                lastError = t.getCause() != null ? t.getCause() : t;
                System.out.println("⚠️ Warning: Node [" + id + "] attempt " + attempts + " failed: " + lastError.getMessage());
            } finally {
                watchdog.shutdownNow();
            }
        }

        // Trigger Fallback if timed out or failed
        if (fallbackComputation != null) {
            System.out.println("🔄 Sandbox Recovery: Triggering fallback for Node [" + id + "]...");
            return fallbackComputation.apply(input);
        }

        throw new IllegalStateException("Node [" + id + "] terminated by Sandbox Guard: " + lastError.getMessage(), lastError);
    }
}