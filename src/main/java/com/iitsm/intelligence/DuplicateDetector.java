package com.iitsm.intelligence;

import com.iitsm.model.Incident;
import com.iitsm.model.RelatedIncident;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Intelligent features A/B: score = Jaccard(token sets) + 0.35 if same service (and at least one shared word). */
public class DuplicateDetector {
    private final double threshold;
    public DuplicateDetector(double threshold) { this.threshold = threshold; }
    public DuplicateDetector() { this(0.4); }

    public double score(String textA, String serviceA, String textB, String serviceB) {
        Set<String> a = TextUtil.tokens(textA), b = TextUtil.tokens(textB);
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> inter = new HashSet<>(a); inter.retainAll(b);
        Set<String> union = new HashSet<>(a); union.addAll(b);
        double jaccard = (double) inter.size() / union.size();
        return jaccard + (serviceA.equalsIgnoreCase(serviceB) && !inter.isEmpty() ? 0.35 : 0);
    }

    public List<RelatedIncident> findSimilar(Incident incident, List<Incident> candidates) {
        String text = incident.getTitle() + " " + incident.getDescription();
        List<RelatedIncident> hits = new ArrayList<>();
        for (Incident c : candidates) {
            if (c.getId().equals(incident.getId())) continue;
            double s = score(text, incident.getService(), c.getTitle() + " " + c.getDescription(), c.getService());
            if (s >= threshold) hits.add(new RelatedIncident(c.getId(), Math.round(s * 100) / 100.0));
        }
        hits.sort(Comparator.comparingDouble(RelatedIncident::score).reversed());
        return hits;
    }
}
