package com.linkBuddy.views;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.linkBuddy.service.AgentService;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.PermitAll;

/**
 * Chat UI wired to AgentService.chat(username, message) - see AgentService for the tool-calling
 * flow (searchBookmark / submitBookmarkRequest).
 */
@Route(value = "bookmarks", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@PageTitle("Bookmarks | MyLinkBuddy")
@PermitAll
public class BookmarkChatView extends VerticalLayout {

    public BookmarkChatView(AgentService agentService, AuthenticationContext authenticationContext) {
        String username = authenticationContext.getPrincipalName().orElse("guest");

        setSizeFull();

        MessageList messageList = new MessageList();
        messageList.setSizeFull();

        List<MessageListItem> items = new ArrayList<>();
        items.add(agentItem("Hi " + username + "! Ask me for a bookmark, e.g. \"Where's the VPN setup guide?\", "
                + "and I'll look it up or help you request it."));
        messageList.setItems(items);

        MessageInput messageInput = new MessageInput();
        messageInput.setWidthFull();
        messageInput.addSubmitListener(event -> {
            String text = event.getValue();

            items.add(new MessageListItem(text, Instant.now(), username));
            messageList.setItems(new ArrayList<>(items));

            String reply = agentService.chat(username, text);

            items.add(agentItem(reply));
            messageList.setItems(new ArrayList<>(items));
        });

        add(messageList, messageInput);
        setFlexGrow(1, messageList);
    }

    private static MessageListItem agentItem(String text) {
        MessageListItem item = new MessageListItem(text, Instant.now(), "Bookmark Agent");
        item.setUserColorIndex(2);
        return item;
    }
}