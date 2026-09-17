package com.linkBuddy.views;

import com.linkBuddy.entity.BookmarkRequest;
import com.linkBuddy.entity.RequestStatus;
import com.linkBuddy.repository.BookmarkRequestRepository;
import com.linkBuddy.service.AdminRequestService;
import com.linkBuddy.service.RequestBroadcaster;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.PermitAll;

/**
 * Shows the current user's own request history and statuses, updated in real time (via @Push +
 * RequestBroadcaster) whenever an admin approves or rejects one of their requests. Lets the user
 * cancel their own requests while still PENDING.
 */
@Route(value = "my-requests", layout = MainLayout.class)
@PageTitle("My Requests | MyLinkBuddy")
@PermitAll
public class MyRequestsView extends VerticalLayout {

    private final BookmarkRequestRepository bookmarkRequestRepository;
    private final AdminRequestService adminRequestService;
    private final RequestBroadcaster broadcaster;
    private final String username;
    private final Grid<BookmarkRequest> grid = new Grid<>(BookmarkRequest.class, false);

    private Registration broadcasterRegistration;

    public MyRequestsView(BookmarkRequestRepository bookmarkRequestRepository,
            AdminRequestService adminRequestService, RequestBroadcaster broadcaster,
            AuthenticationContext authenticationContext) {
        this.bookmarkRequestRepository = bookmarkRequestRepository;
        this.adminRequestService = adminRequestService;
        this.broadcaster = broadcaster;
        this.username = authenticationContext.getPrincipalName().orElse("unknown");

        setSizeFull();
        add(new H2("My Bookmark Requests"));

        grid.addColumn(BookmarkRequest::getTitle).setHeader("Title");
        grid.addColumn(BookmarkRequest::getCategory).setHeader("Category");
        grid.addColumn(r -> r.getStatus().name()).setHeader("Status");
        grid.addColumn(r -> r.getRejectReason() != null ? r.getRejectReason() : "").setHeader("Reject Reason");
        grid.addColumn(r -> r.getCreatedAt() != null ? r.getCreatedAt().toString() : "").setHeader("Requested At");
        grid.addComponentColumn(this::createActions).setHeader("Actions");
        grid.setSizeFull();

        add(grid);
        setFlexGrow(1, grid);

        refresh();
    }

    private Component createActions(BookmarkRequest request) {
        if (request.getStatus() != RequestStatus.PENDING) {
            return new Paragraph("");
        }
        Button cancel = new Button("Cancel", e -> openCancelDialog(request));
        cancel.getStyle().set("color", "red");
        return cancel;
    }

    private void openCancelDialog(BookmarkRequest request) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Cancel request: " + request.getTitle());
        dialog.add(new Paragraph("This withdraws your request so it won't be reviewed. This cannot be undone."));

        Button confirm = new Button("Confirm Cancel", e -> {
            adminRequestService.cancel(request.getId(), username);
            Notification.show("Cancelled '" + request.getTitle() + "'");
            dialog.close();
            refresh();
        });
        confirm.getStyle().set("color", "red");
        Button keep = new Button("Keep Request", e -> dialog.close());
        dialog.getFooter().add(keep, confirm);

        dialog.open();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        UI ui = attachEvent.getUI();
        broadcasterRegistration = broadcaster.register(event -> {
            if (event.username().equals(username)) {
                ui.access(() -> {
                    refresh();
                    Notification.show(
                            "Your request '" + event.title() + "' was " + event.status().name().toLowerCase());
                });
            }
        });
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (broadcasterRegistration != null) {
            broadcasterRegistration.remove();
            broadcasterRegistration = null;
        }
    }

    private void refresh() {
        grid.setItems(bookmarkRequestRepository.findByRequestedByOrderByCreatedAtDesc(username));
    }
}