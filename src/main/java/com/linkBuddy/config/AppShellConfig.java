package com.linkBuddy.config;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;

/**
 * Enables server push (@Push) so approval/rejection notifications from AdminRequestService
 * reach the MyRequestsView / chat UI in real time via RequestBroadcaster + UI.access(...).
 */
@Push
public class AppShellConfig implements AppShellConfigurator {
}