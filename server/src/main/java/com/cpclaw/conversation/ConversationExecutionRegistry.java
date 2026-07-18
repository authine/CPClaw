package com.cpclaw.conversation;

import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class ConversationExecutionRegistry {

    private final ConcurrentMap<String, ExecutionHandle> executions = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ExecutionHandle register(String requestedId) {
        String executionId = requestedId == null || requestedId.isBlank()
            ? UUID.randomUUID().toString()
            : requestedId.trim();
        ExecutionHandle handle = new ExecutionHandle(executionId);
        if (executions.putIfAbsent(executionId, handle) != null) {
            throw new IllegalArgumentException("执行ID已存在，请重新发起请求");
        }
        return handle;
    }

    public void submit(ExecutionHandle handle, Runnable task) {
        handle.attach(executor.submit(task));
    }

    public CancellationResult cancel(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return new CancellationResult(false, "not_found");
        }
        ExecutionHandle handle = executions.get(executionId.trim());
        if (handle == null) {
            return new CancellationResult(false, "not_found");
        }
        boolean cancelled = handle.cancel();
        return new CancellationResult(cancelled, handle.stateName());
    }

    public void complete(ExecutionHandle handle) {
        if (handle != null) {
            executions.remove(handle.executionId(), handle);
        }
    }

    @PreDestroy
    public void shutdown() {
        executions.values().forEach(ExecutionHandle::cancel);
        executions.clear();
        executor.shutdownNow();
    }

    public static final class ExecutionHandle {
        private final String executionId;
        private final AtomicReference<ExecutionState> state = new AtomicReference<>(ExecutionState.RUNNING);
        private final AtomicReference<Future<?>> future = new AtomicReference<>();

        private ExecutionHandle(String executionId) {
            this.executionId = executionId;
        }

        public String executionId() {
            return executionId;
        }

        public boolean isCancelled() {
            ExecutionState current = state.get();
            return current == ExecutionState.CANCEL_REQUESTED || current == ExecutionState.CANCELLED;
        }

        public boolean cancel() {
            ExecutionState current = state.get();
            if (current == ExecutionState.CANCEL_REQUESTED || current == ExecutionState.CANCELLED) {
                return true;
            }
            if (!state.compareAndSet(ExecutionState.RUNNING, ExecutionState.CANCEL_REQUESTED)) {
                return false;
            }
            Future<?> running = future.get();
            if (running != null) {
                running.cancel(true);
            }
            return true;
        }

        public boolean tryBeginCommit() {
            return state.compareAndSet(ExecutionState.RUNNING, ExecutionState.COMMITTING);
        }

        public void markCompleted() {
            state.compareAndSet(ExecutionState.COMMITTING, ExecutionState.COMPLETED);
        }

        public void markCancelled() {
            state.compareAndSet(ExecutionState.CANCEL_REQUESTED, ExecutionState.CANCELLED);
        }

        public String stateName() {
            return state.get().name().toLowerCase();
        }

        private void attach(Future<?> running) {
            if (!future.compareAndSet(null, running)) {
                running.cancel(true);
                throw new IllegalStateException("执行任务已启动");
            }
            if (isCancelled()) {
                running.cancel(true);
            }
        }
    }

    public record CancellationResult(boolean cancelled, String state) {
    }

    private enum ExecutionState {
        RUNNING,
        CANCEL_REQUESTED,
        CANCELLED,
        COMMITTING,
        COMPLETED
    }
}
