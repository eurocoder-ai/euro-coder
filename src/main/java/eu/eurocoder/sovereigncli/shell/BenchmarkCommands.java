package eu.eurocoder.sovereigncli.shell;

import eu.eurocoder.sovereigncli.agent.ModelManager;
import eu.eurocoder.sovereigncli.benchmark.BenchmarkReport;
import eu.eurocoder.sovereigncli.benchmark.BenchmarkResult;
import eu.eurocoder.sovereigncli.benchmark.BenchmarkRunner;
import eu.eurocoder.sovereigncli.benchmark.BenchmarkTask;
import eu.eurocoder.sovereigncli.benchmark.TaskLoader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;
import java.util.Map;

/**
 * Shell commands for the benchmarking framework:
 * {@code benchmark list}, {@code benchmark run}, {@code benchmark report}, {@code benchmark compare}.
 */
@ShellComponent
public class BenchmarkCommands {

    private static final int SEPARATOR_WIDTH = 60;
    private static final int COL_TASK_ID = 25;

    private final TaskLoader taskLoader;
    private final BenchmarkRunner runner;
    private final BenchmarkReport report;
    private final ModelManager modelManager;

    public BenchmarkCommands(TaskLoader taskLoader, BenchmarkRunner runner,
                             BenchmarkReport report, ModelManager modelManager) {
        this.taskLoader = taskLoader;
        this.runner = runner;
        this.report = report;
        this.modelManager = modelManager;
    }

    @ShellMethod(key = "benchmark list", value = "Show all available benchmark tasks grouped by category")
    public String benchmarkList() {
        Map<String, List<BenchmarkTask>> grouped = taskLoader.loadGroupedByCategory();
        if (grouped.isEmpty()) {
            return colorize("No benchmark tasks found.", AttributedStyle.YELLOW);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(colorize("  Available Benchmark Tasks\n", AttributedStyle.CYAN));
        sb.append("  ").append("=".repeat(SEPARATOR_WIDTH)).append("\n");

        for (Map.Entry<String, List<BenchmarkTask>> entry : grouped.entrySet()) {
            sb.append("\n  ").append(colorize(entry.getKey().toUpperCase(), AttributedStyle.GREEN)).append("\n");
            for (BenchmarkTask task : entry.getValue()) {
                String type = task.isRaw() ? "[raw]" : "[agent]";
                sb.append(String.format("    %-" + COL_TASK_ID + "s %-8s %s%n",
                        task.id(), type, task.description()));
            }
        }

        int total = grouped.values().stream().mapToInt(List::size).sum();
        sb.append("\n  Total: ").append(total).append(" tasks\n");
        return sb.toString();
    }

    @ShellMethod(key = "benchmark run", value = "Run benchmark tasks against the current or specified model")
    public String benchmarkRun(
            @ShellOption(defaultValue = "", help = "Filter by category (e.g. code-generation)") String category,
            @ShellOption(defaultValue = "", help = "Run a specific task by ID") String task,
            @ShellOption(defaultValue = "", help = "Model name (defaults to current coder model)") String model) {

        String modelName = model.isBlank() ? modelManager.getCoderModelName() : model;
        String provider = modelManager.getProvider().id();

        List<BenchmarkTask> tasks = resolveTasks(category, task);
        if (tasks.isEmpty()) {
            return colorize("No matching benchmark tasks found.", AttributedStyle.YELLOW);
        }

        System.out.println();
        System.out.println(colorize(String.format("  Running %d benchmark task(s) against %s (%s)...",
                tasks.size(), modelName, provider), AttributedStyle.CYAN));
        System.out.println();

        List<BenchmarkResult> results = runner.runTasks(tasks, modelName, result -> {
            String status = result.passed()
                    ? colorize("PASS", AttributedStyle.GREEN)
                    : colorize("FAIL", AttributedStyle.RED);
            System.out.printf("    %s  %-" + COL_TASK_ID + "s  %dms%n", status, result.taskId(), result.latencyMs());
        });

        report.saveResults(results, modelName, provider);

        return report.formatResults(results);
    }

    @ShellMethod(key = "benchmark report", value = "Show the latest benchmark run results")
    public String benchmarkReport() {
        BenchmarkReport.RunSnapshot latest = report.loadLatestRun();
        if (latest == null) {
            return colorize("No benchmark results found. Run 'benchmark run' first.", AttributedStyle.YELLOW);
        }
        return colorize("  Latest run: " + latest.filename() + "\n", AttributedStyle.CYAN)
                + report.formatResults(latest.results());
    }

    @ShellMethod(key = "benchmark compare", value = "Compare results across different benchmark runs")
    public String benchmarkCompare() {
        List<BenchmarkReport.RunSnapshot> runs = report.loadPreviousRuns();
        if (runs.isEmpty()) {
            return colorize("No benchmark runs to compare. Run 'benchmark run' first.", AttributedStyle.YELLOW);
        }
        return report.formatComparison(runs);
    }

    private List<BenchmarkTask> resolveTasks(String category, String taskId) {
        if (!taskId.isBlank()) {
            BenchmarkTask single = taskLoader.loadById(taskId);
            return single != null ? List.of(single) : List.of();
        }
        if (!category.isBlank()) {
            return taskLoader.loadByCategory(category);
        }
        return taskLoader.loadAllTasks();
    }

    private String colorize(String text, int color) {
        return new AttributedString(text,
                AttributedStyle.DEFAULT.foreground(color)).toAnsi();
    }
}
