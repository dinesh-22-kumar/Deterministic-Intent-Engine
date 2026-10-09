public class EngineDemo {
    public static void main(String[] args) throws Exception {
        ExecutionGraph graph = new ExecutionGraph();

        // Node A: Ingest
        KernelNode<Object, Object> ingest = new KernelNode<>(
            "INGEST",
            KernelRegistry.getKernel("TRIM_LOWERCASE"),
            0
        );

        // Node B: Rogue Node with 1500ms Timeout limit and Safe Fallback
        KernelNode<Object, Object> rogueNode = new KernelNode<>(
            "WATCHDOG_GUARD",
            KernelRegistry.getKernel("HANG_TASK"),
            KernelRegistry.getKernel("SAFE_FALLBACK"),
            1,    // retry count
            1500  // 1.5 seconds timeout limit
        );

        // Node C: Final Stage
        KernelNode<Object, Object> outputNode = new KernelNode<>(
            "OUTPUT",
            KernelRegistry.getKernel("REVERSE_STRING"),
            0
        );

        graph.registerNode(ingest);
        graph.registerNode(rogueNode);
        graph.registerNode(outputNode);

        graph.addEdge("INGEST", "WATCHDOG_GUARD");
        graph.addEdge("WATCHDOG_GUARD", "OUTPUT");

        System.out.println("Topological Execution Plan: " + graph.computeExecutionPlan());

        DeterministicDispatcher dispatcher = new DeterministicDispatcher(graph, 4);

        System.out.println("\n--- STARTING SANDBOXED RUN ---");
        long start = System.currentTimeMillis();

        dispatcher.dispatch("INGEST", "   MISSION_CRITICAL_SANDBOX   ").get();

        long duration = System.currentTimeMillis() - start;

        System.out.println("\n--- RUN FINISHED in " + duration + "ms ---");
        System.out.println("Final Result: " + dispatcher.getResult("OUTPUT"));
        System.out.println("WAL Records:  " + StateJournal.replay().size());
    }
}