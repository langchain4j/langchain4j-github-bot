package dev.langchain4j.automation.config;

public enum Feature {

    ALL,
    SET_AREA_LABEL_COLOR,
    TRIAGE_ISSUES_AND_PULL_REQUESTS,
    TRIAGE_DISCUSSIONS;

    public boolean isEnabled(LangChain4jGitHubBotConfigFile langChain4jBotConfigFile) {
        if (langChain4jBotConfigFile == null) {
            return false;
        }

        return langChain4jBotConfigFile.isFeatureEnabled(this);
    }
}
