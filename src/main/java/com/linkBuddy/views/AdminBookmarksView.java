package com.linkBuddy.views;

import com.linkBuddy.entity.Bookmark;
import com.linkBuddy.service.AdminRequestService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;

/**
 * Admin-only view listing every published Bookmark with Edit/Delete actions. Edit updates the
 * row and re-embeds it into pgvector; Delete removes both the row and its embedding, so the
 * agent's searchBookmark tool never returns a stale or dangling result.
 */
@Route(value = "admin/bookmarks", layout = MainLayout.class)
@PageTitle("Manage Bookmarks | MyLinkBuddy")
@RolesAllowed("ADMIN")
public class AdminBookmarksView extends VerticalLayout {

    private final AdminRequestService adminRequestService;
    private final Grid<Bookmark> grid = new Grid<>(Bookmark.class, false);

    public AdminBookmarksView(AdminRequestService adminRequestService) {
        this.adminRequestService = adminRequestService;

        setSizeFull();
        add(new H2("Published Bookmarks"));

        grid.addColumn(Bookmark::getTitle).setHeader("Title");
        grid.addColumn(Bookmark::getUrl).setHeader("URL");
        grid.addColumn(Bookmark::getDescription).setHeader("Description");
        grid.addColumn(Bookmark::getCategory).setHeader("Category");
        grid.addComponentColumn(this::createActions).setHeader("Actions");
        grid.setSizeFull();

        add(grid);
        setFlexGrow(1, grid);

        refresh();
    }

    private Component createActions(Bookmark bookmark) {
        Button edit = new Button("Edit", e -> openEditDialog(bookmark));

        Button delete = new Button("Delete", e -> openDeleteDialog(bookmark));
        delete.getStyle().set("color", "red");

        return new HorizontalLayout(edit, delete);
    }

    private void openEditDialog(Bookmark bookmark) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit bookmark: " + bookmark.getTitle());

        TextField title = new TextField("Title");
        title.setWidthFull();
        title.setValue(nullToEmpty(bookmark.getTitle()));
        title.setRequired(true);

        TextField url = new TextField("URL");
        url.setWidthFull();
        url.setValue(nullToEmpty(bookmark.getUrl()));
        url.setRequired(true);

        TextArea description = new TextArea("Description");
        description.setWidthFull();
        description.setValue(nullToEmpty(bookmark.getDescription()));
        description.setRequired(true);

        TextField category = new TextField("Category");
        category.setWidthFull();
        category.setValue(nullToEmpty(bookmark.getCategory()));
        category.setRequired(true);

        VerticalLayout form = new VerticalLayout(title, url, description, category);
        form.setPadding(false);
        dialog.add(form);

        Button save = new Button("Save", e -> {
            if (title.getValue().isBlank() || url.getValue().isBlank()
                    || description.getValue().isBlank() || category.getValue().isBlank()) {
                Notification.show("Title, URL, description and category are all required");
                return;
            }
            adminRequestService.updateBookmark(bookmark.getId(), title.getValue(), url.getValue(),
                    description.getValue(), category.getValue());
            Notification.show("Updated '" + title.getValue() + "'");
            dialog.close();
            refresh();
        });
        Button cancel = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancel, save);

        dialog.open();
    }

    private void openDeleteDialog(Bookmark bookmark) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Delete bookmark: " + bookmark.getTitle());
        dialog.add(new Paragraph("This removes it from the knowledge base permanently. "
                + "The agent will no longer be able to find or suggest it. This cannot be undone."));

        Button confirm = new Button("Delete", e -> {
            adminRequestService.deleteBookmark(bookmark.getId());
            Notification.show("Deleted '" + bookmark.getTitle() + "'");
            dialog.close();
            refresh();
        });
        confirm.getStyle().set("color", "red");
        Button cancel = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancel, confirm);

        dialog.open();
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private void refresh() {
        grid.setItems(adminRequestService.listBookmarks());
    }
}