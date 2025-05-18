package org.example.webcrawler.mini.model;

import java.util.Collections;
import java.util.Set;

public record CrawlResult(boolean success, Site origin, Set<Site> sites) {

  public static CrawlResult failed(Site origin) {
    return new CrawlResult(false, origin, Collections.emptySet());
  }

  public static CrawlResult success(Site origin, Set<Site> sites) {
    return new CrawlResult(true, origin, sites);
  }

  public static CrawlResult emptySuccess(Site origin) {
    return new CrawlResult(true, origin, Collections.emptySet());
  }
}
