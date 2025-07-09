package me.zhengjie.modules.charts.service.impl;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.modules.charts.config.JiraProperties;
import me.zhengjie.modules.charts.service.JiraChartService;
import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@AllArgsConstructor
public class JiraChartServiceImpl implements JiraChartService {

    private final JiraProperties jiraProperties;
//    private final String baseUrl = jiraProperties.getBaseUrl();
//    private final String loginJsp = jiraProperties.getLoginJsp();
//    private final String filterStats = jiraProperties.getFilterStats();
    private final CloseableHttpClient httpClient = HttpClients.createDefault();
    private final Map<String, String> cookies = new HashMap<>();

    public boolean login(String username, String password) throws IOException {
        String loginUrl = jiraProperties.getBaseUrl() + jiraProperties.getLoginJsp();

        // Step 1: Get login page to extract CSRF token
        HttpGet getRequest = new HttpGet(loginUrl);
        try (CloseableHttpResponse response = httpClient.execute(getRequest)) {
            String html = EntityUtils.toString(response.getEntity());
            String atlToken = extractAtlToken(html);

            // Step 2: Prepare login form data
            List<NameValuePair> formParams = new ArrayList<>();
            formParams.add(new BasicNameValuePair("os_username", username));
            formParams.add(new BasicNameValuePair("os_password", password));
            formParams.add(new BasicNameValuePair("os_cookie", "on"));
            formParams.add(new BasicNameValuePair("atl_token", atlToken));
            formParams.add(new BasicNameValuePair("login", "登录"));

            // Step 3: Submit login request
            HttpPost postRequest = new HttpPost(loginUrl);
            postRequest.setEntity(new UrlEncodedFormEntity(formParams, "UTF-8"));
            postRequest.setHeader("User-Agent", "Mozilla/5.0");
            postRequest.setHeader("Referer", loginUrl);

            try (CloseableHttpResponse loginResponse = httpClient.execute(postRequest)) {
                // Check for successful login
                boolean success = isLoginSuccessful(loginResponse);
                if (success) {
                    saveCookies(loginResponse);
                    log.info("Login successful for user: {}", username);
                } else {
                    log.warn("Login failed for user: {}", username);
                }
                return success;
            }
        }
    }

    public String getFilterStats(String filterId,
                                 String xstattype,
                                 String ystattype,
                                 String sortDirection,
                                 String sortBy,
                                 int numberToShow) throws IOException, URISyntaxException {

        String apiUrl = jiraProperties.getBaseUrl() + jiraProperties.getFilterStats();

        // Build query parameters
        URIBuilder uriBuilder = new URIBuilder(apiUrl);
        uriBuilder.addParameter("filterId", filterId);
        uriBuilder.addParameter("xstattype", xstattype);
        uriBuilder.addParameter("ystattype", ystattype);
        uriBuilder.addParameter("sortDirection", sortDirection);
        uriBuilder.addParameter("sortBy", sortBy);
        uriBuilder.addParameter("numberToShow", String.valueOf(numberToShow));
        uriBuilder.addParameter("_", String.valueOf(System.currentTimeMillis()));

        HttpGet request = new HttpGet(uriBuilder.build());
        request.setHeader("Accept", "application/json, text/javascript, */*; q=0.01");
        request.setHeader("X-Requested-With", "XMLHttpRequest");
        request.setHeader("User-Agent", "Mozilla/5.0");

        // Add cookies if available
        if (!cookies.isEmpty()) {
            request.setHeader("Cookie", buildCookieHeader());
        }

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                log.error("JIRA API request failed with status: {}", statusCode);
                throw new IOException("JIRA API request failed with status: " + statusCode);
            }
            return EntityUtils.toString(response.getEntity());
        }
    }

    private String extractAtlToken(String html) {
        Document doc = Jsoup.parse(html);
        Element tokenInput = doc.selectFirst("input[name=atl_token]");
        return tokenInput != null ? tokenInput.attr("value") : "";
    }

    private boolean isLoginSuccessful(CloseableHttpResponse response) throws IOException {
        // Check Location header
        Header locationHeader = response.getFirstHeader("Location");
        if (locationHeader != null) {
            return locationHeader.getValue().contains("Dashboard.jspa");
        }

        // Check response content
        String html = EntityUtils.toString(response.getEntity());
        return html.contains("logout") || html.contains("Dashboard.jspa");
    }

    private void saveCookies(CloseableHttpResponse response) {
        Header[] cookieHeaders = response.getHeaders("Set-Cookie");
        for (Header header : cookieHeaders) {
            String cookie = header.getValue().split(";")[0];
            String[] parts = cookie.split("=", 2);
            if (parts.length == 2) {
                cookies.put(parts[0], parts[1]);
            }
        }
    }

    private String buildCookieHeader() {
        StringBuilder sb = new StringBuilder();
        cookies.forEach((name, value) -> sb.append(name).append("=").append(value).append("; "));
        return sb.toString();
    }
}
