package dev.langchain4j.automation;

import java.io.IOException;
import java.util.Locale;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHLabel;

import dev.langchain4j.automation.config.Feature;
import dev.langchain4j.automation.config.LangChain4jGitHubBotConfig;
import dev.langchain4j.automation.config.LangChain4jGitHubBotConfigFile;
import dev.langchain4j.automation.util.Labels;
import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.Label;

public class SetAreaLabelColor {

    private static final Logger LOG = Logger.getLogger(SetAreaLabelColor.class);

    @Inject
    LangChain4jGitHubBotConfig langChain4jBotConfig;

    private static final String AREA_LABEL_COLOR = "0366d6";

    void setAreaLabelColor(@Label.Created GHEventPayload.Label labelPayload,
            @ConfigFile("langchain4j-github-bot.yml") LangChain4jGitHubBotConfigFile langChain4jBotConfigFile)
            throws IOException {
        if (!Feature.SET_AREA_LABEL_COLOR.isEnabled(langChain4jBotConfigFile)) {
            return;
        }

        GHLabel label = labelPayload.getLabel();

        if (!label.getName().startsWith(Labels.AREA_PREFIX)
                || AREA_LABEL_COLOR.equals(label.getColor().toLowerCase(Locale.ROOT))) {
            return;
        }

        if (!langChain4jBotConfig.isDryRun()) {
            label.set().color(AREA_LABEL_COLOR);
        } else {
            LOG.info("Label " + label.getName() + " - Set color: #" + AREA_LABEL_COLOR);
        }
    }
}
