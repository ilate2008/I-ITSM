package com.iitsm.intelligence;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TextUtil {
    private static final Set<String> STOP = Set.of("the", "a", "an", "is", "are", "to", "and", "of", "in", "on", "for",
            "unable", "not", "my", "i", "it", "be", "can", "cannot", "cant", "with", "from", "we", "our", "has", "have", "was", "all");
    private static final Pattern WORD = Pattern.compile("[a-z0-9]+");

    private TextUtil() { }

    static Set<String> tokens(String text) {
        Set<String> out = new HashSet<>();
        Matcher m = WORD.matcher(text.toLowerCase());
        while (m.find()) {
            String w = m.group();
            if (STOP.contains(w)) continue;
            out.add(w.length() > 3 && w.endsWith("s") ? w.substring(0, w.length() - 1) : w);
        }
        return out;
    }
}
