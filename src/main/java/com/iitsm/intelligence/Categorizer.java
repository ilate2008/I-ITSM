package com.iitsm.intelligence;

import com.iitsm.model.Category;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Intelligent feature F: suggests a category from the description via keyword scoring. Extensible via register(). */
public class Categorizer {
    private final Map<Category, Set<String>> keywords = new EnumMap<>(Category.class);

    public Categorizer() {
        register(Category.NETWORK, "wifi", "vpn", "network", "internet", "router", "latency", "dns");
        register(Category.DATABASE, "database", "db", "sql", "query", "deadlock", "replication");
        register(Category.AUTHENTICATION, "login", "password", "sso", "auth", "locked", "mfa", "otp");
        register(Category.SECURITY, "phishing", "malware", "breach", "virus", "attack", "ransomware");
        register(Category.HARDWARE, "laptop", "printer", "keyboard", "monitor", "mouse", "battery");
        register(Category.STORAGE, "storage", "disk", "drive", "quota", "backup", "nas");
        register(Category.CLOUD, "cloud", "aws", "azure", "instance", "bucket", "kubernetes");
        register(Category.APPLICATION, "application", "app", "payment", "transaction", "website", "portal");
        register(Category.SOFTWARE, "install", "software", "license", "update", "crash", "office");
    }

    public void register(Category category, String... words) {
        keywords.computeIfAbsent(category, c -> new HashSet<>()).addAll(Set.of(words));
    }

    public Category suggest(String text) {
        Set<String> tokens = TextUtil.tokens(text);
        Category best = Category.APPLICATION;
        int bestScore = 0;
        for (Map.Entry<Category, Set<String>> e : keywords.entrySet()) {
            int score = (int) e.getValue().stream().filter(tokens::contains).count();
            if (score > bestScore) { best = e.getKey(); bestScore = score; }
        }
        return best;
    }
}
