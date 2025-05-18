package org.example.webcrawler.mini;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.example.webcrawler.mini.config.CrawlingConfig;
import org.example.webcrawler.mini.config.HttpConfig;
import org.example.webcrawler.mini.crawler.WebCrawler;

public class WebCrawlerApp {

  private static final Logger logger = Logger.getLogger(WebCrawlerApp.class.getName());

  public static void main(String[] args) {

    HttpConfig httpConfig = new HttpConfig(10, 10);
    CrawlingConfig crawlingConfig = new CrawlingConfig(5, 3, 20, 12);

    try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
      WebCrawler webCrawler = new WebCrawler(httpConfig, crawlingConfig, executorService);

      String seedUrl = "https://www.google.com";
      webCrawler.crawl(seedUrl);

      logger.info(webCrawler.getSortedVisitedSites());
    }
  }
}
