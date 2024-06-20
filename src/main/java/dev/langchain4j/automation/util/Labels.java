package dev.langchain4j.automation.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import org.kohsuke.github.GHLabel;

public class Labels {

    /**
     * We cannot add more than 100 labels and we have some other automatic labels such as kind/bug.
     */
    private static final int LABEL_SIZE_LIMIT = 95;

    public static final String AREA_PREFIX = "area/";

    public static final String TRIAGE_NEEDS_TRIAGE = "triage/needs-triage";

    private Labels() {
    }

    public static boolean hasAreaLabels(Set<String> labels) {
        for (String label : labels) {
            if (label.startsWith(Labels.AREA_PREFIX)) {
                return true;
            }
        }

        return false;
    }

    public static Collection<String> limit(Set<String> labels) {
        if (labels.size() <= LABEL_SIZE_LIMIT) {
            return labels;
        }

        return new ArrayList<>(labels).subList(0, LABEL_SIZE_LIMIT);
    }

    public static boolean matchesName(Collection<String> labels, String labelCandidate) {
        for (String label : labels) {
            if (label.equals(labelCandidate)) {
                return true;
            }
        }

        return false;
    }

    public static boolean matches(Collection<GHLabel> labels, String labelCandidate) {
        for (GHLabel label : labels) {
            if (label.getName().equals(labelCandidate)) {
                return true;
            }
        }

        return false;
    }
}
