package com.linkBuddy.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A bookmark submitted by a user via the chat agent (or manually) that is awaiting admin review.
 */
@Entity
@Table(name = "bookmark_request")
@Getter
@Setter
@NoArgsConstructor
public class BookmarkRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String requestedBy;

    @Column(nullable = false)
    private String title;

    private String url;

    @Column(length = 2000)
    private String description;

    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.PENDING;

    @Column(length = 1000)
    private String rejectReason;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}