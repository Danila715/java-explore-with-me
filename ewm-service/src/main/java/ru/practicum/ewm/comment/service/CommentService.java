package ru.practicum.ewm.comment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.dto.NewCommentDto;
import ru.practicum.ewm.comment.dto.UpdateCommentDto;
import ru.practicum.ewm.comment.mapper.CommentMapper;
import ru.practicum.ewm.comment.model.Comment;
import ru.practicum.ewm.comment.repository.CommentRepository;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    // ───────────────────── ПУБЛИЧНЫЕ ─────────────────────

    public List<CommentDto> getEventComments(Long eventId, int from, int size) {
        if (!eventRepository.existsById(eventId)) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }
        PageRequest pageable = PageRequest.of(from / size, size, Sort.by("createdOn").descending());
        return commentRepository.findByEventId(eventId, pageable)
                .map(CommentMapper::toDto)
                .getContent();
    }

    public CommentDto getComment(Long commentId) {
        return CommentMapper.toDto(getCommentOrThrow(commentId));
    }

    // ───────────────────── ПРИВАТНЫЕ (пользовательские) ─────────────────────

    @Transactional
    public CommentDto addComment(Long userId, Long eventId, NewCommentDto dto) {
        User author = getUserOrThrow(userId);
        Event event = getPublishedEventOrThrow(eventId);

        Comment comment = CommentMapper.toComment(dto.getText(), author, event);
        log.info("Пользователь {} добавил комментарий к событию {}", userId, eventId);
        return CommentMapper.toDto(commentRepository.save(comment));
    }

    @Transactional
    public CommentDto updateComment(Long userId, Long commentId, UpdateCommentDto dto) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }
        Comment comment = getCommentOrThrow(commentId);

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ConflictException("Редактировать комментарий может только его автор");
        }

        comment.setText(dto.getText());
        comment.setEdited(true);
        comment.setEditedOn(LocalDateTime.now());

        log.info("Пользователь {} отредактировал комментарий {}", userId, commentId);
        return CommentMapper.toDto(commentRepository.save(comment));
    }

    @Transactional
    public void deleteCommentByUser(Long userId, Long commentId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }
        Comment comment = getCommentOrThrow(commentId);

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ConflictException("Удалить комментарий может только его автор");
        }

        commentRepository.delete(comment);
        log.info("Пользователь {} удалил комментарий {}", userId, commentId);
    }

    public List<CommentDto> getUserComments(Long userId, int from, int size) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }
        PageRequest pageable = PageRequest.of(from / size, size, Sort.by("createdOn").descending());
        return commentRepository.findByAuthorId(userId, pageable)
                .map(CommentMapper::toDto)
                .getContent();
    }

    // ───────────────────── АДМИНСКИЕ ─────────────────────

    @Transactional
    public void deleteCommentByAdmin(Long commentId) {
        if (!commentRepository.existsById(commentId)) {
            throw new NotFoundException("Комментарий с id=" + commentId + " не найден");
        }
        commentRepository.deleteById(commentId);
        log.info("Администратор удалил комментарий {}", commentId);
    }

    public List<CommentDto> getEventCommentsByAdmin(Long eventId, int from, int size) {
        if (!eventRepository.existsById(eventId)) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }
        PageRequest pageable = PageRequest.of(from / size, size, Sort.by("createdOn").descending());
        return commentRepository.findByEventId(eventId, pageable)
                .map(CommentMapper::toDto)
                .getContent();
    }

    // ───────────────────── Вспомогательные ─────────────────────

    private Comment getCommentOrThrow(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Комментарий с id=" + commentId + " не найден"));
    }

    private Event getPublishedEventOrThrow(Long eventId) {
        return eventRepository.findByIdAndState(eventId, EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Опубликованное событие с id=" + eventId + " не найдено"));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));
    }
}