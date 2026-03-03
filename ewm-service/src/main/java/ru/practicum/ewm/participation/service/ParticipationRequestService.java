package ru.practicum.ewm.participation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.participation.dto.EventRequestStatusUpdateRequest;
import ru.practicum.ewm.participation.dto.EventRequestStatusUpdateResult;
import ru.practicum.ewm.participation.dto.ParticipationRequestDto;
import ru.practicum.ewm.participation.mapper.ParticipationRequestMapper;
import ru.practicum.ewm.participation.model.ParticipationRequest;
import ru.practicum.ewm.participation.model.RequestStatus;
import ru.practicum.ewm.participation.repository.ParticipationRequestRepository;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParticipationRequestService {

    private final ParticipationRequestRepository requestRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        getUserOrThrow(userId);
        return requestRepository.findByRequesterId(userId).stream()
                .map(ParticipationRequestMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ParticipationRequestDto addParticipationRequest(Long userId, Long eventId) {
        User user = getUserOrThrow(userId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        if (requestRepository.existsByEventIdAndRequesterId(eventId, userId)) {
            throw new ConflictException("Заявка на участие уже существует");
        }
        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Организатор не может подать заявку на участие в собственном событии");
        }
        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Нельзя участвовать в неопубликованном событии");
        }
        if (event.getParticipantLimit() > 0) {
            long confirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
            if (confirmed >= event.getParticipantLimit()) {
                throw new ConflictException("Достигнут лимит участников");
            }
        }

        RequestStatus status = (!event.getRequestModeration() || event.getParticipantLimit() == 0)
                ? RequestStatus.CONFIRMED
                : RequestStatus.PENDING;

        ParticipationRequest request = ParticipationRequest.builder()
                .event(event)
                .requester(user)
                .created(LocalDateTime.now())
                .status(status)
                .build();

        return ParticipationRequestMapper.toDto(requestRepository.save(request));
    }

    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        ParticipationRequest request = requestRepository.findByIdAndRequesterId(requestId, userId)
                .orElseThrow(() -> new NotFoundException("Заявка с id=" + requestId + " не найдена"));
        request.setStatus(RequestStatus.CANCELED);
        return ParticipationRequestMapper.toDto(requestRepository.save(request));
    }

    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));
        return requestRepository.findByEventId(eventId).stream()
                .map(ParticipationRequestMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public EventRequestStatusUpdateResult changeRequestStatus(Long userId, Long eventId,
                                                              EventRequestStatusUpdateRequest updateRequest) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        List<ParticipationRequest> requests = requestRepository.findByIdInAndEventId(
                updateRequest.getRequestIds(), eventId);

        boolean hasNonPending = requests.stream()
                .anyMatch(r -> r.getStatus() != RequestStatus.PENDING);
        if (hasNonPending) {
            throw new ConflictException("Заявка должна иметь статус PENDING");
        }

        List<ParticipationRequestDto> confirmed = new ArrayList<>();
        List<ParticipationRequestDto> rejected = new ArrayList<>();

        String newStatus = updateRequest.getStatus();

        if ("CONFIRMED".equals(newStatus)) {
            long alreadyConfirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
            int limit = event.getParticipantLimit();

            List<ParticipationRequest> toConfirm = new ArrayList<>();
            List<ParticipationRequest> toReject = new ArrayList<>();

            for (ParticipationRequest req : requests) {
                if (limit > 0 && alreadyConfirmed >= limit) {
                    req.setStatus(RequestStatus.REJECTED);
                    toReject.add(req);
                } else {
                    req.setStatus(RequestStatus.CONFIRMED);
                    toConfirm.add(req);
                    alreadyConfirmed++;
                }
            }

            requestRepository.saveAll(toConfirm);
            requestRepository.saveAll(toReject);

            toConfirm.stream().map(ParticipationRequestMapper::toDto).forEach(confirmed::add);
            toReject.stream().map(ParticipationRequestMapper::toDto).forEach(rejected::add);

            if (limit > 0 && alreadyConfirmed >= limit) {
                List<ParticipationRequest> pending = requestRepository.findByEventId(eventId).stream()
                        .filter(r -> r.getStatus() == RequestStatus.PENDING)
                        .collect(Collectors.toList());
                pending.forEach(r -> r.setStatus(RequestStatus.REJECTED));
                requestRepository.saveAll(pending);
            }
        } else {
            requests.forEach(req -> req.setStatus(RequestStatus.REJECTED));
            requestRepository.saveAll(requests);
            requests.stream().map(ParticipationRequestMapper::toDto).forEach(rejected::add);
        }

        return EventRequestStatusUpdateResult.builder()
                .confirmedRequests(confirmed)
                .rejectedRequests(rejected)
                .build();
    }

    // Вспомогательный метод для получения кол-ва подтверждённых заявок по событию
    public long getConfirmedRequestsCount(Long eventId) {
        return requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
    }

    public List<ParticipationRequest> getConfirmedRequestsByEventIds(List<Long> eventIds) {
        return requestRepository.findByEventIdIn(eventIds).stream()
                .filter(r -> r.getStatus() == RequestStatus.CONFIRMED)
                .collect(Collectors.toList());
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));
    }
}