package com.apontaja.back.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

@Configuration
public class PageableConfig
        implements PageableHandlerMethodArgumentResolverCustomizer {

    private static final int MAX_PAGE_SIZE = 100;

    @Override
    public void customize(
            org.springframework.data.web.PageableHandlerMethodArgumentResolver resolver) {
        resolver.setMaxPageSize(MAX_PAGE_SIZE);
    }
}
