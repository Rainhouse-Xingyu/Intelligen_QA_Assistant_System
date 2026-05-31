package me.rainhouse.qasystem.service;

import java.time.LocalDate;
import java.util.Map;

public interface DataStatService {

    int rebuildHotQuestions(LocalDate statDate);

    int refreshRecentHotQuestions(int days);

    Map<String, Object> getFallbackOverview(int days);
}
