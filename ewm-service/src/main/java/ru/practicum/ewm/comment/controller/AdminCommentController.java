package ru.practicum.ewm.comment.controller;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.service.CommentService;

import java.util.List;

@RestController
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
@Slf4j
@Validated
public class AdminCommentController {

    private final CommentService commentService;

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCommentByAdmin(@PathVariable Long commentId) {
        log.info("Получен запрос DELETE /admin/comments/{} (модерация)", commentId);
        commentService.deleteCommentByAdmin(commentId);
    }

    @GetMapping
    public List<CommentDto> searchCommentsByAdmin(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) List<Long> authors,
            @RequestParam(required = false) List<Long> events,
            @RequestParam(defaultValue = "0") @PositiveOrZero int from,
            @RequestParam(defaultValue = "10") @Positive int size) {
        log.info("Получен запрос GET /admin/comments (поиск для модерации)");
        return commentService.searchCommentsByAdmin(text, authors, events, from, size);
    }
}
