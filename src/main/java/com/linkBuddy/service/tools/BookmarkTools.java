package com.linkBuddy.service.tools;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import com.linkBuddy.entity.Bookmark;
import com.linkBuddy.entity.BookmarkRequest;
import com.linkBuddy.entity.RequestStatus;
import com.linkBuddy.repository.BookmarkRepository;
import com.linkBuddy.repository.BookmarkRequestRepository;

/**
 * Tool functions exposed to the ChatClient. The agent decides on its own, based on the
 * system prompt in AgentService, when to call these:
 *
 *   1. searchBookmark          - lookup for a SPECIFIC bookmark the user describes: first tries a
 *                                direct/keyword match against the Bookmark table (substring
 *                                containment, then shared non-stopword tokens against title +
 *                                description), then falls back to pgvector semantic search. The
 *                                keyword pass matters because short/exact queries (a bare title or
 *                                acronym) and word-for-word paraphrases don't reliably score above
 *                                the embedding similarity threshold on their own. Not reliable for
 *                                "list everything in category X" style questions, since it only
 *                                returns a similarity-ranked top-K, not an exhaustive listing.
 *   2. listBookmarksByCategory - exact/structured listing of every approved bookmark in a given
 *                                category, read straight from the Bookmark table (no embeddings
 *                                involved), for "what's available under X" style questions.
 *   3. listCategories          - the distinct category names that currently have bookmarks, so
 *                                the agent can suggest the closest real category if the user's
 *                                wording doesn't match one exactly.
 *   4. submitBookmarkRequest   - saves a PENDING request once the agent has gathered enough
 *                                details from the user, for an admin to review later.
 *
 * Spring AI serializes the @Tool method signatures (including @ToolParam descriptions) into
 * the model's tool schema, and serializes whatever these methods return back to the model as
 * the tool result, which the model then turns into a natural-language reply.
 */
@Component
public class BookmarkTools {

    private static final Logger log = LoggerFactory.getLogger(BookmarkTools.class);

    private final VectorStore vectorStore;
    private final BookmarkRepository bookmarkRepository;
    private final BookmarkRequestRepository bookmarkRequestRepository;

    public BookmarkTools(VectorStore vectorStore, BookmarkRepository bookmarkRepository,
            BookmarkRequestRepository bookmarkRequestRepository) {
        this.vectorStore = vectorStore;
        this.bookmarkRepository = bookmarkRepository;
        this.bookmarkRequestRepository = bookmarkRequestRepository;
    }

    @Tool(description = "Look up a SPECIFIC bookmark by title or by a description of what it's for, e.g. 'usi' or "
            + "'VPN setup guide'. Checks approved bookmark titles directly first, then falls back to a semantic "
            + "search over the knowledge base for less exact/more descriptive queries. Always call this before "
            + "telling the user a bookmark does or does not exist. Returns an empty list if nothing relevant is "
            + "found.")
    public List<BookmarkResult> searchBookmark(
            @ToolParam(description = "The bookmark's title if you know it, otherwise a natural-language "
                    + "description of what the user is looking for, e.g. 'VPN setup guide' or 'expense "
                    + "reimbursement form'") String query) {

        List<BookmarkResult> keywordMatches = findByKeywordMatch(query);
        if (!keywordMatches.isEmpty()) {
            return keywordMatches;
        }

        // Fall back to semantic search for phrasings that share no words with the stored
        // title/description at all (e.g. a paraphrase or a different language). Guarded so a
        // transient pgvector/embedding failure degrades to "no results" instead of blowing up
        // the whole chat turn (see AgentService.chat for the outer safety net too).
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(5)
                    .similarityThreshold(0.35)
                    .build();

            return vectorStore.similaritySearch(request).stream()
                    .map(doc -> new BookmarkResult(
                            stringMeta(doc, "title", doc.getText()),
                            stringMeta(doc, "url", ""),
                            stringMeta(doc, "category", ""),
                            stringMeta(doc, "description", "")))
                    .toList();
        } catch (Exception e) {
            log.warn("Vector search failed for query '{}': {}", query, e.getMessage(), e);
            return List.of();
        }
    }

    // Common words that carry no search signal on their own - excluded so e.g. "where i can raise
    // incident" is compared on {raise, incident}, not diluted by {where, i, can}.
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "i", "me", "my", "you", "your",
            "where", "when", "which", "who", "how", "can", "could", "do", "does", "did", "for", "to", "of",
            "in", "on", "at", "and", "or", "it", "this", "that", "there", "here", "please", "give", "find",
            "get", "show", "tell", "need", "want", "url", "link", "bookmark", "raise", "have", "has");

    /**
     * Matches the query against bookmark titles/descriptions by direct substring containment
     * first (handles short exact titles like "usi"), then by shared meaningful words (handles
     * paraphrased asks like "where can I raise an incident" against a title/description that
     * mentions "incident").
     */
    private List<BookmarkResult> findByKeywordMatch(String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        if (normalizedQuery.isBlank()) {
            return List.of();
        }
        Set<String> queryTokens = tokenize(normalizedQuery);
        queryTokens.removeAll(STOPWORDS);

        return bookmarkRepository.findAll().stream()
                .filter(b -> b.getTitle() != null)
                .filter(b -> {
                    String title = b.getTitle().toLowerCase();
                    if (title.contains(normalizedQuery) || normalizedQuery.contains(title)) {
                        return true;
                    }
                    if (queryTokens.isEmpty()) {
                        return false;
                    }
                    Set<String> haystack = tokenize(title + " " + emptyToBlank(b.getDescription()));
                    return fuzzyOverlap(queryTokens, haystack);
                })
                .map(b -> new BookmarkResult(b.getTitle(), b.getUrl(), b.getCategory(), b.getDescription()))
                .limit(5)
                .toList();
    }

    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        // length > 1 (not > 2) so short but meaningful acronyms like "HR", "IT", "QA" aren't
        // silently dropped from matching - STOPWORDS already filters out short noise words.
        return Arrays.stream(text.toLowerCase().split("[^a-z0-9]+"))
                .filter(t -> t.length() > 1)
                .collect(Collectors.toSet());
    }

    /**
     * True if any query token exactly matches, or is a near-miss typo of (edit distance <= 1 for
     * 5-7 letter words, <= 2 for longer ones), any haystack token. Users mistype things ("holidya"
     * for "holiday") and exact token overlap alone misses those.
     */
    private static boolean fuzzyOverlap(Set<String> queryTokens, Set<String> haystackTokens) {
        if (!Collections.disjoint(queryTokens, haystackTokens)) {
            return true;
        }
        for (String q : queryTokens) {
            for (String h : haystackTokens) {
                int maxDistance = Math.min(q.length(), h.length()) >= 8 ? 2 : 1;
                if (Math.min(q.length(), h.length()) >= 5 && Math.abs(q.length() - h.length()) <= maxDistance
                        && levenshtein(q, h) <= maxDistance) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[b.length()];
    }

    private static String emptyToBlank(String value) {
        return value != null ? value : "";
    }

    @Tool(description = "List every approved bookmark in a given category, or find one described by a category-like "
            + "phrase. Use this - not searchBookmark - whenever the user asks what's available/listed under a "
            + "category, e.g. 'what bookmarks are under Finance' or 'show me everything in the Tools category'. "
            + "Matches case-insensitively and allows partial/misspelled matches. If no category matches, this also "
            + "tries matching the phrase against bookmark titles/descriptions directly (in case it wasn't really a "
            + "category), so you don't need to separately call searchBookmark as a fallback. Still returns an "
            + "empty list if truly nothing matches - in that case call listCategories to find the closest real "
            + "category name instead of guessing.")
    public List<BookmarkResult> listBookmarksByCategory(
            @ToolParam(description = "The category name (or part of it) to list bookmarks for") String category) {

        List<BookmarkResult> categoryMatches = bookmarkRepository.findByCategoryIgnoreCaseContaining(category)
                .stream()
                .map(b -> new BookmarkResult(b.getTitle(), b.getUrl(), b.getCategory(), b.getDescription()))
                .toList();
        if (!categoryMatches.isEmpty()) {
            return categoryMatches;
        }

        // The model's guess that this was a category name may be wrong - fall back to the same
        // keyword matching searchBookmark uses, treating it as a general query instead.
        return findByKeywordMatch(category);
    }

    @Tool(description = "List the distinct category names that currently have at least one approved bookmark. "
            + "Use this if listBookmarksByCategory returned nothing, to check whether the user meant a "
            + "differently-worded or misspelled category, and suggest the closest real one instead of saying "
            + "nothing exists.")
    public List<String> listCategories() {
        return bookmarkRepository.findDistinctCategories();
    }

    @Tool(description = "Submit a new bookmark request for admin review because no existing bookmark matched the "
            + "user's need. Only call this after you have collected a title, a description, and a category from "
            + "the user (the URL is optional - pass an empty string if the user does not know it), AND after you "
            + "have shown the user a summary of these exact details and they have explicitly confirmed/approved "
            + "it. Never invent a URL yourself, and never call this before the user has confirmed.")
    public String submitBookmarkRequest(
            @ToolParam(description = "Short descriptive title for the bookmark") String title,
            @ToolParam(description = "The URL if the user knows it, otherwise an empty string") String url,
            @ToolParam(description = "What the bookmark is for / why it's useful") String description,
            @ToolParam(description = "A category, e.g. Engineering, HR, Finance, Tools") String category,
            ToolContext toolContext) {

        String username = String.valueOf(toolContext.getContext().get("username"));

        BookmarkRequest bookmarkRequest = new BookmarkRequest();
        bookmarkRequest.setRequestedBy(username);
        bookmarkRequest.setTitle(title);
        bookmarkRequest.setUrl(url);
        bookmarkRequest.setDescription(description);
        bookmarkRequest.setCategory(category);
        bookmarkRequest.setStatus(RequestStatus.PENDING);
        bookmarkRequest.setCreatedAt(Instant.now());
        bookmarkRequestRepository.save(bookmarkRequest);

        return "Submitted request #" + bookmarkRequest.getId() + " for '" + title
                + "'. It is now pending admin approval; the user will be notified once it is reviewed.";
    }

    private static String stringMeta(Document doc, String key, String fallback) {
        Object value = doc.getMetadata().get(key);
        return value != null ? String.valueOf(value) : fallback;
    }

    public record BookmarkResult(String title, String url, String category, String description) {
    }
}