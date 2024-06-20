package dev.langchain4j.automation.config;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "langchain4j-github-bot")
public interface LangChain4jGitHubBotConfig {

    Optional<Boolean> dryRun();

    public default boolean isDryRun() {
        Optional<Boolean> dryRun = dryRun();
        return dryRun.isPresent() && dryRun.get();
    }
}
