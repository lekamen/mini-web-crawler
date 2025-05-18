package org.example.webcrawler.mini.model;

import java.net.URI;
import java.util.Objects;

public class Site {

  private final URI url;
  private final int numberOfTimesVisitAttempted;

  public Site(URI url) {
    this(url, 0);
  }

  public Site(URI url, int numberOfTimesVisitAttempted) {
    this.url = url;
    this.numberOfTimesVisitAttempted = numberOfTimesVisitAttempted;
  }

  public Site incrementNumberOfTimesVisitAttemptedAndGet() {
    return new Site(url, numberOfTimesVisitAttempted + 1);
  }

  public int getNumberOfTimesVisitAttempted() {
    return numberOfTimesVisitAttempted;
  }

  public URI getUrl() {
    return url;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Site site = (Site) o;
    return Objects.equals(url, site.url);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(url);
  }

  @Override
  public String toString() {
    return "Site{" +
        "url=" + url +
        '}';
  }
}
