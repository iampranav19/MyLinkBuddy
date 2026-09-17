package com.linkBuddy.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.linkBuddy.entity.Bookmark;
import com.linkBuddy.repository.BookmarkRepository;

/**
 * Seeds 10 sample bookmarks on first startup (only when the bookmark table is empty), so the
 * chat agent's searchBookmark / listBookmarksByCategory tools have something to find right away.
 * Each bookmark is saved to the Bookmark table AND embedded into the pgvector store, mirroring
 * what AdminRequestService.approve does for a real approved request.
 */
@Component
public class SampleDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataSeeder.class);

    private final BookmarkRepository bookmarkRepository;
    private final VectorStore vectorStore;

    public SampleDataSeeder(BookmarkRepository bookmarkRepository, VectorStore vectorStore) {
        this.bookmarkRepository = bookmarkRepository;
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(String... args) {
        if (bookmarkRepository.count() > 0) {
            return;
        }

        List<Bookmark> samples = List.of(
                bookmark("VPN Setup Guide", "https://intranet.example.com/it/vpn-setup",
                        "Step-by-step guide to configure and connect to the corporate VPN on Windows, macOS, "
                                + "and Linux.",
                        "IT"),
                bookmark("Expense Reimbursement Form", "https://intranet.example.com/finance/expense-form",
                        "Submit travel and purchase expenses for reimbursement through the finance portal.",
                        "Finance"),
                bookmark("Employee Handbook", "https://intranet.example.com/hr/handbook",
                        "Company policies, code of conduct, and benefits overview for all employees.", "HR"),
                bookmark("ServiceNow Portal", "https://servicenow.example.com",
                        "Raise and track IT support tickets, hardware requests, and access requests.", "IT"),
                bookmark("Engineering Wiki", "https://wiki.example.com/engineering",
                        "Central knowledge base for architecture docs, coding standards, and onboarding "
                                + "for engineers.",
                        "Engineering"),
                bookmark("Company Holiday Calendar", "https://intranet.example.com/hr/holidays",
                        "List of public holidays and company-wide days off for the current year.", "HR"),
                bookmark("Internal Team Chat", "https://example.slack.com",
                        "Company Slack workspace used for day-to-day team communication and announcements.",
                        "Communication"),
                bookmark("Payroll Self-Service", "https://payroll.example.com",
                        "View payslips, tax documents, and update direct deposit details.", "Finance"),
                bookmark("New Hire Onboarding Checklist", "https://intranet.example.com/hr/onboarding",
                        "Checklist of tasks and resources for employees in their first two weeks.", "HR"),
                bookmark("Incident Response Runbook", "https://wiki.example.com/engineering/incident-response",
                        "Step-by-step runbook for triaging and resolving production incidents.", "Engineering"));

        bookmarkRepository.saveAll(samples);

        try {
            List<Document> documents = new ArrayList<>();
            for (Bookmark b : samples) {
                documents.add(new Document(
                        b.getTitle() + " - " + b.getDescription(),
                        Map.of(
                                "bookmarkId", b.getId(),
                                "title", b.getTitle(),
                                "url", b.getUrl(),
                                "category", b.getCategory() == null ? "" : b.getCategory(),
                                "description", b.getDescription() == null ? "" : b.getDescription())));
            }
            vectorStore.add(documents);
            log.info("Seeded {} sample bookmarks and embedded them into the vector store.", samples.size());
        } catch (Exception e) {
            log.warn("Seeded {} sample bookmarks, but embedding them into the vector store failed "
                    + "(check OPENAI_API_KEY) - they won't be found by semantic search until re-embedded.",
                    samples.size(), e);
        }
    }

    private static Bookmark bookmark(String title, String url, String description, String category) {
        Bookmark bookmark = new Bookmark();
        bookmark.setTitle(title);
        bookmark.setUrl(url);
        bookmark.setDescription(description);
        bookmark.setCategory(category);
        return bookmark;
    }
}