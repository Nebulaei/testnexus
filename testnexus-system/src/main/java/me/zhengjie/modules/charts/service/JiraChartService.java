package me.zhengjie.modules.charts.service;

import java.io.IOException;
import java.net.URISyntaxException;

public interface JiraChartService {

    boolean login(String username, String password) throws IOException;

    String getFilterStats(String filterId,
                          String xstattype,
                          String ystattype,
                          String sortDirection,
                          String sortBy,
                          int numberToShow) throws IOException, URISyntaxException;
}