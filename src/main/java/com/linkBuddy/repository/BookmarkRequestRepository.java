package com.linkBuddy.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkBuddy.entity.BookmarkRequest;
import com.linkBuddy.entity.RequestStatus;

public interface BookmarkRequestRepository extends JpaRepository<BookmarkRequest, Long> {

    List<BookmarkRequest> findByStatusOrderByCreatedAtDesc(RequestStatus status);

    List<BookmarkRequest> findByRequestedByOrderByCreatedAtDesc(String requestedBy);
}