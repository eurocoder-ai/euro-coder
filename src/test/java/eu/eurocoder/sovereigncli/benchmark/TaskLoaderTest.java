package eu.eurocoder.sovereigncli.benchmark;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaskLoaderTest {

    private final TaskLoader taskLoader = new TaskLoader();

    @Test
    void loadAllTasks_returnsNonEmptyList() {
        List<BenchmarkTask> tasks = taskLoader.loadAllTasks();

        assertThat(tasks).isNotEmpty();
    }

    @Test
    void loadAllTasks_tasksHaveRequiredFields() {
        List<BenchmarkTask> tasks = taskLoader.loadAllTasks();

        for (BenchmarkTask task : tasks) {
            assertThat(task.id()).isNotBlank();
            assertThat(task.category()).isNotBlank();
            assertThat(task.description()).isNotBlank();
            assertThat(task.type()).isIn("raw", "agent");
            assertThat(task.prompt()).isNotBlank();
            assertThat(task.assertions()).isNotEmpty();
            assertThat(task.timeoutSeconds()).isGreaterThan(0);
        }
    }

    @Test
    void loadAllTasks_sortedByCategoryThenId() {
        List<BenchmarkTask> tasks = taskLoader.loadAllTasks();

        for (int i = 1; i < tasks.size(); i++) {
            BenchmarkTask prev = tasks.get(i - 1);
            BenchmarkTask curr = tasks.get(i);
            int catCompare = prev.category().compareTo(curr.category());
            if (catCompare == 0) {
                assertThat(prev.id()).isLessThanOrEqualTo(curr.id());
            } else {
                assertThat(catCompare).isLessThan(0);
            }
        }
    }

    @Test
    void loadByCategory_codeGeneration_returnsOnlyThatCategory() {
        List<BenchmarkTask> tasks = taskLoader.loadByCategory("code-generation");

        assertThat(tasks).isNotEmpty();
        assertThat(tasks).allMatch(t -> "code-generation".equals(t.category()));
    }

    @Test
    void loadByCategory_nonExistent_returnsEmpty() {
        List<BenchmarkTask> tasks = taskLoader.loadByCategory("nonexistent-category");

        assertThat(tasks).isEmpty();
    }

    @Test
    void loadById_existingId_returnsTask() {
        BenchmarkTask task = taskLoader.loadById("gen-fibonacci");

        assertThat(task).isNotNull();
        assertThat(task.id()).isEqualTo("gen-fibonacci");
        assertThat(task.category()).isEqualTo("code-generation");
        assertThat(task.isRaw()).isTrue();
    }

    @Test
    void loadById_nonExistentId_returnsNull() {
        BenchmarkTask task = taskLoader.loadById("no-such-task");

        assertThat(task).isNull();
    }

    @Test
    void loadGroupedByCategory_returnsAllCategories() {
        Map<String, List<BenchmarkTask>> grouped = taskLoader.loadGroupedByCategory();

        assertThat(grouped).containsKeys("code-generation", "debugging", "refactoring", "tool-calling");
    }

    @Test
    void rawTasks_haveNoSetup() {
        List<BenchmarkTask> rawTasks = taskLoader.loadAllTasks().stream()
                .filter(BenchmarkTask::isRaw)
                .toList();

        assertThat(rawTasks).isNotEmpty();
        for (BenchmarkTask task : rawTasks) {
            assertThat(task.setup()).isNullOrEmpty();
        }
    }

    @Test
    void agentTasks_haveSetupFiles() {
        List<BenchmarkTask> agentTasks = taskLoader.loadAllTasks().stream()
                .filter(BenchmarkTask::isAgent)
                .toList();

        assertThat(agentTasks).isNotEmpty();
        for (BenchmarkTask task : agentTasks) {
            assertThat(task.setup()).isNotEmpty();
        }
    }

    @Test
    void fibonacciTask_hasExpectedAssertions() {
        BenchmarkTask fib = taskLoader.loadById("gen-fibonacci");

        assertThat(fib.assertions()).hasSize(3);
        assertThat(fib.assertions()).anyMatch(a -> "response_contains".equals(a.type())
                && "fibonacci".equals(a.value()));
    }
}
