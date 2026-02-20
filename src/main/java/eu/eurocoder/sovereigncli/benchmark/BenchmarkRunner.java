package eu.eurocoder.sovereigncli.benchmark;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import eu.eurocoder.sovereigncli.agent.ModelManager;
import eu.eurocoder.sovereigncli.security.AuditLog;
import eu.eurocoder.sovereigncli.security.PermissionService;
import eu.eurocoder.sovereigncli.security.ToolSecurityManager;
import eu.eurocoder.sovereigncli.security.TrustLevel;
import eu.eurocoder.sovereigncli.tool.FileSystemTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Orchestrates benchmark execution: loads tasks, creates isolated sandbox directories,
 * runs models in raw or agent mode, evaluates assertions, and collects results.
 */
@Service
public class BenchmarkRunner {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkRunner.class);
    private static final int AGENT_MEMORY_SIZE = 50;
    private static final int EXECUTOR_SHUTDOWN_SECONDS = 5;

    private final ModelManager modelManager;
    private final BenchmarkEvaluator evaluator;

    public BenchmarkRunner(ModelManager modelManager, BenchmarkEvaluator evaluator) {
        this.modelManager = modelManager;
        this.evaluator = evaluator;
    }

    /**
     * Runs a list of benchmark tasks against the specified model.
     *
     * @param tasks        tasks to run
     * @param modelName    model identifier (e.g. "codestral-latest")
     * @param onProgress   callback invoked after each task completes (for live UI updates)
     * @return list of results, one per task
     */
    public List<BenchmarkResult> runTasks(List<BenchmarkTask> tasks, String modelName,
                                          Consumer<BenchmarkResult> onProgress) {
        String provider = modelManager.getProvider().id();
        ChatModel chatModel = modelManager.buildBenchmarkModel(modelName);

        List<BenchmarkResult> results = new ArrayList<>();
        for (BenchmarkTask task : tasks) {
            log.info("Running benchmark: {} ({})", task.id(), task.type());
            BenchmarkResult result = runSingleTask(task, chatModel, modelName, provider);
            results.add(result);
            if (onProgress != null) {
                onProgress.accept(result);
            }
        }
        return results;
    }

    private BenchmarkResult runSingleTask(BenchmarkTask task, ChatModel chatModel,
                                          String modelName, String provider) {
        long startTime = System.currentTimeMillis();
        Path sandboxDir = createSandbox(task);
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "benchmark-" + task.id());
            t.setDaemon(true);
            return t;
        });

        try {
            Future<BenchmarkResult> future = executor.submit(() -> {
                if (task.isRaw()) {
                    return executeRawTask(task, chatModel, modelName, provider, startTime, sandboxDir);
                } else {
                    return executeAgentTask(task, chatModel, modelName, provider, startTime, sandboxDir);
                }
            });

            return future.get(task.timeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.warn("Benchmark task '{}' timed out after {}s", task.id(), task.timeoutSeconds());
            return BenchmarkResult.failure(task.id(), task.category(), modelName, provider,
                    elapsed, "Timeout after " + task.timeoutSeconds() + "s");
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            log.warn("Benchmark task '{}' failed: {}", task.id(), message);
            return BenchmarkResult.failure(task.id(), task.category(), modelName, provider,
                    elapsed, message);
        } finally {
            executor.shutdownNow();
            try {
                executor.awaitTermination(EXECUTOR_SHUTDOWN_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            cleanupSandbox(sandboxDir);
        }
    }

    private BenchmarkResult executeRawTask(BenchmarkTask task, ChatModel chatModel,
                                           String modelName, String provider,
                                           long startTime, Path sandboxDir) {
        ChatResponse response = chatModel.chat(
                dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .messages(List.of(UserMessage.from(task.prompt())))
                        .build());

        long elapsed = System.currentTimeMillis() - startTime;
        String responseText = response.aiMessage().text();
        TokenUsage tokens = response.tokenUsage();
        long inputTokens = tokens != null ? tokens.inputTokenCount() : 0;
        long outputTokens = tokens != null ? tokens.outputTokenCount() : 0;

        if (responseText != null) {
            writeResponseToSandbox(sandboxDir, responseText);
        }

        List<BenchmarkResult.AssertionOutcome> outcomes =
                evaluator.evaluate(task.assertions(), responseText, sandboxDir);

        return BenchmarkResult.success(task.id(), task.category(), modelName, provider,
                outcomes, elapsed, inputTokens, outputTokens);
    }

    private BenchmarkResult executeAgentTask(BenchmarkTask task, ChatModel chatModel,
                                             String modelName, String provider,
                                             long startTime, Path sandboxDir) {
        PermissionService benchmarkPermissions = new PermissionService(TrustLevel.TRUST_ALL);
        AuditLog benchmarkAuditLog = new AuditLog(sandboxDir.resolve(".benchmark-audit.jsonl"));
        ToolSecurityManager benchmarkSecurity = new ToolSecurityManager(
                benchmarkAuditLog, benchmarkPermissions, true, sandboxDir);

        FileSystemTools sandboxTools = new FileSystemTools(benchmarkSecurity, sandboxDir);

        BenchmarkAgentInterface agent = AiServices.builder(BenchmarkAgentInterface.class)
                .chatModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(AGENT_MEMORY_SIZE))
                .tools(sandboxTools)
                .build();

        String agentResponse = agent.execute(task.prompt());

        long elapsed = System.currentTimeMillis() - startTime;

        List<BenchmarkResult.AssertionOutcome> outcomes =
                evaluator.evaluate(task.assertions(), agentResponse, sandboxDir);

        return BenchmarkResult.success(task.id(), task.category(), modelName, provider,
                outcomes, elapsed, 0, 0);
    }

    private Path createSandbox(BenchmarkTask task) {
        try {
            Path sandbox = Files.createTempDirectory("benchmark-" + task.id() + "-");

            Map<String, String> setupFiles = task.setup();
            if (setupFiles != null) {
                for (Map.Entry<String, String> entry : setupFiles.entrySet()) {
                    Path file = sandbox.resolve(entry.getKey());
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, entry.getValue());
                }
            }

            log.debug("Created benchmark sandbox: {}", sandbox);
            return sandbox;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create benchmark sandbox: " + e.getMessage(), e);
        }
    }

    private void writeResponseToSandbox(Path sandboxDir, String response) {
        try {
            Files.writeString(sandboxDir.resolve("_response.txt"), response);
        } catch (IOException e) {
            log.debug("Failed to write response to sandbox: {}", e.getMessage());
        }
    }

    private void cleanupSandbox(Path sandboxDir) {
        try {
            if (sandboxDir != null && Files.exists(sandboxDir)) {
                try (var stream = Files.walk(sandboxDir)) {
                    stream.sorted(java.util.Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException ignored) {
                                }
                            });
                }
            }
        } catch (IOException e) {
            log.debug("Failed to clean up sandbox: {}", e.getMessage());
        }
    }

    /**
     * Internal agent interface for benchmark agent-mode tasks.
     */
    interface BenchmarkAgentInterface {
        @dev.langchain4j.service.SystemMessage("""
                You are an AI coding assistant running a benchmark task.
                You have access to file system tools to read, write, and modify files.
                Complete the task described in the user's message.
                Work within the current directory. Be precise and thorough.
                """)
        String execute(@dev.langchain4j.service.UserMessage String task);
    }
}
