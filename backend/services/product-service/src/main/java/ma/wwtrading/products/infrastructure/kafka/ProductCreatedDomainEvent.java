package ma.wwtrading.products.infrastructure.kafka;

import ma.wwtrading.events.DomainEvent;
import ma.wwtrading.events.EventPayloads;

public record ProductCreatedDomainEvent(DomainEvent<EventPayloads.ProductCreated> event) {

}
