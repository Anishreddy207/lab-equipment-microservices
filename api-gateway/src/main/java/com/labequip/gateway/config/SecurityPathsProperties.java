package com.labequip.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.AntPathMatcher;

import java.util.List;

@ConfigurationProperties(prefix = "security")
public class SecurityPathsProperties {

    private final AntPathMatcher matcher = new AntPathMatcher();
    private List<String> publicPaths = List.of();

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public boolean isPublic(String path) {
        return publicPaths.stream().anyMatch(pattern -> matcher.match(pattern, path));
    }
}
