package org.example.webcrawler.mini.config;

public record CrawlingConfig(int politenessMaxDelaySeconds,
                             int maxVisitsPerSite,
                             int maxCrawlDepth,
                             int timeoutSeconds) {}
