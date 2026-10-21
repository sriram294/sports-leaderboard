package com.org.playboard.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.org.playboard.common.ApiException;
import com.org.playboard.dto.auth.TokenResponse;
import com.org.playboard.dto.group.GroupSummaryDto;
import com.org.playboard.entity.group.GroupRole;
import com.org.playboard.service.group.GroupService;
import com.org.playboard.service.match.MatchService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// Uses a cost-4 BCrypt hash of "reviewer-pass" so the context can enable the login.
@SpringBootTest(properties = {
    "playboard.auth.reviewer.username=play-reviewer",
    "playboard.auth.reviewer.password-hash=$2a$04$cEWDnjnZ4N5HakI5yfPFo.oK0rtEptPdhBeMDv3ksprf2/b2OKe6C"
})
@Transactional
class ReviewerSignInIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private GroupService groupService;
    @Autowired private MatchService matchService;

    @MockitoBean private GoogleTokenVerifier googleTokenVerifier;
    @MockitoBean private AppleTokenVerifier appleTokenVerifier;

    @Test
    void reviewerSignInIssuesTokensAndSeedsDemoGroupOnce() {
        TokenResponse first = authService.signInAsReviewer("play-reviewer", "reviewer-pass");

        assertThat(first.accessToken()).isNotBlank();
        assertThat(first.refreshToken()).isNotBlank();
        assertThat(first.user().authProviders()).isEmpty();
        UUID reviewerId = first.user().id();

        List<GroupSummaryDto> groups = groupService.listGroupsForUser(reviewerId).groups();
        assertThat(groups).singleElement().satisfies(group -> {
            assertThat(group.name()).isEqualTo(ReviewerDemoSeeder.GROUP_NAME);
            assertThat(group.myRole()).isEqualToIgnoringCase(GroupRole.OWNER.name());
        });
        UUID groupId = groups.getFirst().id();
        // Reviewer + three demo members; the shared guests are listed separately.
        assertThat(groupService.listMembers(groupId, reviewerId).members()).hasSize(4);
        assertThat(matchService.listMatches(groupId, reviewerId, null, 50, false).matches()).hasSize(12);

        // A second sign-in resolves the same user and doesn't seed again.
        TokenResponse second = authService.signInAsReviewer("play-reviewer", "reviewer-pass");
        assertThat(second.user().id()).isEqualTo(reviewerId);
        assertThat(groupService.listGroupsForUser(reviewerId).groups()).hasSize(1);
    }

    @Test
    void wrongPasswordIsRejected() {
        assertThatThrownBy(() -> authService.signInAsReviewer("play-reviewer", "nope"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid username or password");
    }
}
