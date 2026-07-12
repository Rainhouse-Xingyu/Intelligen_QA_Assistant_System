package me.rainhouse.qasystem.service;

import java.util.List;

public interface IntentClassifierService {

    String classify(String query, String userSelectedModule);

    default List<String> classifyCandidates(String query, String userSelectedModule) {
        String module = classify(query, userSelectedModule);
        return module == null || module.isBlank() ? List.of() : List.of(module);
    }
}
