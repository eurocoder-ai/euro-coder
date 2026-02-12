package dev.aihelpcenter.sovereigncli;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Routes user requests through AI agents depending on the current mode:
 * <p>
 * <b>Auto mode</b> (hybrid):
 *   Planner model explores the project + produces a plan → Coder model executes.
 *   Complex tasks get both; simple tasks go directly to the Coder.
 * <p>
 * <b>Single-model mode</b>:
 *   A specific model handles everything (exploration + planning + coding).
 * <p>
 * All agents receive project context automatically so they understand the
 * full codebase structure before acting. The Planner has read-only tools
 * (getProjectTree, findFiles, readFile, listFiles) so it can explore the
 * project and produce informed, file-specific plans.
 * <p>
 * Supports both Mistral Cloud API and Ollama (local) providers.
 * Models are created <b>lazily</b> via {@link ModelManager}, so the first-run
 * setup dialog can collect configuration before any model is instantiated.
 */
@Service
public class HybridAgentRouter {

    private static final Logger log = LoggerFactory.getLogger(HybridAgentRouter.class);

    private final ModelManager modelManager;
    private final FileSystemTools fileSystemTools;

    private PlannerAgent planner;
    private CoderAgent coder;
    private DirectAgent directAgent;
    private boolean initialized = false;

    // ── Inner AI Service interfaces ──────────────────────────────────

    interface PlannerAgent {
        @SystemMessage("""
                You are the PLANNER of a Sovereign AI Developer Agent.
                Your job is to EXPLORE the project and produce a detailed, actionable plan.
                
                CRITICAL WORKFLOW — always follow this order:
                1. EXPLORE: You have tools. Start with getProjectTree to see the full project layout.
                   Use findFiles to locate specific file types (e.g., '*.java', '*.py').
                   Use searchContent to find usages, imports, or references across the codebase.
                2. READ: Use readFile (or readFileRange for large files) to read ALL files relevant
                   to the user's request.
                   - For code tasks: read source files, existing tests, build files (pom.xml, package.json).
                   - For refactoring: read every file that will be affected.
                   - Use searchContent to find all files that reference a symbol being changed.
                   - The MORE files you read, the BETTER your plan will be.
                3. ANALYZE: Identify patterns, dependencies, coding conventions, and existing tests.
                4. PLAN: Produce a step-by-step plan with SPECIFIC file paths and concrete actions.
                
                Plan format rules:
                - Reference SPECIFIC files by their full path (e.g., "Modify src/main/.../Foo.java").
                - Describe WHAT to change in each file (e.g., "Add a new method bar() that does X").
                - If code needs to be WRITTEN or GENERATED, end your plan with: [NEEDS_CODE]
                - If the task is purely informational (explain, answer a question),
                  end with: [DONE] and include your complete answer.
                - Be thorough but concise. No code in your response — only the plan.
                """)
        String plan(@UserMessage String userMessage);
    }

    interface CoderAgent {
        @SystemMessage("""
                You are the CODER of a Sovereign AI Developer Agent running locally.
                You receive a PLAN from the Planner and execute it by writing code and files.
                
                You have access to the local file system AND the ability to execute shell commands
                through your tools (getProjectTree, findFiles, searchContent, listFiles,
                readFile, readFileRange, writeFile, appendToFile, createDirectory, deleteFile,
                runCommand, runCommandInDirectory).
                
                CRITICAL WORKFLOW — always follow this order:
                1. VERIFY CONTEXT: Even though you received a plan, read any source files you
                   need to understand before modifying them. Never write code for a file you
                   haven't read first. Use searchContent to find all references to symbols
                   you are changing. Use readFileRange for large files.
                2. READ DEPENDENCIES: If modifying a class, also read the classes it imports
                   or depends on, so your changes are consistent.
                3. EXECUTE: Follow the plan step by step, writing production-quality code with
                   proper error handling. Match the existing code style and conventions.
                4. VERIFY: After making changes, run builds or tests with runCommand to confirm
                   your changes compile and work (e.g., 'mvn compile', 'npm test').
                5. SUMMARIZE: After completing all steps, summarize what you created/modified.
                
                Rules:
                - NEVER write code for a file without reading it (or its directory) first.
                - When writing tests, read ALL source classes that need testing.
                - When modifying existing code, preserve the file's style and conventions.
                - Use runCommand for builds, tests, git, docker, and other terminal operations.
                """)
        String execute(@UserMessage String planAndContext);
    }

    interface DirectAgent {
        @SystemMessage("""
                You are a Sovereign AI Developer Agent running entirely on local/sovereign infrastructure.
                You have full access to the local file system AND the ability to execute any shell command
                through your tools (getProjectTree, findFiles, searchContent, listFiles,
                readFile, readFileRange, writeFile, appendToFile, createDirectory, deleteFile,
                runCommand, runCommandInDirectory).
                
                CRITICAL WORKFLOW — always follow this order:
                1. EXPLORE FIRST: Before doing ANYTHING, use getProjectTree to understand the
                   project structure. Use findFiles to locate relevant files (e.g., '*.java',
                   '*.py', '*.ts'). Use searchContent to find usages and references.
                2. READ RELEVANT FILES: Read ALL files related to the user's request.
                   Use readFileRange for large files when you only need specific sections.
                   - For "add tests": read EVERY source class, existing tests, and build config.
                   - For "fix a bug": read the buggy file AND all files that interact with it.
                   - For "refactor X": use searchContent('X') to find every file referencing it.
                   - For "create feature": read existing code to understand patterns and conventions.
                   - The MORE context you gather, the BETTER your output will be.
                3. UNDERSTAND: Identify the project's language, framework, patterns, dependencies,
                   and coding conventions before writing any code.
                4. ACT: Create, modify, or delete files as needed. Write production-quality code.
                5. VERIFY: Run builds/tests with runCommand to confirm your changes work
                   (e.g., 'mvn test', 'npm run build', 'python -m pytest').
                6. SUMMARIZE: Provide a clear summary of what you did.
                
                Rules:
                - NEVER create or modify files without first reading the relevant source files.
                - NEVER write a single test without reading every class that needs testing.
                - ALWAYS match the existing code style and conventions.
                - If a task is ambiguous, explore the project first, then ask for clarification.
                """)
        String chat(@UserMessage String userMessage);
    }

    // ── Constructor ──────────────────────────────────────────────────

    public HybridAgentRouter(ModelManager modelManager, FileSystemTools fileSystemTools) {
        this.modelManager = modelManager;
        this.fileSystemTools = fileSystemTools;
        // Models are NOT created here — they are created lazily on first use
    }

    // ── Lazy initialization ──────────────────────────────────────────

    private synchronized void ensureInitialized() {
        if (!initialized) {
            buildAgents();
        }
    }

    /**
     * Rebuilds all AI service agents (e.g. after a model switch).
     * Chat memory is reset.
     */
    public synchronized void reinitialize() {
        this.initialized = false;
        this.planner = null;
        this.coder = null;
        this.directAgent = null;
        buildAgents();
    }

    private void buildAgents() {
        log.info("Building AI agents — mode: {}", modelManager.getCurrentMode());

        // Planner — has read-only tools so it can explore the project
        this.planner = AiServices.builder(PlannerAgent.class)
                .chatModel(modelManager.getPlannerModel())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(30))
                .tools(fileSystemTools)
                .build();

        // Coder — has all tools for reading, writing, and executing
        this.coder = AiServices.builder(CoderAgent.class)
                .chatModel(modelManager.getCoderModel())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(30))
                .tools(fileSystemTools)
                .build();

        // Direct agent — single model for all tasks (uses coder model + all tools)
        this.directAgent = AiServices.builder(DirectAgent.class)
                .chatModel(modelManager.getCoderModel())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(30))
                .tools(fileSystemTools)
                .build();

        this.initialized = true;
        log.info("AI agents ready — planner: {}, coder: {}",
                modelManager.getPlannerModelName(), modelManager.getCoderModelName());
    }

    // ── Public API ───────────────────────────────────────────────────

    /**
     * Auto-routing: uses hybrid mode for complex tasks, direct mode for simple ones.
     * In "auto" mode, complex tasks go through Planner → Coder (two different models).
     * In single-model mode, everything goes through the Direct agent.
     */
    public HybridResult chat(String userMessage) {
        ensureInitialized();
        if (modelManager.isAutoMode() && isComplexTask(userMessage)) {
            return chatHybrid(userMessage);
        }
        String result = chatDirect(userMessage);
        return new HybridResult(null, result, false);
    }

    /**
     * Full hybrid flow: Planner explores + plans → Coder executes.
     * The Planner receives a project context snapshot so it starts with awareness
     * of the project structure before using its tools for deeper exploration.
     */
    public HybridResult chatHybrid(String userMessage) {
        ensureInitialized();
        log.info("HYBRID mode — Planner ({}) analyzing...", modelManager.getPlannerModelName());

        // Gather project context automatically
        String projectContext = gatherProjectContext();

        // Step 1: Plan (with project context + tools for deeper exploration)
        String enrichedPrompt = String.format("""
                PROJECT CONTEXT (current directory structure):
                %s
                
                USER REQUEST: %s
                
                Use your tools (getProjectTree, findFiles, readFile) to explore relevant files \
                before making your plan. Read ALL files related to this request.
                """, projectContext, userMessage);

        String plan = planner.plan(enrichedPrompt);
        log.info("Plan generated. Needs code: {}", plan.contains("[NEEDS_CODE]"));

        // Step 2: If code is needed, hand off to Coder
        if (plan.contains("[NEEDS_CODE]")) {
            String cleanPlan = plan.replace("[NEEDS_CODE]", "").trim();
            log.info("CODER ({}) executing plan...", modelManager.getCoderModelName());

            String coderPrompt = String.format("""
                    The user asked: "%s"
                    
                    Here is the detailed plan from the Planner (who has already explored the project):
                    %s
                    
                    IMPORTANT: Before writing or modifying any file, READ it first with readFile \
                    to understand its current content and coding style. Execute the plan now.
                    """, userMessage, cleanPlan);

            String result = coder.execute(coderPrompt);
            return new HybridResult(cleanPlan, result, true);
        }

        // Task was informational — planner already answered it
        String answer = plan.replace("[DONE]", "").trim();
        return new HybridResult(answer, null, false);
    }

    /**
     * Direct single-model flow with tools.
     * Injects project context so the agent starts with awareness.
     */
    public String chatDirect(String userMessage) {
        ensureInitialized();
        log.info("DIRECT mode — {} handling request...", modelManager.getCoderModelName());

        // Inject project context so the agent knows it's working in a real project
        // and is much more likely to use its tools from the very first message.
        String projectContext = gatherProjectContext();
        String enrichedMessage = String.format("""
                PROJECT CONTEXT (current directory structure):
                %s
                
                USER REQUEST: %s
                
                Remember: you have tools to explore, read, write files, and run commands. \
                Use them to fulfill the request — do NOT just give advice.
                """, projectContext, userMessage);

        return directAgent.chat(enrichedMessage);
    }

    // ── Context gathering ────────────────────────────────────────────

    /**
     * Gathers a lightweight project context snapshot to inject into agent prompts.
     * This gives agents immediate awareness of the project structure without
     * requiring them to spend a tool call discovering it.
     */
    String gatherProjectContext() {
        try {
            return fileSystemTools.getProjectTree(".", 3);
        } catch (Exception e) {
            log.warn("Failed to gather project context: {}", e.getMessage());
            return "(project context unavailable)";
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    // package-private for testing
    boolean isComplexTask(String message) {
        String lower = message.toLowerCase();
        return lower.contains("refactor") ||
                lower.contains("rewrite") ||
                lower.contains("redesign") ||
                lower.contains("architect") ||
                lower.contains("migrate") ||
                lower.contains("convert") ||
                lower.contains("optimize") ||
                lower.contains("review") ||
                lower.contains("build a") ||
                lower.contains("create a project") ||
                lower.contains("multi-file") ||
                lower.contains("full application") ||
                lower.contains("add tests") ||
                lower.contains("add junit") ||
                lower.contains("write tests") ||
                lower.contains("test coverage") ||
                lower.contains("implement") ||
                lower.contains("plan");
    }

    // ── Result record ────────────────────────────────────────────────

    public record HybridResult(String plan, String execution, boolean wasHybrid) {

        public String toDisplayString() {
            StringBuilder sb = new StringBuilder();
            if (wasHybrid && plan != null) {
                sb.append("PLAN:\n");
                sb.append(plan).append("\n\n");
                sb.append("EXECUTION:\n");
                sb.append(execution != null ? execution : "(no execution needed)");
            } else if (plan != null) {
                sb.append(plan);
            } else {
                sb.append(execution != null ? execution : "(no response)");
            }
            return sb.toString();
        }
    }
}
