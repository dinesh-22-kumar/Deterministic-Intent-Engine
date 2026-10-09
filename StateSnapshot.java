import java.time.Instant;

public final class StateSnapshot {
    private final String nodeId;
    private final Object statePayload;
    private final Instant timestamp;

    public StateSnapshot(String nodeId, Object statePayload) {
        this.nodeId = nodeId;
        this.statePayload = statePayload;
        this.timestamp = Instant.now();
    }

    public String getNodeId() {
        return nodeId;
    }

    public Object getStatePayload() {
        return statePayload;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "Snapshot[Node=" + nodeId + ", At=" + timestamp + ", Data=" + statePayload + "]";
    }
}