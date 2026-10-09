import java.util.*;

public final class ExecutionGraph {
    private final Map<String, List<String>> adjacencyList = new HashMap<>();
    private final Map<String, Integer> inDegree = new HashMap<>();
    private final Map<String, KernelNode<?, ?>> nodeRegistry = new HashMap<>();

    public void registerNode(KernelNode<?, ?> node) {
        nodeRegistry.put(node.getId(), node);
        adjacencyList.putIfAbsent(node.getId(), new ArrayList<>());
        inDegree.putIfAbsent(node.getId(), 0);
    }

    public void addEdge(String fromNodeId, String toNodeId) {
        if (!nodeRegistry.containsKey(fromNodeId) || !nodeRegistry.containsKey(toNodeId)) {
            throw new IllegalArgumentException("Both nodes must be registered before linking an edge.");
        }
        adjacencyList.get(fromNodeId).add(toNodeId);
        inDegree.put(toNodeId, inDegree.get(toNodeId) + 1);
    }

    // Kahn's Algorithm for Topological Sorting & Cycle Detection
    public List<String> computeExecutionPlan() {
        Map<String, Integer> currentInDegree = new HashMap<>(inDegree);
        Queue<String> zeroInDegreeQueue = new ArrayDeque<>();

        for (Map.Entry<String, Integer> entry : currentInDegree.entrySet()) {
            if (entry.getValue() == 0) {
                zeroInDegreeQueue.add(entry.getKey());
            }
        }

        List<String> executionOrder = new ArrayList<>();
        while (!zeroInDegreeQueue.isEmpty()) {
            String currentNode = zeroInDegreeQueue.poll();
            executionOrder.add(currentNode);

            for (String neighbor : adjacencyList.getOrDefault(currentNode, Collections.emptyList())) {
                int updatedDegree = currentInDegree.get(neighbor) - 1;
                currentInDegree.put(neighbor, updatedDegree);
                if (updatedDegree == 0) {
                    zeroInDegreeQueue.add(neighbor);
                }
            }
        }

        if (executionOrder.size() != nodeRegistry.size()) {
            throw new IllegalStateException("Cyclic dependency detected. Execution halted to protect state invariants.");
        }

        return executionOrder;
    }

    @SuppressWarnings("unchecked")
    public <I, O> KernelNode<I, O> getNode(String id) {
        return (KernelNode<I, O>) nodeRegistry.get(id);
    }

    public List<String> getDownstreamNeighbors(String id) {
        return adjacencyList.getOrDefault(id, Collections.emptyList());
    }
}