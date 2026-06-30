package ru.practicum.ewm.comment.repository;

import org.springframework.data.jpa.domain.Specification;
import ru.practicum.ewm.comment.model.Comment;

import java.util.List;

public class CommentSpecifications {

    public static Specification<Comment> hasText(String text) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("text")), "%" + text.toLowerCase() + "%");
    }

    public static Specification<Comment> hasAuthors(List<Long> authors) {
        return (root, query, cb) -> cb.in(root.get("author").get("id")).value(authors);
    }

    public static Specification<Comment> hasEvents(List<Long> events) {
        return (root, query, cb) -> cb.in(root.get("event").get("id")).value(events);
    }
}
