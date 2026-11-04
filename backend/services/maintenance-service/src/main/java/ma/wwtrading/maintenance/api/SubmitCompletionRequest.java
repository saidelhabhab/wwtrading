package ma.wwtrading.maintenance.api;

public record SubmitCompletionRequest(
    String completionSummary,
    String interventionNotes
) {}

