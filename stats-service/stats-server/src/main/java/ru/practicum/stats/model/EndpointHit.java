package ru.practicum.stats.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "endpoint_hits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointHit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app", nullable = false, length = 255)
    private String app;

    @Column(name = "uri", nullable = false, length = 512)
    private String uri;

    @Column(name = "ip", nullable = false, length = 45)
    private String ip;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
}