package com.cpclaw.agent;

import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public final class AnswerStreamSupport {

    private static final int CHUNK_SIZE = 24;
    private static final long CHUNK_DELAY_NANOS = 18_000_000L;

    private AnswerStreamSupport() {
    }

    public static void emitReadableChunks(String content, Consumer<String> chunkConsumer) {
        if (chunkConsumer == null || content == null || content.isEmpty()) {
            return;
        }
        int offset = 0;
        while (offset < content.length()) {
            int end = Math.min(content.length(), offset + CHUNK_SIZE);
            chunkConsumer.accept(content.substring(offset, end));
            offset = end;
            if (offset < content.length()) {
                LockSupport.parkNanos(CHUNK_DELAY_NANOS);
            }
        }
    }
}
