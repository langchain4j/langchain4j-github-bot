package dev.langchain4j.automation.el;

import dev.langchain4j.automation.util.Patterns;
import dev.langchain4j.automation.util.Strings;

public class Matcher {

    public static boolean matches(String pattern, String string) {
        if (Strings.isNotBlank(string)) {
            return Patterns.find(pattern, string);
        }

        return false;
    }

    private Matcher() {
    }
}
