package eu.eurocoder.sovereigncli.agent;

public record HybridResult(String plan, String execution, boolean wasHybrid) {

    private static final String NO_EXECUTION = "(no execution needed)";
    private static final String NO_RESPONSE = "(no response)";

    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();
        if (wasHybrid && plan != null) {
            sb.append("PLAN:\n");
            sb.append(plan).append("\n\n");
            sb.append("EXECUTION:\n");
            sb.append(execution != null ? execution : NO_EXECUTION);
        } else if (plan != null) {
            sb.append(plan);
        } else {
            sb.append(execution != null ? execution : NO_RESPONSE);
        }
        return sb.toString();
    }
}
