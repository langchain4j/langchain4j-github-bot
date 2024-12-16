package dev.langchain4j.automation;

import com.github.rvesse.airline.annotations.Cli;
import com.github.rvesse.airline.annotations.Command;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHUser;

import java.io.IOException;

@Cli(name = "@bot", commands = {SayHelloCli.SayHello.class})
public class SayHelloCli {

    interface Commands {

        void run(GHEventPayload.IssueComment issueCommentPayload) throws IOException;
    }

    @Command(name = "sayHello")
    static class SayHello implements Commands {

        @Override
        public void run(GHEventPayload.IssueComment issueCommentPayload) throws IOException {
            // TODO check permissions
            GHUser sender = issueCommentPayload.getSender();
            if (sender != null && sender.getLogin() != null) {
                issueCommentPayload.getIssue().comment("Hello, @" + sender.getLogin());
            } else {
                issueCommentPayload.getIssue().comment("Hello stranger!");
            }
        }
    }
}