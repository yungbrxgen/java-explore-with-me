package ru.practicum.ewm.event.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.event.mapper.EventMapper;
import ru.practicum.ewm.event.state.EventState;
import ru.practicum.ewm.event.state.StateActionAdmin;
import ru.practicum.ewm.event.state.StateActionUser;
import ru.practicum.ewm.event.dto.*;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.event.repository.EventSpecifications;
import ru.practicum.ewm.exception.BadRequestException;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.location.dto.LocationDto;
import ru.practicum.ewm.location.model.Location;
import ru.practicum.ewm.location.repository.LocationRepository;
import ru.practicum.ewm.request.RequestStatus;
import ru.practicum.ewm.request.repository.ParticipationRequestRepository;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;
import ru.practicum.stats.client.StatsClient;
import ru.practicum.stats.dto.EndpointHitDto;
import ru.practicum.stats.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final EventMapper eventMapper;
    private final StatsClient statsClient;
    private final ParticipationRequestRepository requestRepository;

    @Override
    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto newEventDto) {
        log.info("Создание события пользователем id={}: {}", userId, newEventDto.getTitle());

        if (newEventDto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new BadRequestException("Field: eventDate. Error: должно содержать дату, которая еще не наступила. Value: " + newEventDto.getEventDate());
        }

        User initiator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
        Category category = categoryRepository.findById(newEventDto.getCategory())
                .orElseThrow(() -> new NotFoundException("Category with id=" + newEventDto.getCategory() + " was not found"));

        Location location = Location.builder()
                .lat(newEventDto.getLocation().getLat())
                .lon(newEventDto.getLocation().getLon())
                .build();
        Location savedLocation = locationRepository.save(location);

        Event event = eventMapper.toEvent(newEventDto);
        event.setInitiator(initiator);
        event.setCategory(category);
        event.setLocation(savedLocation);
        event.setState(EventState.PENDING);
        event.setCreatedOn(LocalDateTime.now());

        Event savedEvent = eventRepository.save(event);
        return eventMapper.toEventFullDto(savedEvent, 0L, 0L);
    }

    @Override
    public List<EventShortDto> getEventsByUserId(Long userId, int from, int size) {
        log.info("Получение событий пользователя id={} (from={}, size={})", userId, from, size);
        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findByInitiatorId(userId, pageable);

        return events.stream()
                .map(event -> eventMapper.toEventShortDto(event, getConfirmedRequests(event.getId()), getViews(event.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public EventFullDto getEventByIdAndUserId(Long userId, Long eventId) {
        log.info("Получение детальной информации о событии id={} пользователя id={}", eventId, userId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        return eventMapper.toEventFullDto(event, getConfirmedRequests(eventId), getViews(eventId));
    }

    @Override
    @Transactional
    public EventFullDto updateEventByUserId(Long userId, Long eventId, UpdateEventUserRequest request) {
        log.info("Обновление события id={} пользователем id={}", eventId, userId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }

        if (request.getEventDate() != null) {
            if (request.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
                throw new ru.practicum.ewm.exception.BadRequestException("Event date must be at least 2 hours in the future");
            }
            event.setEventDate(request.getEventDate());
        }

        updateEventCommonFields(event, request.getAnnotation(), request.getCategory(), request.getDescription(),
                request.getLocation(), request.getPaid(), request.getParticipantLimit(), request.getRequestModeration(),
                request.getTitle());

        if (request.getStateAction() != null) {
            if (request.getStateAction() == StateActionUser.SEND_TO_REVIEW) {
                event.setState(EventState.PENDING);
            } else if (request.getStateAction() == StateActionUser.CANCEL_REVIEW) {
                event.setState(EventState.CANCELED);
            }
        }

        Event updatedEvent = eventRepository.save(event);
        return eventMapper.toEventFullDto(updatedEvent, getConfirmedRequests(eventId), getViews(eventId));
    }


    @Override
    public List<EventFullDto> getEventsByAdmin(List<Long> users, List<String> states, List<Long> categories,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd, int from, int size) {
        log.info("Поиск событий администратором");
        Pageable pageable = PageRequest.of(from / size, size);
        Specification<Event> spec = Specification.where(null);

        if (users != null && !users.isEmpty()) {
            spec = spec.and(EventSpecifications.hasInitiators(users));
        }
        if (states != null && !states.isEmpty()) {
            List<EventState> eventStates = states.stream().map(EventState::valueOf).collect(Collectors.toList());
            spec = spec.and(EventSpecifications.hasStates(eventStates));
        }
        if (categories != null && !categories.isEmpty()) {
            spec = spec.and(EventSpecifications.hasCategories(categories));
        }

        if (rangeStart != null && rangeEnd != null) {
            if (rangeStart.isAfter(rangeEnd)) {
                throw new ru.practicum.ewm.exception.BadRequestException("rangeStart cannot be after rangeEnd");
            }
            spec = spec.and(EventSpecifications.isWithinDates(rangeStart, rangeEnd));
        } else {
            if (rangeStart != null) {
                spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("eventDate"), rangeStart));
            }
            if (rangeEnd != null) {
                spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("eventDate"), rangeEnd));
            }
        }

        List<Event> events = eventRepository.findAll(spec, pageable).getContent();
        return events.stream()
                .map(event -> eventMapper.toEventFullDto(event, getConfirmedRequests(event.getId()), getViews(event.getId())))
                .collect(Collectors.toList());
    }


    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest request) {
        log.info("Редактирование события id={} администратором", eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        if (request.getEventDate() != null) {
            if (request.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                throw new ConflictException("Event date must be at least 1 hour in the future for admin");
            }
            event.setEventDate(request.getEventDate());
        }

        updateEventCommonFields(event, request.getAnnotation(), request.getCategory(), request.getDescription(),
                request.getLocation(), request.getPaid(), request.getParticipantLimit(), request.getRequestModeration(),
                request.getTitle());

        if (request.getStateAction() != null) {
            if (request.getStateAction() == StateActionAdmin.PUBLISH_EVENT) {
                if (event.getState() != EventState.PENDING) {
                    throw new ConflictException("Cannot publish the event because it's not in the PENDING state: " + event.getState());
                }
                if (event.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                    throw new ConflictException("The event date must be at least 1 hour after the publication date");
                }
                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
            } else if (request.getStateAction() == StateActionAdmin.REJECT_EVENT) {
                if (event.getState() == EventState.PUBLISHED) {
                    throw new ConflictException("Cannot reject published event");
                }
                event.setState(EventState.CANCELED);
            }
        }

        Event updatedEvent = eventRepository.save(event);
        return eventMapper.toEventFullDto(updatedEvent, getConfirmedRequests(eventId), getViews(eventId));
    }


    @Override
    public List<EventShortDto> getEventsPublic(String text, List<Long> categories, Boolean paid,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd, Boolean onlyAvailable,
                                               String sort, int from, int size, HttpServletRequest request) {
        log.info("Публичный поиск событий");

        sendHit(request);

        Pageable pageable = PageRequest.of(from / size, size);
        Specification<Event> spec = Specification.where(EventSpecifications.hasStates(List.of(EventState.PUBLISHED)));

        if (text != null && !text.isBlank()) {
            spec = spec.and(EventSpecifications.hasText(text));
        }
        if (categories != null && !categories.isEmpty()) {
            spec = spec.and(EventSpecifications.hasCategories(categories));
        }
        if (paid != null) {
            spec = spec.and(EventSpecifications.isPaid(paid));
        }

        LocalDateTime start = (rangeStart != null) ? rangeStart : LocalDateTime.now();
        LocalDateTime end = (rangeEnd != null) ? rangeEnd : LocalDateTime.now().plusYears(100);

        if (start.isAfter(end)) {
            throw new BadRequestException("rangeStart is after rangeEnd");
        }
        spec = spec.and(EventSpecifications.isWithinDates(start, end));

        List<Event> events = eventRepository.findAll(spec, pageable).getContent();

        if (onlyAvailable != null && onlyAvailable) {
            events = events.stream()
                    .filter(event -> event.getParticipantLimit() == 0 ||
                            getConfirmedRequests(event.getId()) < event.getParticipantLimit())
                    .collect(Collectors.toList());
        }

        List<EventShortDto> dtos = events.stream()
                .map(event -> eventMapper.toEventShortDto(event, getConfirmedRequests(event.getId()), getViews(event.getId())))
                .collect(Collectors.toList());

        if ("EVENT_DATE".equals(sort)) {
            dtos.sort(Comparator.comparing(EventShortDto::getEventDate));
        } else if ("VIEWS".equals(sort)) {
            dtos.sort(Comparator.comparing(EventShortDto::getViews).reversed());
        }

        return dtos;
    }

    @Override
    public EventFullDto getEventByIdPublic(Long id, HttpServletRequest request) {
        log.info("Публичный просмотр подробной информации о событии id={}", id);
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event with id=" + id + " was not found"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event with id=" + id + " is not published yet");
        }

        sendHit(request);

        return eventMapper.toEventFullDto(event, getConfirmedRequests(id), getViews(id));
    }

    private void updateEventCommonFields(Event event, String annotation, Long catId, String description,
                                         LocationDto locationDto, Boolean paid, Integer participantLimit,
                                         Boolean requestModeration, String title) {
        if (annotation != null && !annotation.isBlank()) event.setAnnotation(annotation);
        if (catId != null) {
            Category category = categoryRepository.findById(catId)
                    .orElseThrow(() -> new NotFoundException("Category with id=" + catId + " was not found"));
            event.setCategory(category);
        }
        if (description != null && !description.isBlank()) event.setDescription(description);
        if (locationDto != null) {
            Location location = locationRepository.findById(event.getLocation().getId()).orElse(new Location());
            location.setLat(locationDto.getLat());
            location.setLon(locationDto.getLon());
            locationRepository.save(location);
        }
        if (paid != null) event.setPaid(paid);
        if (participantLimit != null) event.setParticipantLimit(participantLimit);
        if (requestModeration != null) event.setRequestModeration(requestModeration);
        if (title != null && !title.isBlank()) event.setTitle(title);
    }

    private Long getConfirmedRequests(Long eventId) {
        return requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
    }

    private Long getViews(Long eventId) {
        LocalDateTime start = LocalDateTime.now().minusYears(10);
        LocalDateTime end = LocalDateTime.now().plusYears(10);
        String uri = "/events/" + eventId;

        List<ViewStatsDto> stats = statsClient.getStats(start, end, List.of(uri), true);
        if (stats == null || stats.isEmpty()) {
            return 0L;
        }
        return stats.get(0).getHits();
    }

     private void sendHit(HttpServletRequest request) {
        EndpointHitDto hitDto = EndpointHitDto.builder()
                .app("ewm-service")
                .uri(request.getRequestURI())
                .ip(request.getRemoteAddr())
                .timestamp(LocalDateTime.now())
                .build();
        statsClient.saveHit(hitDto);
    }
}