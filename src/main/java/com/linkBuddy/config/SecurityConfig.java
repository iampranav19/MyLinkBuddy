package com.linkBuddy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import com.linkBuddy.views.LoginView;
import com.vaadin.flow.spring.security.VaadinWebSecurity;

/**
 * Form-login security config wired through Vaadin's VaadinWebSecurity helper, which also
 * enforces the @AnonymousAllowed / @PermitAll / @RolesAllowed annotations on the views for
 * route-level access control.
 *
 * Users are hard-coded in memory with PLAINTEXT passwords ({noop} prefix) for local testing only.
 * Do not use this UserDetailsService in production.
 */
@Configuration
public class SecurityConfig extends VaadinWebSecurity {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http);
        setLoginView(http, LoginView.class);
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails admin = User.withUsername("admin")
                .password("{noop}admin123")
                .roles("ADMIN", "USER")
                .build();

        UserDetails sumit = User.withUsername("sumit")
                .password("{noop}sumit123")
                .roles("USER")
                .build();

        UserDetails gaurav = User.withUsername("gaurav")
                .password("{noop}gaurav123")
                .roles("USER")
                .build();

        UserDetails priya = User.withUsername("priya")
                .password("{noop}priya123")
                .roles("USER")
                .build();

        UserDetails pankaj = User.withUsername("pankaj")
                .password("{noop}pankaj123")
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(admin, sumit, gaurav, priya, pankaj);
    }
}