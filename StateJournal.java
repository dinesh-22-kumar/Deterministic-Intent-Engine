import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class StateJournal {
    private static final Path JOURNAL_PATH = Paths.get("dvm_state.wal");
    private static final Object WRITE_LOCK = new Object();

    static {
        try {
            if (!Files.exists(JOURNAL_PATH)) {
                Files.createFile(JOURNAL_PATH);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize state WAL file", e);
        }
    }

    /**
     * Appends an immutable state record directly to disk.
     * Format per line: TIMESTAMP | NODE_ID | PAYLOAD
     */
    public static void append(String nodeId, Object payload) {
        String logEntry = Instant.now() + " | " + nodeId + " | " + String.valueOf(payload) + "\n";
        byte[] bytes = logEntry.getBytes(StandardCharsets.UTF_8);

        synchronized (WRITE_LOCK) {
            try (FileChannel channel = FileChannel.open(
                    JOURNAL_PATH, 
                    StandardOpenOption.WRITE, 
                    StandardOpenOption.APPEND)) {
                
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
            } catch (IOException e) {
                System.err.println("❌ WAL Write Failure for node [" + nodeId + "]: " + e.getMessage());
            }
        }
    }

    /**
     * Reads and replays the persistent journal from disk.
     */
    public static List<String> replay() {
        try {
            if (!Files.exists(JOURNAL_PATH)) {
                return new ArrayList<>();
            }
            return Files.readAllLines(JOURNAL_PATH, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("❌ WAL Replay Failure: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}