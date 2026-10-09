import java.util.*;
import java.util.function.Function;

public final class KernelRegistry {
    private static final Map<String, Function<Object, Object>> registry = new HashMap<>();

    static {
        // Register core deterministic computation primitives
        registry.put("TRIM_LOWERCASE", input -> ((String) input).trim().toLowerCase());
        registry.put("TO_BYTES", input -> ((String) input).getBytes());
        registry.put("GENERATE_HASH", input -> Arrays.hashCode((byte[]) input));
        registry.put("REVERSE_STRING", input -> new StringBuilder((String) input).reverse().toString());
        registry.put("BASE64_ENCODE", input -> Base64.getEncoder().encodeToString((byte[]) input));

        // Multiple inputs aggregation kernel
        registry.put("AGGREGATE_SUMMARY", input -> {
            if (input instanceof List) {
                return "AGGREGATED_PACKET::" + input.toString();
            }
            return "PACKET::" + input;
        });

        // Sandbox test: Simulates a rogue slow task / hanging execution
        registry.put("HANG_TASK", input -> {
            try {
                System.out.println("🚨 Rogue node executing infinite loop / hang...");
                Thread.sleep(10000); // Exceeds standard watchdog limit
            } catch (InterruptedException e) {
                System.out.println("🛑 Rogue thread successfully interrupted and killed by Sandbox!");
            }
            return input;
        });

        // Sandbox test: Fallback recovery for hanging tasks
        registry.put("SAFE_FALLBACK", input -> "RECOVERED_FROM_HANG::" + input);
    }

    public static Function<Object, Object> getKernel(String operationKey) {
        Function<Object, Object> kernel = registry.get(operationKey);
        if (kernel == null) {
            throw new IllegalArgumentException("Unknown kernel operation: " + operationKey);
        }
        return kernel;
    }
}