package ru.practicum.ewm.event.repository;

import org.springframework.data.jpa.domain.Specification;
import ru.practicum.ewm.event.state.EventState;
import ru.practicum.ewm.event.model.Event;

import java.time.LocalDateTime;
import java.util.List;

public class EventSpecifications {

    public static Specification<Event> hasInitiators(List<Long> initiators) {
        return (root, query, cb) -> cb.in(root.get("initiator").get("id")).value(initiators);
    }

    public static Specification<Event> hasStates(List<EventState> states) {
        return (root, query, cb) -> cb.in(root.get("state")).value(states);
    }

    public static Specification<Event> hasCategories(List<Long> categories) {
        return (root, query, cb) -> cb.in(root.get("category").get("id")).value(categories);
    }

    public static Specification<Event> isWithinDates(LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> cb.between(root.get("eventDate"), start, end);
    }

    public static Specification<Event> hasText(String text) {
        return (root, query, cb) -> {
            String pattern = "%" + text.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("annotation")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            );
        };
    }

    public static Specification<Event> isPaid(Boolean paid) {
        return (root, query, cb) -> cb.equal(root.get("paid"), paid);
    }
}
