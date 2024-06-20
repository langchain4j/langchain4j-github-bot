package dev.langchain4j.automation;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.PagedIterable;
import org.mockito.junit.jupiter.MockitoExtension;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@GitHubAppTest
@ExtendWith(MockitoExtension.class)
public class PullRequestOpenedTest {

    @Test
    void triageFromChangedFiles() throws IOException {
        given()
                .github(mocks -> {
                    mocks.configFile("langchain4j-github-bot.yml")
                            .fromString("""
                                    features: [ TRIAGE_ISSUES_AND_PULL_REQUESTS ]
                                    triage:
                                      rules:
                                        - files:
                                            - foo/*
                                            - bar/*
                                          labels: [area/test1, area/test2]
                                        - title: keyword
                                          directories:
                                            - foobar
                                          labels: [area/test3]""");
                    PagedIterable<GHPullRequestFileDetail> changedFilesMocks = MockHelper.mockPagedIterable(
                            MockHelper.mockGHPullRequestFileDetail("foo/Something.java"),
                            MockHelper.mockGHPullRequestFileDetail("something/foobar/SomethingElse.java"));
                    when(mocks.pullRequest(527350930).listFiles())
                            .thenReturn(changedFilesMocks);
                })
                .when().payloadFromClasspath("/pullrequest-opened-title-contains-keyword.json")
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {
                    verify(mocks.pullRequest(527350930))
                            .addLabels("area/test1", "area/test2");
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

    @Test
    void triageFromDescription() throws IOException {
        given()
                .github(mocks -> {
                    mocks.configFile("langchain4j-github-bot.yml")
                            .fromString("""
                                    features: [ TRIAGE_ISSUES_AND_PULL_REQUESTS ]
                                    triage:
                                      rules:
                                        - title: keyword
                                          files:
                                            - foo/*
                                            - bar/*
                                          labels: [area/test1, area/test2]
                                        - title: somethingelse
                                          files:
                                            - foobar
                                          labels: [area/test3]""");
                    PagedIterable<GHPullRequestFileDetail> changedFilesMock = MockHelper.mockPagedIterable(
                            MockHelper.mockGHPullRequestFileDetail("pom.xml"));
                    when(mocks.pullRequest(527350930).listFiles())
                            .thenReturn(changedFilesMock);
                })
                .when().payloadFromClasspath("/pullrequest-opened-title-contains-keyword.json")
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {
                    verify(mocks.pullRequest(527350930))
                            .addLabels("area/test1", "area/test2");
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

    @Test
    void triageFromChangedFilesAndDescription() throws IOException {
        given()
                .github(mocks -> {
                    mocks.configFile("langchain4j-github-bot.yml")
                            .fromString("""
                                    features: [ TRIAGE_ISSUES_AND_PULL_REQUESTS ]
                                    triage:
                                      rules:
                                        - title: keyword
                                          files:
                                            - foo/*
                                            - bar/*
                                          labels: [area/test1, area/test2]
                                        - title: somethingelse
                                          files:
                                            - foobar
                                          labels: [area/test3]
                                          allowSecondPass: true""");
                    PagedIterable<GHPullRequestFileDetail> changedFilesMocks = MockHelper.mockPagedIterable(
                            MockHelper.mockGHPullRequestFileDetail("foobar/pom.xml"));
                    when(mocks.pullRequest(527350930).listFiles())
                            .thenReturn(changedFilesMocks);
                })
                .when().payloadFromClasspath("/pullrequest-opened-title-contains-keyword.json")
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {
                    verify(mocks.pullRequest(527350930))
                            .addLabels("area/test1", "area/test2", "area/test3");
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

    @Test
    void triageComment() throws IOException {
        given()
                .github(mocks -> {
                    mocks.configFile("langchain4j-github-bot.yml")
                            .fromString("""
                                    features: [ TRIAGE_ISSUES_AND_PULL_REQUESTS ]
                                    triage:
                                      rules:
                                        - files:
                                            - foo
                                            - bar
                                          comment: 'This is an urgent PR'""");
                    PagedIterable<GHPullRequestFileDetail> changedFilesMocks = MockHelper.mockPagedIterable(
                            MockHelper.mockGHPullRequestFileDetail("foo/Something.java"));
                    when(mocks.pullRequest(527350930).listFiles())
                            .thenReturn(changedFilesMocks);
                })
                .when().payloadFromClasspath("/pullrequest-opened-title-does-not-contain-keyword.json")
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {
                    verify(mocks.pullRequest(527350930))
                            .comment("This is an urgent PR");
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

    @Test
    void triageBasicNotify() throws IOException {
        given().github(mocks -> mocks.configFile("langchain4j-github-bot.yml")
                .fromString("features: [ TRIAGE_ISSUES_AND_PULL_REQUESTS ]\n"
                        + "triage:\n"
                        + "  rules:\n"
                        + "    - title: test\n"
                        + "      notify: [sec-team]\n"
                        + "      comment: 'This is a security issue'\n"
                        + "      notifyInPullRequest: true"))
                .when().payloadFromClasspath("/pullrequest-opened-title-contains-test.json")
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {
                    verify(mocks.pullRequest(527350930))
                            .comment("/cc @sec-team");
                    verify(mocks.pullRequest(527350930))
                            .comment("This is a security issue");
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

    @Test
    void triageIdNotify() throws IOException {
        given().github(mocks -> mocks.configFile("langchain4j-github-bot.yml")
                .fromString("features: [ TRIAGE_ISSUES_AND_PULL_REQUESTS ]\n"
                        + "triage:\n"
                        + "  rules:\n"
                        + "    - id: 'security'\n"
                        + "      title: test\n"
                        + "      notify: [sec-team,gsmet]\n"
                        + "      comment: 'This is a security issue'\n"
                        + "      notifyInPullRequest: true\n"
                        + "    - id: 'devtools'\n"
                        + "      title: test\n"
                        + "      notify: [gsmet]\n"
                        + "      notifyInPullRequest: true"))
                .when().payloadFromClasspath("/pullrequest-opened-title-contains-test.json")
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {
                    verify(mocks.pullRequest(527350930))
                            .comment("This is a security issue");
                    verify(mocks.pullRequest(527350930))
                            .comment("/cc @gsmet (devtools,security), @sec-team (security)");
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }

    @Test
    void triageGlob() throws IOException {
        given()
                .github(mocks -> {
                    mocks.configFile("langchain4j-github-bot.yml")
                            .fromString("""
                                    features: [ TRIAGE_ISSUES_AND_PULL_REQUESTS ]
                                    triage:
                                      rules:
                                        - files:
                                            - '*-azure-*/**'
                                          labels: [area/azure]
                                          notify: [azure-team]
                                          notifyInPullRequest: true""");
                    PagedIterable<GHPullRequestFileDetail> changedFilesMocks = MockHelper.mockPagedIterable(
                            MockHelper.mockGHPullRequestFileDetail("langchain4j-azure-ai-search/Something.java"),
                            MockHelper.mockGHPullRequestFileDetail("something/foobar/SomethingElse.java"));
                    when(mocks.pullRequest(527350930).listFiles())
                            .thenReturn(changedFilesMocks);
                })
                .when().payloadFromClasspath("/pullrequest-opened-title-contains-test.json")
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {
                    verify(mocks.pullRequest(527350930))
                            .addLabels("area/azure");
                    verify(mocks.pullRequest(527350930))
                            .comment("/cc @azure-team");
                    verifyNoMoreInteractions(mocks.ghObjects());
                });
    }
}
