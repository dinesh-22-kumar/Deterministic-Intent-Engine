import java.util.regex.*;

public final class IntentCompiler {

    /**
     * Compiles a declarative intent DSL string into an ExecutionGraph.
     * Format: NODE_ID(OPERATION) -> NODE_ID(OPERATION) -> ...
     */
    public static ExecutionGraph compile(String intentScript) {
        ExecutionGraph graph = new ExecutionGraph();
        String[] pipelineSteps = intentScript.split("->");

        Pattern pattern = Pattern.compile("([A-Za-z0-9_]+)\\(([A-Za-z0-9_]+)\\)");
        String previousNodeId = null;

        for (String rawStep : pipelineSteps) {
            String step = rawStep.trim();
            Matcher matcher = pattern.matcher(step);

            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid syntax in pipeline step: " + step);
            }

            String nodeId = matcher.group(1);
            String operationKey = matcher.group(2);

            // Construct deterministic kernel from registry
            KernelNode<Object, Object> node = new KernelNode<>(
                nodeId,
                KernelRegistry.getKernel(operationKey),
                1
            );

            graph.registerNode(node);

            // Dynamically stitch the execution edge
            if (previousNodeId != null) {
                graph.addEdge(previousNodeId, nodeId);
            }

            previousNodeId = nodeId;
        }

        // Cycle check & topological ordering validation
        graph.computeExecutionPlan();
        return graph;
    }
}