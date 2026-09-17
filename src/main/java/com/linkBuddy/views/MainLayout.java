package com.linkBuddy.views;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.spring.security.AuthenticationContext;

public class MainLayout extends AppLayout {

    public MainLayout(AuthenticationContext authenticationContext) {
        H1 title = new H1("MyLinkBuddy");
        title.getStyle().set("font-size", "1.3rem").set("margin", "0 1rem");

        HorizontalLayout nav = new HorizontalLayout(
                new RouterLink("Chat", BookmarkChatView.class),
                new RouterLink("My Requests", MyRequestsView.class));
        if (isAdmin()) {
            nav.add(new RouterLink("Admin Requests", AdminRequestsView.class));
            nav.add(new RouterLink("Manage Bookmarks", AdminBookmarksView.class));
        }
        nav.setAlignItems(FlexComponent.Alignment.CENTER);
        nav.setSpacing(true);
        nav.getStyle().set("margin", "0 1rem");

        Button logout = new Button("Log out", e -> authenticationContext.logout());

        HorizontalLayout header = new HorizontalLayout(title, nav, logout);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.getStyle().set("padding", "0.5rem 1rem");

        addToNavbar(header);
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}