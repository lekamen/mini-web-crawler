package org.example.webcrawler.mini.crawler;

import static org.example.webcrawler.mini.HttpUtils.isStatus2xx;
import static org.example.webcrawler.mini.HttpUtils.isStatus3xx;
import static org.example.webcrawler.mini.HttpUtils.isStatus4xx;
import static org.example.webcrawler.mini.HttpUtils.isStatus5xx;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.example.webcrawler.mini.HttpUtils;
import org.example.webcrawler.mini.config.CrawlingConfig;
import org.example.webcrawler.mini.config.HttpConfig;
import org.example.webcrawler.mini.model.CrawlResult;
import org.example.webcrawler.mini.model.Site;

public class WebCrawler {

  private final Logger logger = Logger.getLogger(this.getClass().getName());

  private final Set<Site> visitedSites;
  private final Queue<Site> queue;
  private final HttpClient httpClient;
  private final HttpConfig httpConfig;
  private final CrawlingConfig crawlingConfig;
  private final ExecutorService executorService;

  private Site seedSite;

  private static final Pattern pattern = Pattern.compile("a\\s+(?:[^>]*?\\s+)?href=\"(?<url>.*?)\"");

  public WebCrawler(HttpConfig httpConfig, CrawlingConfig crawlingConfig, ExecutorService executorService) {
    this.visitedSites = new HashSet<>();
    this.queue = new LinkedList<>();
    this.httpConfig = httpConfig;
    this.crawlingConfig = crawlingConfig;
    this.httpClient = httpConfig.defaultHttpClient();
    this.executorService = executorService;
  }

  public void crawl(String seedUrl) {

    URI url = validateSeedUrl(seedUrl);
    this.seedSite = new Site(url);
    visitedSites.clear();
    queue.offer(seedSite);

    int depth = 0;
    while (!queue.isEmpty() && depth < crawlingConfig.maxCrawlDepth()) {

      List<CompletableFuture<CrawlResult>> tasks = new ArrayList<>();
      while (!queue.isEmpty()) {
        Site site = queue.poll();

        CompletableFuture<CrawlResult> task = CompletableFuture.supplyAsync(
            () -> fetchPage(site, Collections.unmodifiableSet(visitedSites)),
                executorService)
            .completeOnTimeout(
                CrawlResult.failed(site.incrementNumberOfTimesVisitAttemptedAndGet()),
                crawlingConfig.timeoutSeconds(),
                TimeUnit.SECONDS);
        tasks.add(task);
      }

      CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();

      for(CompletableFuture<CrawlResult> task : tasks) {
        CrawlResult result = task.join(); // should immediately return
        if (result.success()) {
          visitedSites.add(result.origin());
          result.sites().stream()
              .filter(crawledSite -> !visitedSites.contains(crawledSite) && !queue.contains(crawledSite))
              .filter(crawledSite -> crawledSite.getNumberOfTimesVisitAttempted() < crawlingConfig.maxVisitsPerSite())
              .forEach(queue::offer);

        }
        else if (result.origin().getNumberOfTimesVisitAttempted() < crawlingConfig.maxVisitsPerSite()) {
          queue.offer(result.origin());
        }
      }

      depth++;
    }

    logger.info(String.format("Finished after %s/%s steps. Found %d sites",
        depth, crawlingConfig.maxCrawlDepth(), visitedSites.size()));
  }

  public String getSortedVisitedSites() {
    return visitedSites.stream()
        .sorted(Comparator.comparing(site -> site.getUrl().toString()))
        .map(site -> site.getUrl().toString())
        .collect(Collectors.joining(System.lineSeparator()));
  }

  private CrawlResult fetchPage(Site site, Set<Site> visitedSites) {

    if (visitedSites.contains(site)) {
      return CrawlResult.emptySuccess(site);
    }

    // make a random pause before trying to connect
    try {
      int sleepTime = ThreadLocalRandom.current().nextInt(crawlingConfig.politenessMaxDelaySeconds());
      logger.info(String.format("Sleeping for %d seconds before crawling %s", sleepTime, site.getUrl()));
      Thread.sleep(Duration.ofSeconds(sleepTime));
    } catch (InterruptedException ignore) {} // don't stop the whole process if this fails

    HttpRequest request = HttpRequest.newBuilder()
        .uri(site.getUrl())
        .timeout(Duration.of(httpConfig.timeoutSeconds(), ChronoUnit.SECONDS))
        .GET()
        .build();

    try {
      HttpResponse<Stream<String>> response = httpClient.send(request, BodyHandlers.ofLines());
      int statusCode = response.statusCode();

      if (isStatus5xx(statusCode)) {
        // try visiting this page later
        logger.info(String.format("Received HTTP status %s for url %s, will retry later", statusCode, site.getUrl()));
        return CrawlResult.failed(site.incrementNumberOfTimesVisitAttemptedAndGet());
      }

      if (isStatus4xx(statusCode)) {
        logger.info(String.format("Received HTTP status %s for url %s, site won't be visited again", statusCode, site.getUrl()));
        return CrawlResult.emptySuccess(site);
      }

      if (isStatus3xx(statusCode)) {
        return Optional.ofNullable(response.headers().map().get("location"))
            .filter(urls -> !urls.isEmpty())
            .map(List::getFirst)
            .flatMap(this::siteFromUrl)
            .map(redirect -> CrawlResult.success(site, Set.of(redirect)))
            .orElse(CrawlResult.emptySuccess(site));
      }

      if (isStatus2xx(statusCode)) {
        // process
        return CrawlResult.success(site,
            response.body()
            .map(this::extractSitesFromLine)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet()));
      }

      logger.warning(String.format("Received response with unexpected status code %s for site %s", statusCode, site.getUrl()));
      return CrawlResult.emptySuccess(site);
    } catch (IOException | InterruptedException e) {
      // try visiting this page later
      logger.warning(String.format("Error happened for url %s, will retry later, error message: %s", site.getUrl(), e.getMessage()));
      return CrawlResult.failed(site.incrementNumberOfTimesVisitAttemptedAndGet());
    }
  }

  private List<Site> extractSitesFromLine(String line) {
    Matcher matcher = pattern.matcher(line);
    List<Site> urls = new ArrayList<>();
    while (matcher.find()) {
      String url = matcher.group("url");
      siteFromUrl(url).ifPresent(urls::add);
    }

    return urls;
  }

  private Optional<Site> siteFromUrl(String url) {
    URI uri = HttpUtils.validateAndObtainUrlSilent(url);
    if (uri == null) {
      return Optional.empty();
    }

    if (!uri.isAbsolute()) {
      // if relative URL then domain has to be the same
      return Optional.of(new Site(seedSite.getUrl().resolve(uri)));
    }
    else if (HttpUtils.isCorrectScheme(uri) && HttpUtils.isSameDomain(uri, seedSite.getUrl())) {
      return Optional.of(new Site(uri));
    }

    return Optional.empty();
  }

  private static URI validateSeedUrl(String seedUrl) {
    URI url;
    try {
      url = HttpUtils.validateAndObtainUrl(seedUrl);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Valid seed url has to be provided!", e);
    }

    if (!HttpUtils.isCorrectScheme(url)) {
      throw new IllegalArgumentException("Valid seed url has to be provided!");
    }
    return url;
  }

}
