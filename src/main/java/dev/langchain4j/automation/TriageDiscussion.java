package dev.langchain4j.automation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepositoryDiscussion;

import dev.langchain4j.automation.config.Feature;
import dev.langchain4j.automation.config.LangChain4jGitHubBotConfig;
import dev.langchain4j.automation.config.LangChain4jGitHubBotConfigFile;
import dev.langchain4j.automation.config.LangChain4jGitHubBotConfigFile.TriageRule;
import dev.langchain4j.automation.util.Labels;
import dev.langchain4j.automation.util.Mentions;
import dev.langchain4j.automation.util.Strings;
import dev.langchain4j.automation.util.Triage;
import io.quarkiverse.githubapp.ConfigFile;
import io.quarkiverse.githubapp.event.Discussion;
import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;

class TriageDiscussion {

    private static final Logger LOG = Logger.getLogger(TriageDiscussion.class);

    @Inject
    LangChain4jGitHubBotConfig langChain4jBotConfig;

    void triageDiscussion(@Discussion.Created @Discussion.CategoryChanged GHEventPayload.Discussion discussionPayload,
            @ConfigFile("langchain4j-github-bot.yml") LangChain4jGitHubBotConfigFile langChain4jBotConfigFile,
            DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
        if (!Feature.TRIAGE_DISCUSSIONS.isEnabled(langChain4jBotConfigFile)) {
            return;
        }

        if (langChain4jBotConfigFile.triage.rules.isEmpty()) {
            return;
        }

        GHRepositoryDiscussion discussion = discussionPayload.getDiscussion();

        if (!langChain4jBotConfigFile.triage.discussions.monitoredCategories.contains(discussion.getCategory().getId())) {
            if (langChain4jBotConfigFile.triage.discussions.logCategories) {
                LOG.info("Discussion category " + discussion.getCategory().getId() + " - " + discussion.getCategory().getName()
                        + " is not monitored, ignoring discussion.");
            }
            return;
        }

        Set<String> labels = new TreeSet<>();
        Mentions mentions = new Mentions();
        List<String> comments = new ArrayList<>();

        for (TriageRule rule : langChain4jBotConfigFile.triage.rules) {
            if (Triage.matchRuleFromDescription(discussion.getTitle(), discussion.getBody(), rule)) {
                if (!rule.labels.isEmpty()) {
                    labels.addAll(rule.labels);
                }
                if (!rule.notify.isEmpty()) {
                    for (String mention : rule.notify) {
                        if (!mention.equals(discussion.getUser().getLogin())) {
                            mentions.add(mention, rule.id);
                        }
                    }
                }
                if (Strings.isNotBlank(rule.comment)) {
                    comments.add(rule.comment);
                }
            }
        }

        if (!labels.isEmpty()) {
            if (!langChain4jBotConfig.isDryRun()) {
                addLabels(gitHubGraphQLClient, discussion, discussionPayload.getRepository(), Labels.limit(labels));
            } else {
                LOG.info("Discussion #" + discussion.getNumber() + " - Add labels: " + String.join(", ", Labels.limit(labels)));
            }
        }

        if (!mentions.isEmpty()) {
            comments.add("/cc " + mentions.getMentionsString());
        }

        for (String comment : comments) {
            if (!langChain4jBotConfig.isDryRun()) {
                addComment(gitHubGraphQLClient, discussion, comment);
            } else {
                LOG.info("Discussion #" + discussion.getNumber() + " - Add comment: " + comment);
            }
        }

        // TODO: we would need to get the labels via GraphQL. For now, let's see if we can avoid one more query.
        //        if (mentions.isEmpty() && !Labels.hasAreaLabels(labels) && !GHIssues.hasAreaLabel(issue)) {
        //            if (!langChain4jBotConfig.isDryRun()) {
        //                issue.addLabels(Labels.TRIAGE_NEEDS_TRIAGE);
        //            } else {
        //                LOG.info("Discussion #" + discussion.getNumber() + " - Add label: " + Labels.TRIAGE_NEEDS_TRIAGE);
        //            }
        //        }
    }

    private static void addLabels(DynamicGraphQLClient gitHubGraphQLClient, GHRepositoryDiscussion discussion,
            GHRepository repository, Collection<String> labels) {
        // unfortunately, we need to get the ids of the labels
        Set<String> labelIds = new HashSet<>();
        for (String label : labels) {
            try {
                labelIds.add(repository.getLabel(label).getNodeId());
            } catch (IOException e) {
                LOG.error("Discussion #" + discussion.getNumber() + " - Unable to get id for label: " + label);
            }
        }

        if (labelIds.isEmpty()) {
            return;
        }

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("labelableId", discussion.getNodeId());
            variables.put("labelIds", labelIds.toArray(new String[0]));

            Response response = gitHubGraphQLClient.executeSync("""
                    mutation AddLabels($labelableId: ID!, $labelIds: [ID!]!) {
                      addLabelsToLabelable(input: {
                        labelableId: $labelableId,
                        labelIds: $labelIds}) {
                            clientMutationId
                      }
                    }""", variables);

            if (response.hasError()) {
                LOG.info("Discussion #" + discussion.getNumber() + " - Unable to add labels: " + String.join(", ", labels)
                        + " - " + response.getErrors());
            }
        } catch (ExecutionException | InterruptedException e) {
            LOG.info("Discussion #" + discussion.getNumber() + " - Unable to add labels: " + String.join(", ", labels));
        }
    }

    private static void addComment(DynamicGraphQLClient gitHubGraphQLClient, GHRepositoryDiscussion discussion,
            String comment) {
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("discussionId", discussion.getNodeId());
            variables.put("comment", comment);

            Response response = gitHubGraphQLClient.executeSync("""
                    mutation AddComment($discussionId: ID!, $comment: String!) {
                      addDiscussionComment(input: {
                        discussionId: $discussionId,
                        body: $comment }) {
                            clientMutationId
                      }
                    }""", variables);

            if (response.hasError()) {
                LOG.info("Discussion #" + discussion.getNumber() + " - Unable to add comment: " + comment
                        + " - " + response.getErrors());
            }
        } catch (ExecutionException | InterruptedException e) {
            LOG.info("Discussion #" + discussion.getNumber() + " - Unable to add comment: " + comment);
        }
    }
}
