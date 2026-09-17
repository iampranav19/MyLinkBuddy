package com.linkBuddy.service;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkBuddy.entity.Bookmark;
import com.linkBuddy.entity.BookmarkRequest;
import com.linkBuddy.entity.RequestStatus;
import com.linkBuddy.repository.BookmarkRepository;
import com.linkBuddy.repository.BookmarkRequestRepository;
import com.linkBuddy.service.RequestBroadcaster.BookmarkRequestEvent;

/**
 * Backs the admin approval workflow (/admin/requests).
 */
@Service
public class AdminRequestService {

    private final BookmarkRequestRepository bookmarkRequestRepository;
    private final BookmarkRepository bookmarkRepository;
    private final VectorStore vectorStore;
    private final RequestBroadcaster broadcaster;

    public AdminRequestService(BookmarkRequestRepository bookmarkRequestRepository,
            BookmarkRepository bookmarkRepository, VectorStore vectorStore, RequestBroadcaster broadcaster) {
        this.bookmarkRequestRepository = bookmarkRequestRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.vectorStore = vectorStore;
        this.broadcaster = broadcaster;
    }

    /**
     * Approves a request using its fields as submitted (no admin edits).
     */
    @Transactional
    public void approve(Long requestId) {
        BookmarkRequest request = bookmarkRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));
        approve(requestId, request.getTitle(), request.getUrl(), request.getDescription(), request.getCategory());
    }

    /**
     * Approves a request, letting the admin fill in or correct any field first - most commonly
     * the URL, when the requesting user didn't know it. The corrected values are saved back onto
     * the request (so the requester's history reflects what was actually published) and used for
     * the new Bookmark + its pgvector embedding.
     */
    @Transactional
    public void approve(Long requestId, String title, String url, String description, String category) {
        BookmarkRequest request = bookmarkRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        request.setTitle(title);
        request.setUrl(url);
        request.setDescription(description);
        request.setCategory(category);

        Bookmark bookmark = new Bookmark();
        bookmark.setTitle(title);
        bookmark.setUrl(url);
        bookmark.setDescription(description);
        bookmark.setCategory(category);
        bookmarkRepository.save(bookmark);

        // Embed the newly approved bookmark into the pgvector knowledge base so the agent's
        // searchBookmark tool can find it on future semantic searches.
        Document document = new Document(
                bookmark.getTitle() + " - " + bookmark.getDescription(),
                Map.of(
                        "bookmarkId", bookmark.getId(),
                        "title", bookmark.getTitle(),
                        "url", bookmark.getUrl(),
                        "category", bookmark.getCategory() == null ? "" : bookmark.getCategory(),
                        "description", bookmark.getDescription() == null ? "" : bookmark.getDescription()));
        vectorStore.add(List.of(document));

        request.setStatus(RequestStatus.APPROVED);
        bookmarkRequestRepository.save(request);

        broadcaster.broadcast(new BookmarkRequestEvent(
                request.getRequestedBy(), request.getId(), request.getTitle(), RequestStatus.APPROVED));
    }

    @Transactional
    public void reject(Long requestId, String reason) {
        BookmarkRequest request = bookmarkRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        request.setStatus(RequestStatus.REJECTED);
        request.setRejectReason(reason);
        bookmarkRequestRepository.save(request);

        broadcaster.broadcast(new BookmarkRequestEvent(
                request.getRequestedBy(), request.getId(), request.getTitle(), RequestStatus.REJECTED));
    }
}