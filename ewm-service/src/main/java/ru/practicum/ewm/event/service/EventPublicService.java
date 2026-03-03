package ru.practicum.ewm.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.client.StatsClient;
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.dto.EventShortDto;
import ru.practicum.ewm.event.mapper.EventMapper;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.event.repository.EventSpecifications;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.exception.ValidationException;
import ru.practicum.ewm.participation.model.ParticipationRequest;
import ru.practicum.ewm.participation.service.ParticipationRequestService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventPublicService {

    private final EventRepository eventRepository;
    private final StatsClient statsClient;
    private final ParticipationRequestService requestService;

    public List<EventShortDto> getPublicEvents(String text, List<Long> categories, Boolean paid,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                               boolean onlyAvailable, String sort,
                                               int from, int size, String clientIp) {
        if (rangeStart == null) {
            rangeStart = LocalDateTime.now();
        }

        if (rangeEnd != null && rangeEnd.isBefore(rangeStart)) {
            throw new ValidationException("rangeEnd не может быть раньше rangeStart");
        }

        Specification<Event> spec = EventSpecifications.publicFilter(
                text, categories, paid, rangeStart, rangeEnd);

        Pageable pageable = PageRequest.of(from / size, size);

        List<Event> events = new ArrayList<>(eventRepository.findAll(spec, pageable).getContent());

        enrichEventsWithConfirmedRequests(events);

        if (onlyAvailable) {
            events = events.stream()
                    .filter(e -> e.getParticipantLimit() == 0 ||
                            e.getConfirmedRequests() < e.getParticipantLimit())
                    .collect(Collectors.toList());
        }

        enrichEventsWithStats(events);

        if ("VIEWS".equalsIgnoreCase(sort)) {
            events.sort(Comparator.comparingLong(
                    (Event e) -> e.getViews() != null ? e.getViews() : 0L).reversed());
        } else {
            events.sort(Comparator.comparing(Event::getEventDate));
        }

        saveHit("/events", clientIp);

        return events.stream().map(EventMapper::toEventShortDto).collect(Collectors.toList());
    }

    public EventFullDto getPublicEvent(Long eventId, String clientIp) {
        Event event = eventRepository.findByIdAndState(eventId, EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        event.setConfirmedRequests(requestService.getConfirmedRequestsCount(eventId));

        saveHit("/events/" + eventId, clientIp);

        enrichEventWithStats(event);

        return EventMapper.toEventFullDto(event);
    }

    private void enrichEventsWithConfirmedRequests(List<Event> events) {
        if (events.isEmpty()) return;
        List<Long> ids = events.stream().map(Event::getId).collect(Collectors.toList());
        List<ParticipationRequest> requests = requestService.getConfirmedRequestsByEventIds(ids);
        Map<Long, Long> countMap = requests.stream()
                .collect(Collectors.groupingBy(r -> r.getEvent().getId(), Collectors.counting()));
        events.forEach(e -> e.setConfirmedRequests(countMap.getOrDefault(e.getId(), 0L)));
    }

    private void enrichEventsWithStats(List<Event> events) {
        if (events.isEmpty()) return;
        List<String> uris = events.stream()
                .map(e -> "/events/" + e.getId())
                .collect(Collectors.toList());
        try {
            Map<String, Long> stats = statsClient.getViewsForUris(
                    uris, LocalDateTime.of(2000, 1, 1, 0, 0), LocalDateTime.now());
            events.forEach(e -> e.setViews(stats.getOrDefault("/events/" + e.getId(), 0L)));
        } catch (Throwable t) {
            log.warn("Сервис статистики недоступен, устанавливаем views=0 для всех событий: {}", t.getMessage());
            events.forEach(ev -> ev.setViews(0L));
        }
    }

    private void enrichEventWithStats(Event event) {
        try {
            Long views = statsClient.getViewsForUri(
                    "/events/" + event.getId(),
                    LocalDateTime.of(2000, 1, 1, 0, 0), LocalDateTime.now());
            event.setViews(views);
        } catch (Throwable t) {
            log.warn("Сервис статистики недоступен для события {}: {}", event.getId(), t.getMessage());
            event.setViews(0L);
        }
    }

    private void saveHit(String uri, String clientIp) {
        try {
            statsClient.saveHit("ewm-main-service", uri, clientIp, LocalDateTime.now());
        } catch (Throwable t) {
            log.warn("Не удалось сохранить hit для {}: {}", uri, t.getMessage());
        }
    }
}
