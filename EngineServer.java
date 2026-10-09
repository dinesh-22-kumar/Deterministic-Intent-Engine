import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public final class EngineServer {

    private final int port;
    // Dynamic In-Memory Registry for Compiled Pipelines
    private static final Map<String, ExecutionGraph> pipelineRegistry = new ConcurrentHashMap<>();

    public EngineServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/health", new HealthHandler());
        server.createContext("/compile", new CompileHandler());
        server.createContext("/execute", new ExecuteHandler());
        server.createContext("/journal", new JournalHandler());

        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        System.out.println("=================================================");
        System.out.println("  DETERMINISTIC INTENT ENGINE RUNTIME READY      ");
        System.out.println("  Listening on: http://localhost:" + port);
        System.out.println("  Endpoints:");
        System.out.println("    POST /compile  (Compile dynamic intent DSL)  ");
        System.out.println("    POST /execute  (Execute registered graph)    ");
        System.out.println("    GET  /health   (System memory & telemetry)   ");
        System.out.println("    GET  /journal  (Audit & replay WAL records)  ");
        System.out.println("=================================================");
    }

    // 1. Health Handler
    private static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }
            long freeMem = Runtime.getRuntime().freeMemory();
            String response = "{\n  \"status\": \"HEALTHY\",\n  \"active_pipelines\": " 
                    + pipelineRegistry.keySet() + ",\n  \"free_memory\": " + freeMem + "\n}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            sendResponse(exchange, 200, response);
        }
    }

    // 2. Dynamic Compiler Handler: Compiles DSL directly into memory
    private static class CompileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed. Use POST.");
                return;
            }

            InputStream is = exchange.getRequestBody();
            String dslScript = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();

            if (dslScript.isEmpty()) {
                sendResponse(exchange, 400, "{\"error\": \"Empty DSL script\"}");
                return;
            }

            try {
                String pipelineId = exchange.getRequestHeaders().getFirst("X-Pipeline-Id");
                if (pipelineId == null || pipelineId.isBlank()) {
                    pipelineId = "default";
                }

                ExecutionGraph compiledGraph = IntentCompiler.compile(dslScript);
                pipelineRegistry.put(pipelineId, compiledGraph);

                String response = "{\n"
                        + "  \"status\": \"COMPILED\",\n"
                        + "  \"pipeline_id\": \"" + pipelineId + "\",\n"
                        + "  \"execution_plan\": " + compiledGraph.computeExecutionPlan() + "\n"
                        + "}";

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                sendResponse(exchange, 200, response);
            } catch (Exception e) {
                String error = "{\"status\": \"COMPILATION_ERROR\", \"error\": \"" + e.getMessage() + "\"}";
                sendResponse(exchange, 400, error);
            }
        }
    }

    // 3. Dynamic Execution Handler: Dispatches payload to any compiled pipeline
    private static class ExecuteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed. Use POST.");
                return;
            }

            String pipelineId = exchange.getRequestHeaders().getFirst("X-Pipeline-Id");
            if (pipelineId == null || pipelineId.isBlank()) {
                pipelineId = "default";
            }

            ExecutionGraph graph = pipelineRegistry.get(pipelineId);
            if (graph == null) {
                sendResponse(exchange, 404, "{\"error\": \"Pipeline '" + pipelineId + "' not found. Compile it first via /compile\"}");
                return;
            }

            InputStream is = exchange.getRequestBody();
            String payload = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();

            try {
                String startNode = graph.computeExecutionPlan().get(0);
                DeterministicDispatcher dispatcher = new DeterministicDispatcher(graph, 4);
                dispatcher.dispatch(startNode, payload).get();

                String lastNode = graph.computeExecutionPlan().get(graph.computeExecutionPlan().size() - 1);
                Object finalResult = dispatcher.getResult(lastNode);

                String jsonResponse = "{\n"
                        + "  \"status\": \"SUCCESS\",\n"
                        + "  \"pipeline_id\": \"" + pipelineId + "\",\n"
                        + "  \"input\": \"" + payload + "\",\n"
                        + "  \"final_output\": \"" + finalResult + "\",\n"
                        + "  \"snapshots\": " + dispatcher.getRollbackJournal().size() + "\n"
                        + "}";

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                sendResponse(exchange, 200, jsonResponse);
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"status\": \"FAILURE\", \"error\": \"" + e.getMessage() + "\"}");
            }
        }
    }

    // 4. State Journal Replay & Audit Handler
    private static class JournalHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed. Use GET.");
                return;
            }

            List<String> records = StateJournal.replay();
            StringBuilder sb = new StringBuilder();
            sb.append("{\n  \"wal_records_count\": ").append(records.size()).append(",\n  \"entries\": [\n");
            for (int i = 0; i < records.size(); i++) {
                sb.append("    \"").append(records.get(i).replace("\"", "\\\"")).append("\"");
                if (i < records.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("  ]\n}");

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            sendResponse(exchange, 200, sb.toString());
        }
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void main(String[] args) throws Exception {
        EngineServer server = new EngineServer(8080);
        server.start();
    }
}