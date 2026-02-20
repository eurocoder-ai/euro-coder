package eu.eurocoder.sovereigncli.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Loads benchmark task definitions from JSON files on the classpath
 * ({@code benchmarks/*.json}).
 */
@Component
public class TaskLoader {

    private static final Logger log = LoggerFactory.getLogger(TaskLoader.class);
    private static final String TASKS_PATTERN = "classpath:benchmarks/*.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<BenchmarkTask> loadAllTasks() {
        List<BenchmarkTask> tasks = new ArrayList<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(TASKS_PATTERN);

            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    BenchmarkTask task = objectMapper.readValue(is, BenchmarkTask.class);
                    tasks.add(task);
                } catch (IOException e) {
                    log.warn("Failed to load benchmark task from {}: {}",
                            resource.getFilename(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to scan benchmark tasks: {}", e.getMessage());
        }

        tasks.sort(Comparator.comparing(BenchmarkTask::category)
                .thenComparing(BenchmarkTask::id));
        return tasks;
    }

    public List<BenchmarkTask> loadByCategory(String category) {
        return loadAllTasks().stream()
                .filter(t -> t.category().equalsIgnoreCase(category))
                .toList();
    }

    public BenchmarkTask loadById(String id) {
        return loadAllTasks().stream()
                .filter(t -> t.id().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    public Map<String, List<BenchmarkTask>> loadGroupedByCategory() {
        return loadAllTasks().stream()
                .collect(Collectors.groupingBy(BenchmarkTask::category));
    }
}
