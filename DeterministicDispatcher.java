import java.util.*;
import java.util.concurrent.*;

public final class DeterministicDispatcher {
    private final ExecutionGraph graph;
    private final ExecutorService workerPool;
    private final Map<String, Object> stateSlotTable = new ConcurrentHashMap<>();
    private final Map<String, List<Object>> aggregatedInputs = new ConcurrentHashMap<>();
    private final Deque<StateSnapshot> rollbackJournal = new ConcurrentLinkedDeque<>();

    public DeterministicDispatcher(ExecutionGraph graph, int threadPoolSize) {
        this.graph = graph;
        this.workerPool = Executors.newFixedThreadPool(threadPoolSize);
    }

    public CompletableFuture<Void> dispatch(String startNodeId, Object rootInput) {
        List<String> executionPlan = graph.computeExecutionPlan();
        stateSlotTable.put(startNodeId, rootInput);

        Map<String, CompletableFuture<Void>> nodeFutures = new ConcurrentHashMap<>();

        for (String nodeId : executionPlan) {
            List<String> parents = new ArrayList<>();
            List<CompletableFuture<Void>> parentFutures = new ArrayList<>();

            for (String potentialParent : executionPlan) {
                if (graph.getDownstreamNeighbors(potentialParent).contains(nodeId)) {
                    parents.add(potentialParent);
                    if (nodeFutures.containsKey(potentialParent)) {
                        parentFutures.add(nodeFutures.get(potentialParent));
                    }
                }
            }

            CompletableFuture<Void> barrier = parentFutures.isEmpty()
                    ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.allOf(parentFutures.toArray(new CompletableFuture[0]));

            CompletableFuture<Void> executionFuture = barrier.thenRunAsync(() -> {
                Object input;

                if (parents.isEmpty()) {
                    input = stateSlotTable.get(nodeId);
                } else if (parents.size() == 1) {
                    input = stateSlotTable.get(parents.get(0));
                } else {
                    input = aggregatedInputs.get(nodeId);
                }

                // In-memory snapshot
                rollbackJournal.push(new StateSnapshot(nodeId, input));

                KernelNode<Object, Object> node = graph.getNode(nodeId);
                Object output = node.execute(input);

                stateSlotTable.put(nodeId, output);

                // WAL Disk Persistence: Flush state transition to disk
                StateJournal.append(nodeId, output);

                // Downstream propagation
                for (String childId : graph.getDownstreamNeighbors(nodeId)) {
                    aggregatedInputs.computeIfAbsent(childId, k -> Collections.synchronizedList(new ArrayList<>()))
                                    .add(output);
                }
            }, workerPool);

            nodeFutures.put(nodeId, executionFuture);
        }

        return CompletableFuture.allOf(nodeFutures.values().toArray(new CompletableFuture[0]))
                .whenComplete((res, err) -> workerPool.shutdown());
    }

    public Object getResult(String nodeId) {
        return stateSlotTable.get(nodeId);
    }

    public List<StateSnapshot> getRollbackJournal() {
        return new ArrayList<>(rollbackJournal);
    }
}