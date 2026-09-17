package com.linkBuddy.service;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkBuddy.entity.Bookmark;
import com.linkBuddy.entity.BookmarkRequest;
import com.linkBuddy.entity.RequestStatus;
import com.linkBuddy.repository.BookmarkRepository;
import com.linkBuddy.repository.BookmarkRequestRepository;
import com.linkBuddy.service.RequestBroadcaster.BookmarkRequestEvent;

/**
 * Backs the admin approval workflow (/admin/requests) and the published-bookmark management
 * view (/admin/bookmarks), keeping the Bookmark table and its pgvector embeddings in sync.
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
        embed(bookmark);

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

    /**
     * Lets the requesting user withdraw their own request before an admin has acted on it. Marks
     * it CANCELLED rather than deleting the row, so it still shows up (as a distinct status) in
     * the user's own request history.
     */
    @Transactional
    public void cancel(Long requestId, String username) {
        BookmarkRequest request = bookmarkRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        if (!request.getRequestedBy().equals(username)) {
            throw new IllegalStateException("Cannot cancel another user's request");
        }
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be cancelled");
        }

        request.setStatus(RequestStatus.CANCELLED);
        bookmarkRequestRepository.save(request);
    }

    public List<Bookmark> listBookmarks() {
        return bookmarkRepository.findAll();
    }

    /**
     * Updates a published bookmark's fields and re-embeds it, so the agent's searchBookmark tool
     * picks up the edited title/description on future semantic searches instead of the stale one.
     */
    @Transactional
    public void updateBookmark(Long bookmarkId, String title, String url, String description, String category) {
        Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new IllegalArgumentException("Bookmark not found: " + bookmarkId));

        bookmark.setTitle(title);
        bookmark.setUrl(url);
        bookmark.setDescription(description);
        bookmark.setCategory(category);
        bookmarkRepository.save(bookmark);

        deleteEmbedding(bookmarkId);
        embed(bookmark);
    }

    @Transactional
    public void deleteBookmark(Long bookmarkId) {
        if (!bookmarkRepository.existsById(bookmarkId)) {
            throw new IllegalArgumentException("Bookmark not found: " + bookmarkId);
        }
        deleteEmbedding(bookmarkId);
        bookmarkRepository.deleteById(bookmarkId);
    }

    // Embeds (or re-embeds) a bookmark into the pgvector knowledge base so the agent's
    // searchBookmark tool can find it on future semantic searches. The bookmarkId metadata is
    // what lets deleteEmbedding find the matching vector row(s) again later.
    private void embed(Bookmark bookmark) {
        Document document = new Document(
                bookmark.getTitle() + " - " + bookmark.getDescription(),
                Map.of(
                        "bookmarkId", bookmark.getId(),
                        "title", bookmark.getTitle(),
                        "url", bookmark.getUrl(),
                        "category", bookmark.getCategory() == null ? "" : bookmark.getCategory(),
                        "description", bookmark.getDescription() == null ? "" : bookmark.getDescription()));
        vectorStore.add(List.of(document));
    }

    private void deleteEmbedding(Long bookmarkId) {
        Filter.Expression matchesBookmark = new FilterExpressionBuilder().eq("bookmarkId", bookmarkId).build();
        vectorStore.delete(matchesBookmark);
    }
}