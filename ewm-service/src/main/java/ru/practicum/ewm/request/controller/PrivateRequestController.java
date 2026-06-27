package ru.practicum.ewm.request.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.request.dto.ParticipationRequestDto;
import ru.practicum.ewm.request.service.ParticipationRequestService;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}/requests")
@RequiredArgsConstructor
@Slf4j
public class PrivateRequestController {

    private final ParticipationRequestService requestService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipationRequestDto addRequest(
            @PathVariable Long userId,
            @RequestParam Long eventId) {
        log.info("Получен запрос POST /users/{}/requests (eventId={})", userId, eventId);
        return requestService.addRequest(userId, eventId);
    }

    @GetMapping
    public List<ParticipationRequestDto> getUserRequests(@PathVariable Long userId) {
        log.info("Получен запрос GET /users/{}/requests", userId);
        return requestService.getUserRequests(userId);
    }

    @PatchMapping("/{requestId}/cancel")
    public ParticipationRequestDto cancelRequest(
            @PathVariable Long userId,
            @PathVariable Long requestId) {
        log.info("Получен запрос PATCH /users/{}/requests/{}/cancel", userId, requestId);
        return requestService.cancelRequest(userId, requestId);
    }
}
