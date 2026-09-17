package com.linkBuddy.config;

import java.net.MalformedURLException;
import java.net.URL;

import org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAIClientBuilderCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.azure.core.http.HttpPipelineCallContext;
import com.azure.core.http.HttpPipelineNextPolicy;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.policy.HttpPipelinePolicy;
import com.azure.core.util.UrlBuilder;

import reactor.core.publisher.Mono;

/**
 * The org's APIM/Azure OpenAI sandbox requires an exact api-version (e.g. "2024-12-01-preview")
 * that the Azure SDK version bundled with this Spring AI release doesn't expose as a selectable
 * {@code OpenAIServiceVersion} enum constant, so it can't be set via the usual Spring AI
 * properties. This adds an HTTP pipeline policy - Spring AI's supported extension point for the
 * Azure OpenAI client builder - that rewrites the api-version query parameter on every request to
 * the configured literal value instead.
 */
@Configuration
public class AzureOpenAiApiVersionConfig {

    @Bean
    public AzureOpenAIClientBuilderCustomizer apiVersionOverrideCustomizer(
            @Value("${app.azure-openai.api-version}") String apiVersion) {
        return builder -> builder.addPolicy(new ApiVersionOverridePolicy(apiVersion));
    }

    private static final class ApiVersionOverridePolicy implements HttpPipelinePolicy {

        private final String apiVersion;

        private ApiVersionOverridePolicy(String apiVersion) {
            this.apiVersion = apiVersion;
        }

        @Override
        public Mono<HttpResponse> process(HttpPipelineCallContext context, HttpPipelineNextPolicy next) {
            overrideApiVersion(context);
            return next.process();
        }

        private void overrideApiVersion(HttpPipelineCallContext context) {
            try {
                URL current = context.getHttpRequest().getUrl();
                URL updated = UrlBuilder.parse(current).setQueryParameter("api-version", apiVersion).toUrl();
                context.getHttpRequest().setUrl(updated);
            } catch (MalformedURLException e) {
                throw new IllegalStateException("Failed to override api-version on Azure OpenAI request", e);
            }
        }
    }
}