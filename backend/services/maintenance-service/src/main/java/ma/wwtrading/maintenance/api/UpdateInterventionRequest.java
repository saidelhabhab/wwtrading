package ma.wwtrading.maintenance.api;

public record UpdateInterventionRequest(
    String diagnosis,
    String interventionNotes
) {}

