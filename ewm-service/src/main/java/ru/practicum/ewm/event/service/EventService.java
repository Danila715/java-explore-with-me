package ru.practicum.ewm.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.service.CategoryService;
import ru.practicum.ewm.event.dto.*;
import ru.practicum.ewm.event.mapper.EventMapper;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.event.repository.EventSpecifications;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.exception.ValidationException;
import ru.practicum.ewm.participation.model.ParticipationRequest;
import ru.practicum.ewm.participation.service.ParticipationRequestService;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.service.UserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final UserService userService;
    private final CategoryService categoryService;
    private final ParticipationRequestService requestService;

    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto dto) {
        if (dto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Дата события должна быть не ранее чем через 2 часа от текущего момента");
        }
        User user = userService.getUserById(userId);
        Category category = categoryService.getCategoryById(dto.getCategory());
        Event event = EventMapper.toEvent(dto, category, user);
        return EventMapper.toEventFullDto(eventRepository.save(event));
    }

    public List<EventShortDto> getUserEvents(Long userId, int from, int size) {
        userService.getUserById(userId);
        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findByInitiatorId(userId, pageable).getContent();
        enrichWithConfirmedRequests(events);
        return events.stream().map(EventMapper::toEventShortDto).collect(Collectors.toList());
    }

    public EventFullDto getUserEvent(Long userId, Long eventId) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));
        event.setConfirmedRequests(requestService.getConfirmedRequestsCount(eventId));
        return EventMapper.toEventFullDto(event);
    }

    @Transactional
    public EventFullDto updateEventByUser(Long userId, Long eventId, UpdateEventUserRequest dto) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Изменять можно только отменённые или ожидающие модерации события");
        }
        if (dto.getEventDate() != null && dto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Дата события должна быть не ранее чем через 2 часа от текущего момента");
        }

        applyUserUpdate(event, dto);

        if (dto.getStateAction() != null) {
            switch (dto.getStateAction()) {
                case SEND_TO_REVIEW -> event.setState(EventState.PENDING);
                case CANCEL_REVIEW -> event.setState(EventState.CANCELED);
            }
        }

        event.setConfirmedRequests(requestService.getConfirmedRequestsCount(eventId));
        return EventMapper.toEventFullDto(eventRepository.save(event));
    }

    public List<EventFullDto> getEventsByAdmin(List<Long> users, List<String> states,
                                               List<Long> categories, LocalDateTime rangeStart,
                                               LocalDateTime rangeEnd, int from, int size) {
        List<EventState> eventStates = states != null
                ? states.stream().map(EventState::valueOf).collect(Collectors.toList())
                : null;

        Specification<Event> spec = EventSpecifications.adminFilter(
                users, eventStates, categories, rangeStart, rangeEnd);

        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAll(spec, pageable).getContent();

        enrichWithConfirmedRequests(events);
        return events.stream().map(EventMapper::toEventFullDto).collect(Collectors.toList());
    }

    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest dto) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        if (dto.getEventDate() != null && dto.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
            throw new ValidationException("Дата события должна быть не ранее чем через 1 час от текущего момента");
        }

        applyAdminUpdate(event, dto);

        if (dto.getStateAction() != null) {
            switch (dto.getStateAction()) {
                case PUBLISH_EVENT:
                    if (event.getState() != EventState.PENDING) {
                        throw new ConflictException("Нельзя опубликовать событие в статусе: " + event.getState());
                    }
                    event.setState(EventState.PUBLISHED);
                    event.setPublishedOn(LocalDateTime.now());
                    break;
                case REJECT_EVENT:
                    if (event.getState() == EventState.PUBLISHED) {
                        throw new ConflictException("Нельзя отклонить уже опубликованное событие");
                    }
                    event.setState(EventState.CANCELED);
                    break;
            }
        }

        event.setConfirmedRequests(requestService.getConfirmedRequestsCount(eventId));
        return EventMapper.toEventFullDto(eventRepository.save(event));
    }

    public Event getEventById(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие под id" + eventId + " не найдено"));
    }

    private void enrichWithConfirmedRequests(List<Event> events) {
        if (events.isEmpty()) return;
        List<Long> ids = events.stream().map(Event::getId).collect(Collectors.toList());
        List<ParticipationRequest> requests = requestService.getConfirmedRequestsByEventIds(ids);
        Map<Long, Long> countMap = requests.stream()
                .collect(Collectors.groupingBy(r -> r.getEvent().getId(), Collectors.counting()));
        events.forEach(e -> e.setConfirmedRequests(countMap.getOrDefault(e.getId(), 0L)));
    }

    private void applyUserUpdate(Event event, UpdateEventUserRequest dto) {
        Optional.ofNullable(dto.getAnnotation()).ifPresent(event::setAnnotation);
        Optional.ofNullable(dto.getCategory()).ifPresent(id -> event.setCategory(categoryService.getCategoryById(id)));
        Optional.ofNullable(dto.getDescription()).ifPresent(event::setDescription);
        Optional.ofNullable(dto.getEventDate()).ifPresent(event::setEventDate);
        Optional.ofNullable(dto.getLocation()).ifPresent(event::setLocation);
        Optional.ofNullable(dto.getPaid()).ifPresent(event::setPaid);
        Optional.ofNullable(dto.getParticipantLimit()).ifPresent(event::setParticipantLimit);
        Optional.ofNullable(dto.getRequestModeration()).ifPresent(event::setRequestModeration);
        Optional.ofNullable(dto.getTitle()).ifPresent(event::setTitle);
    }

    private void applyAdminUpdate(Event event, UpdateEventAdminRequest dto) {
        Optional.ofNullable(dto.getAnnotation()).ifPresent(event::setAnnotation);
        Optional.ofNullable(dto.getCategory()).ifPresent(id -> event.setCategory(categoryService.getCategoryById(id)));
        Optional.ofNullable(dto.getDescription()).ifPresent(event::setDescription);
        Optional.ofNullable(dto.getEventDate()).ifPresent(event::setEventDate);
        Optional.ofNullable(dto.getLocation()).ifPresent(event::setLocation);
        Optional.ofNullable(dto.getPaid()).ifPresent(event::setPaid);
        Optional.ofNullable(dto.getParticipantLimit()).ifPresent(event::setParticipantLimit);
        Optional.ofNullable(dto.getRequestModeration()).ifPresent(event::setRequestModeration);
        Optional.ofNullable(dto.getTitle()).ifPresent(event::setTitle);
    }
}