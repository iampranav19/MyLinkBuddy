package com.linkBuddy.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import com.linkBuddy.service.tools.BookmarkTools;

/**
 * Wraps the Spring AI ChatClient with the bookmark agent's system prompt, tool wiring, and a
 * simple in-memory per-user conversation history (fine for a single-instance demo app).
 *
 * Tool-calling flow per message:
 *   1. User message + system prompt + prior turns are sent to the model.
 *   2. If the model decides it needs data, it calls searchBookmark and/or submitBookmarkRequest
 *      (see BookmarkTools) - Spring AI executes them locally and feeds the results back to the
 *      model automatically, in the same .call().
 *   3. The model produces the final natural-language reply, which we return and also append
 *      to the conversation history for the next turn.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private static final String SYSTEM_PROMPT = """
            You are the internal bookmark assistant for the organization.

            Tool selection:
            - If the user asks for a SPECIFIC bookmark (e.g. "where's the VPN guide?"), call searchBookmark.
            - If the user asks what's available UNDER/IN a category (e.g. "what's under Finance", "list
              everything in Tools"), call listBookmarksByCategory instead - searchBookmark only returns a
              similarity-ranked handful of results and is not reliable for exhaustive category listings.
            - You will not always be able to tell in advance whether something is a bookmark topic or a
              category name (e.g. "public holiday") - that's fine, just pick one and call it.
            - If your first lookup tool call comes back empty, DO NOT immediately conclude nothing exists -
              try the OTHER lookup tool with the same or a simplified phrase before giving up (e.g. if
              listBookmarksByCategory found nothing, try searchBookmark with the same words, and vice versa).
            - Only after trying both, call listCategories to check whether the user's wording is just a
              misspelling or rephrasing of a real category - if a close match exists, suggest it or retry
              with the real category name.

            Rules you must always follow:
            1. Always call a lookup tool (and try the other one if the first comes back empty, per above)
               before answering - even if you think you already know the URL.
            2. If a lookup tool returns one or more results, present their title(s) and URL(s) to the user.
               NEVER invent, guess, or modify a URL - only ever state a URL that was returned by a tool.
            3. If, after trying both lookup tools and checking listCategories, nothing matches, do NOT say a
               URL exists and do NOT make one up. Instead, tell the user it isn't in the knowledge base yet
               and ask for the details needed to request it: a title, a short description, a category, and
               the URL if they happen to know it (it's fine if they don't).
            4. Once you have at least a title, description and category, do NOT call submitBookmarkRequest
               yet. First summarize back the exact details you are about to submit - title, description,
               category, and URL (or "none" if not provided) - and ask the user to confirm, e.g. "Here's
               what I'll submit: ... Shall I go ahead, or would you like to change anything?"
            5. If the user asks to change any detail, update it and show the summary again for confirmation
               before submitting. Only call submitBookmarkRequest after the user explicitly confirms/approves
               the summary as-is. Then tell the user it has been submitted and is pending approval.
            6. submitBookmarkRequest automatically checks for an existing bookmark that looks very similar. If
               it reports a match instead of confirming submission, do NOT treat the request as submitted -
               tell the user about the existing match and ask whether they still want a new request filed
               despite it. Only call submitBookmarkRequest again (same details, confirmDuplicate=true) if they
               explicitly say yes; if they'd rather use the existing bookmark, give them that instead.
            7. Be concise and friendly.
            8. Reply in plain conversational text only - no markdown (no **bold**, no bullet/numbered lists,
               no headers). The chat UI displays raw text, so markdown syntax would show up as literal
               asterisks/dashes. When listing multiple bookmarks, put each on its own line as
               "Title - URL (category)".
            """;

    private final ChatClient chatClient;
    private final BookmarkTools bookmarkTools;
    private final Map<String, List<Message>> conversations = new ConcurrentHashMap<>();

    public AgentService(ChatClient.Builder chatClientBuilder, BookmarkTools bookmarkTools) {
        this.chatClient = chatClientBuilder.build();
        this.bookmarkTools = bookmarkTools;
    }

    public String chat(String username, String userMessage) {
        List<Message> history = conversations.computeIfAbsent(username, u -> new ArrayList<>());

        String answer;
        try {
            answer = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .messages(history)
                    .user(userMessage)
                    .tools(bookmarkTools)
                    // Lets submitBookmarkRequest know who is asking without trusting the model to state it.
                    .toolContext(Map.of("username", username))
                    .call()
                    .content();
        } catch (Exception e) {
            // Without this, any failure here (OpenAI auth/quota/network, a tool throwing, etc.)
            // propagates up through the Vaadin event listener, gets swallowed by Vaadin's default
            // error handler, and the user sees no reply at all with nothing to react to. Always
            // hand back *something* instead, and keep the bad exchange out of the conversation
            // history so it doesn't confuse the next turn.
            log.error("Chat call failed for user '{}': {}", username, e.getMessage(), e);
            return "Sorry, I ran into a problem answering that. Please try again in a moment.";
        }

        history.add(new UserMessage(userMessage));
        history.add(new AssistantMessage(answer));

        return answer;
    }
}