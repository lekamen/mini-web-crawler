package org.example.webcrawler.mini.config;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

public record HttpConfig(int timeoutSeconds,
                         int connectTimeoutSeconds) {

  public HttpClient defaultHttpClient() {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.of(connectTimeoutSeconds, ChronoUnit.SECONDS))
        .build();
  }
}
