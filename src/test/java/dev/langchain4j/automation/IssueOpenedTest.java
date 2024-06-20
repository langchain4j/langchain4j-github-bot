package dev.langchain4j.automation;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEvent;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@GitHubAppTest
public class IssueOpenedTest {

    @Test
    void triage() throws IOException {
        given().github(mocks -> mocks.configFile("langchain4j-github-bot.yml")
                .fromString("features: [ ALL ]\n"
                        + "triage:\n"
                        + "  rules:\n"
                        + "    - title: test\n"
                        + "      labels: [area/test1, area/test2]"))
                .when().payloadFromClasspath("/issue-opened.json")
                .event(GHEvent.ISSUES)
                .then().github(mocks -> {
                    verify(mocks.issue(750705278))
                            .addLabels("area/test1", "area/test2");
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

    @Test
    void triageComment() throws IOException {
        given().github(mocks -> mocks.configFile("langchain4j-github-bot.yml")
                .fromString("features: [ ALL ]\n"
                        + "triage:\n"
                        + "  rules:\n"
                        + "    - title: test\n"
                        + "      comment: 'This is a security issue'"))
                .when().payloadFromClasspath("/issue-opened.json")
                .event(GHEvent.ISSUES)
                .then().github(mocks -> {
                    verify(mocks.issue(750705278))
                            .comment("This is a security issue");
                    // Not implemented for now
                    //                    verify(mocks.issue(750705278))
                    //                            .addLabels(Labels.TRIAGE_NEEDS_TRIAGE);
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

    @Test
    void triageBasicNotify() throws IOException {
        given().github(mocks -> mocks.configFile("langchain4j-github-bot.yml")
                .fromString("features: [ ALL ]\n"
                        + "triage:\n"
                        + "  rules:\n"
                        + "    - title: test\n"
                        + "      notify: [sec-team]\n"
                        + "      comment: 'This is a security issue'"))
                .when().payloadFromClasspath("/issue-opened.json")
                .event(GHEvent.ISSUES)
                .then().github(mocks -> {
                    verify(mocks.issue(750705278))
                            .comment("/cc @sec-team");
                    verify(mocks.issue(750705278))
                            .comment("This is a security issue");
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

    @Test
    void triageIdNotify() throws IOException {
        given().github(mocks -> mocks.configFile("langchain4j-github-bot.yml")
                .fromString("features: [ ALL ]\n"
                        + "triage:\n"
                        + "  rules:\n"
                        + "    - id: 'security'\n"
                        + "      title: test\n"
                        + "      notify: [sec-team,gsmet]\n"
                        + "      comment: 'This is a security issue'\n"
                        + "    - id: 'devtools'\n"
                        + "      title: test\n"
                        + "      notify: [gsmet]\n"))
                .when().payloadFromClasspath("/issue-opened.json")
                .event(GHEvent.ISSUES)
                .then().github(mocks -> {
                    verify(mocks.issue(750705278))
                            .comment("This is a security issue");
                    verify(mocks.issue(750705278)).comment("/cc @gsmet (devtools,security), @sec-team (security)");
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

}
