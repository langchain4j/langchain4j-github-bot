package dev.langchain4j.automation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;

import dev.langchain4j.automation.config.Feature;
import dev.langchain4j.automation.config.LangChain4jGitHubBotConfig;
import dev.langchain4j.automation.config.LangChain4jGitHubBotConfigFile;
import dev.langchain4j.automation.config.LangChain4jGitHubBotConfigFile.TriageRule;
import dev.langchain4j.automation.util.GHIssues;
import dev.langchain4j.automation.util.Mentions;
import dev.langchain4j.automation.util.Strings;
import dev.langchain4j.automation.util.Triage;
import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.PullRequest;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;

class TriagePullRequest {

    private static final Logger LOG = Logger.getLogger(TriagePullRequest.class);

    /**
     * We cannot add more than 100 labels and we have some other automatic labels such as kind/bug.
     */
    private static final int LABEL_SIZE_LIMIT = 95;

    @Inject
    LangChain4jGitHubBotConfig langChain4jBotConfig;

    void triagePullRequest(
            @PullRequest.Opened @PullRequest.Edited @PullRequest.Synchronize GHEventPayload.PullRequest pullRequestPayload,
            @ConfigFile("langchain4j-github-bot.yml") LangChain4jGitHubBotConfigFile langChain4jBotConfigFile,
            DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
        if (!Feature.TRIAGE_ISSUES_AND_PULL_REQUESTS.isEnabled(langChain4jBotConfigFile)) {
            return;
        }

        if (langChain4jBotConfigFile.triage.rules.isEmpty()) {
            return;
        }

        GHPullRequest pullRequest = pullRequestPayload.getPullRequest();
        Set<String> labels = new TreeSet<>();
        Mentions mentions = new Mentions();
        List<String> comments = new ArrayList<>();
        // The second pass is allowed if either:
        // - no rule matched in the first pass
        // - OR all matching rules from the first pass explicitly allow the second pass
        boolean allowSecondPass = true;

        for (TriageRule rule : langChain4jBotConfigFile.triage.rules) {
            if (Triage.matchRuleFromChangedFiles(pullRequest, rule)) {
                allowSecondPass = allowSecondPass && rule.allowSecondPass;
                applyRule(pullRequestPayload, pullRequest, rule, labels, mentions, comments);
            }
        }

        if (allowSecondPass) {
            // Do a second pass, triaging according to the PR title/body
            for (TriageRule rule : langChain4jBotConfigFile.triage.rules) {
                if (Triage.matchRuleFromDescription(pullRequest.getTitle(), pullRequest.getBody(), rule)) {
                    applyRule(pullRequestPayload, pullRequest, rule, labels, mentions, comments);
                }
            }
        }

        // remove from the set the labels already present on the pull request
        pullRequest.getLabels().stream().map(GHLabel::getName).forEach(labels::remove);

        if (!labels.isEmpty()) {
            if (!langChain4jBotConfig.isDryRun()) {
                pullRequest.addLabels(limit(labels).toArray(new String[0]));
            } else {
                LOG.info("Pull Request #" + pullRequest.getNumber() + " - Add labels: " + String.join(", ", limit(labels)));
            }
        }

        mentions.removeAlreadyParticipating(GHIssues.getParticipatingUsers(pullRequest, gitHubGraphQLClient));
        if (!mentions.isEmpty()) {
            comments.add("/cc " + mentions.getMentionsString());
        }

        for (String comment : comments) {
            if (!langChain4jBotConfig.isDryRun()) {
                pullRequest.comment(comment);
            } else {
                LOG.info("Pull Request #" + pullRequest.getNumber() + " - Add comment: " + comment);
            }
        }
    }

    private void applyRule(GHEventPayload.PullRequest pullRequestPayload, GHPullRequest pullRequest,
            TriageRule rule, Set<String> labels, Mentions mentions, List<String> comments) throws IOException {
        if (!rule.labels.isEmpty()) {
            labels.addAll(rule.labels);
        }

        if (!rule.notify.isEmpty() && rule.notifyInPullRequest
                && PullRequest.Opened.NAME.equals(pullRequestPayload.getAction())) {
            for (String mention : rule.notify) {
                if (!mention.equals(pullRequest.getUser().getLogin())) {
                    mentions.add(mention, rule.id);
                }
            }
        }
        if (Strings.isNotBlank(rule.comment)) {
            comments.add(rule.comment);
        }
    }

    private static Collection<String> limit(Set<String> labels) {
        if (labels.size() <= LABEL_SIZE_LIMIT) {
            return labels;
        }

        return new ArrayList<>(labels).subList(0, LABEL_SIZE_LIMIT);
    }

}
