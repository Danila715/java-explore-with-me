package ru.practicum.ewm.comment.mapper;

import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.model.Comment;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.user.model.User;

import java.time.LocalDateTime;

public class CommentMapper {

    public static Comment toComment(String text, User author, Event event) {
        return Comment.builder()
                .text(text)
                .author(author)
                .event(event)
                .createdOn(LocalDateTime.now())
                .edited(false)
                .build();
    }

    public static CommentDto toDto(Comment comment) {
        return CommentDto.builder()
                .id(comment.getId())
                .text(comment.getText())
                .authorName(comment.getAuthor().getName())
                .eventId(comment.getEvent().getId())
                .edited(comment.isEdited())
                .createdOn(comment.getCreatedOn())
                .editedOn(comment.getEditedOn())
                .build();
    }
}