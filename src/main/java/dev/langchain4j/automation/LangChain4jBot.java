package dev.langchain4j.automation;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import dev.langchain4j.automation.config.LangChain4jGitHubBotConfig;
import io.quarkus.runtime.StartupEvent;

public class LangChain4jBot {

    private static final Logger LOG = Logger.getLogger(LangChain4jBot.class);

    @Inject
    LangChain4jGitHubBotConfig langChain4jBotConfig;

    void init(@Observes StartupEvent startupEvent) {
        if (langChain4jBotConfig.isDryRun()) {
            LOG.warn("››› Quarkus Bot running in dry-run mode");
        }
    }
}
