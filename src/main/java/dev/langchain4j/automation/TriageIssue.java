package dev.langchain4j.automation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;

import dev.langchain4j.automation.config.Feature;
import dev.langchain4j.automation.config.LangChain4jGitHubBotConfig;
import dev.langchain4j.automation.config.LangChain4jGitHubBotConfigFile;
import dev.langchain4j.automation.config.LangChain4jGitHubBotConfigFile.TriageRule;
import dev.langchain4j.automation.util.GHIssues;
import dev.langchain4j.automation.util.Labels;
import dev.langchain4j.automation.util.Mentions;
import dev.langchain4j.automation.util.Strings;
import dev.langchain4j.automation.util.Triage;
import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.Issue;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;

class TriageIssue {

    private static final Logger LOG = Logger.getLogger(TriageIssue.class);

    @Inject
    LangChain4jGitHubBotConfig langChain4jBotConfig;

    void triageIssue(@Issue.Opened GHEventPayload.Issue issuePayload,
            @ConfigFile("langchain4j-github-bot.yml") LangChain4jGitHubBotConfigFile langChain4jBotConfigFile,
            DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
        if (!Feature.TRIAGE_ISSUES_AND_PULL_REQUESTS.isEnabled(langChain4jBotConfigFile)) {
            return;
        }

        if (langChain4jBotConfigFile.triage.rules.isEmpty()) {
            return;
        }

        GHIssue issue = issuePayload.getIssue();
        Set<String> labels = new TreeSet<>();
        Mentions mentions = new Mentions();
        List<String> comments = new ArrayList<>();

        for (TriageRule rule : langChain4jBotConfigFile.triage.rules) {
            if (Triage.matchRuleFromDescription(issue.getTitle(), issue.getBody(), rule)) {
                if (!rule.labels.isEmpty()) {
                    labels.addAll(rule.labels);
                }
                if (!rule.notify.isEmpty()) {
                    for (String mention : rule.notify) {
                        if (!mention.equals(issue.getUser().getLogin())) {
                            mentions.add(mention, rule.id);
                        }
                    }
                }
                if (Strings.isNotBlank(rule.comment)) {
                    comments.add(rule.comment);
                }
            }
        }

        // remove from the set the labels already present on the pull request
        issue.getLabels().stream().map(GHLabel::getName).forEach(labels::remove);

        if (!labels.isEmpty()) {
            if (!langChain4jBotConfig.isDryRun()) {
                issue.addLabels(Labels.limit(labels).toArray(new String[0]));
            } else {
                LOG.info("Issue #" + issue.getNumber() + " - Add labels: " + String.join(", ", Labels.limit(labels)));
            }
        }

        mentions.removeAlreadyParticipating(GHIssues.getParticipatingUsers(issue, gitHubGraphQLClient));
        if (!mentions.isEmpty()) {
            comments.add("/cc " + mentions.getMentionsString());
        }

        for (String comment : comments) {
            if (!langChain4jBotConfig.isDryRun()) {
                issue.comment(comment);
            } else {
                LOG.info("Issue #" + issue.getNumber() + " - Add comment: " + comment);
            }
        }

        // For now, let's not be too intrusive
        //        if (mentions.isEmpty() && !Labels.hasAreaLabels(labels) && !GHIssues.hasAreaLabel(issue)) {
        //            if (!langchain4jBotConfig.isDryRun()) {
        //                issue.addLabels(Labels.TRIAGE_NEEDS_TRIAGE);
        //            } else {
        //                LOG.info("Issue #" + issue.getNumber() + " - Add label: " + Labels.TRIAGE_NEEDS_TRIAGE);
        //            }
        //        }
    }
}
