package org.example.webcrawler.mini;

import java.net.URI;
import java.net.URISyntaxException;

public class HttpUtils {

  public static boolean isStatus2xx(int statusCode) {
    return statusCode >= 200 && statusCode < 300;
  }

  public static boolean isStatus3xx(int statusCode) {
    return statusCode >= 300 && statusCode < 400;
  }

  public static boolean isStatus4xx(int statusCode) {
    return statusCode >= 400 && statusCode < 500;
  }

  public static boolean isStatus5xx(int statusCode) {
    return statusCode >= 500 && statusCode < 600;
  }

  public static URI validateAndObtainUrlSilent(String url) {
    try {
      return validateAndObtainUrl(url);
    } catch (URISyntaxException e) {
      return null;
    }
  }

  public static URI validateAndObtainUrl(String url) throws URISyntaxException {
      return new URI(url);
  }

  public static boolean isCorrectScheme(URI url) {
    return url.getScheme() != null
        && (url.getScheme().equals("https") || url.getScheme().equals("http"));
  }

  public static boolean isSameDomain(URI url, URI base) {
    return base.getHost().equals(url.getHost());
  }
}
