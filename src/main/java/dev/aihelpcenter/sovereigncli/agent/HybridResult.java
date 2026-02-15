package dev.aihelpcenter.sovereigncli.agent;

/**
 * The result of a routed agent request, which may include a plan and/or an execution.
 *
 * @param plan       the plan produced by the Planner agent (may be {@code null})
 * @param execution  the output produced by the Coder/Direct agent (may be {@code null})
 * @param wasHybrid  {@code true} if the request went through the Planner → Coder pipeline
 */
public record HybridResult(String plan, String execution, boolean wasHybrid) {

    /** Formats the result for display in the shell. */
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
