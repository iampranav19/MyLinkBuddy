package com.linkBuddy.views;

import com.linkBuddy.entity.BookmarkRequest;
import com.linkBuddy.entity.RequestStatus;
import com.linkBuddy.repository.BookmarkRequestRepository;
import com.linkBuddy.service.AdminRequestService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;

/**
 * Admin-only view listing PENDING bookmark requests with Approve/Reject actions.
 * Approve copies the request into Bookmark + embeds it into the pgvector store (see
 * AdminRequestService.approve). Reject captures a reason and marks the request REJECTED.
 */
@Route(value = "admin/requests", layout = MainLayout.class)
@PageTitle("Admin Requests | MyLinkBuddy")
@RolesAllowed("ADMIN")
public class AdminRequestsView extends VerticalLayout {

    private final BookmarkRequestRepository bookmarkRequestRepository;
    private final AdminRequestService adminRequestService;
    private final Grid<BookmarkRequest> grid = new Grid<>(BookmarkRequest.class, false);

    public AdminRequestsView(BookmarkRequestRepository bookmarkRequestRepository,
            AdminRequestService adminRequestService) {
        this.bookmarkRequestRepository = bookmarkRequestRepository;
        this.adminRequestService = adminRequestService;

        setSizeFull();
        add(new H2("Pending Bookmark Requests"));

        grid.addColumn(BookmarkRequest::getRequestedBy).setHeader("Requested By");
        grid.addColumn(BookmarkRequest::getTitle).setHeader("Title");
        grid.addColumn(BookmarkRequest::getUrl).setHeader("URL");
        grid.addColumn(BookmarkRequest::getDescription).setHeader("Description");
        grid.addColumn(BookmarkRequest::getCategory).setHeader("Category");
        grid.addColumn(r -> r.getCreatedAt() != null ? r.getCreatedAt().toString() : "").setHeader("Requested At");
        grid.addComponentColumn(this::createActions).setHeader("Actions");
        grid.setSizeFull();

        add(grid);
        setFlexGrow(1, grid);

        refresh();
    }

    private Component createActions(BookmarkRequest request) {
        Button approve = new Button("Approve", e -> openApproveDialog(request));
        approve.getStyle().set("color", "green");

        Button reject = new Button("Reject", e -> openRejectDialog(request));
        reject.getStyle().set("color", "red");

        return new HorizontalLayout(approve, reject);
    }

    /**
     * Lets the admin fill in or correct any field - most commonly the URL, when the requesting
     * user didn't know it - before the request is published as a real Bookmark.
     */
    private void openApproveDialog(BookmarkRequest request) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Approve request: " + request.getTitle());

        TextField title = new TextField("Title");
        title.setWidthFull();
        title.setValue(nullToEmpty(request.getTitle()));
        title.setRequired(true);

        TextField url = new TextField("URL");
        url.setWidthFull();
        url.setValue(nullToEmpty(request.getUrl()));
        url.setRequired(true);
        url.setHelperText("Fill this in if the user didn't know it");

        TextArea description = new TextArea("Description");
        description.setWidthFull();
        description.setValue(nullToEmpty(request.getDescription()));
        description.setRequired(true);

        TextField category = new TextField("Category");
        category.setWidthFull();
        category.setValue(nullToEmpty(request.getCategory()));
        category.setRequired(true);

        VerticalLayout form = new VerticalLayout(title, url, description, category);
        form.setPadding(false);
        dialog.add(form);

        Button confirm = new Button("Confirm Approve", e -> {
            if (title.getValue().isBlank() || url.getValue().isBlank()
                    || description.getValue().isBlank() || category.getValue().isBlank()) {
                Notification.show("Title, URL, description and category are all required to approve");
                return;
            }
            adminRequestService.approve(request.getId(), title.getValue(), url.getValue(),
                    description.getValue(), category.getValue());
            Notification.show("Approved '" + title.getValue() + "'");
            dialog.close();
            refresh();
        });
        Button cancel = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancel, confirm);

        dialog.open();
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private void openRejectDialog(BookmarkRequest request) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Reject request: " + request.getTitle());

        TextArea reason = new TextArea("Rejection reason");
        reason.setWidthFull();
        reason.setRequired(true);
        dialog.add(reason);

        Button confirm = new Button("Confirm Reject", e -> {
            if (reason.getValue() == null || reason.getValue().isBlank()) {
                Notification.show("Please provide a reason");
                return;
            }
            adminRequestService.reject(request.getId(), reason.getValue());
            Notification.show("Rejected '" + request.getTitle() + "'");
            dialog.close();
            refresh();
        });
        Button cancel = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancel, confirm);

        dialog.open();
    }

    private void refresh() {
        grid.setItems(bookmarkRequestRepository.findByStatusOrderByCreatedAtDesc(RequestStatus.PENDING));
    }
}