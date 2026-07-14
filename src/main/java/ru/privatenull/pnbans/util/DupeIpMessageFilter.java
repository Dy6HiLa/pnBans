package ru.privatenull.pnbans.util;

import java.util.ArrayList;
import java.util.List;

final class DupeIpMessageFilter {
    private DupeIpMessageFilter() { }

    static List<String> withoutCoordinates(List<String> lines) {
        List<String> filtered = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (!line.contains("{location}") && !line.contains("{related_location}")) {
                filtered.add(line);
                continue;
            }
            String[] sections = line.split("\\|", -1);
            List<String> kept = new ArrayList<>(sections.length);
            for (String section : sections) {
                if (!section.contains("{location}") && !section.contains("{related_location}")) {
                    kept.add(section);
                }
            }
            if (!kept.isEmpty() && sections.length > 1) filtered.add(String.join("|", kept));
        }
        return filtered;
    }
}
