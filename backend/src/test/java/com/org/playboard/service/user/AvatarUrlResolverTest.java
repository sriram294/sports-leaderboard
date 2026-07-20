package com.org.playboard.service.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AvatarUrlResolverTest {

    private static final String BASE = "https://playboard-prd.cooperbcknd.in";

    @Test
    void prependsBaseUrlToStoredPath() {
        assertThat(new AvatarUrlResolver(BASE).resolve("/avatars/abc.jpg"))
                .isEqualTo("https://playboard-prd.cooperbcknd.in/avatars/abc.jpg");
    }

    @Test
    void doesNotDoubleUpSlashWhenBaseUrlHasTrailingSlash() {
        assertThat(new AvatarUrlResolver(BASE + "/").resolve("/avatars/abc.jpg"))
                .isEqualTo("https://playboard-prd.cooperbcknd.in/avatars/abc.jpg");
    }

    @Test
    void passesThroughLegacyAbsoluteUrls() {
        // Rows written before V10 keep their host so photos survive a partial rollout.
        String legacy = "https://playboard-prd.up.railway.app/avatars/abc.jpg";
        assertThat(new AvatarUrlResolver(BASE).resolve(legacy)).isEqualTo(legacy);
    }

    @Test
    void returnsNullWhenUserHasNoPhoto() {
        assertThat(new AvatarUrlResolver(BASE).resolve(null)).isNull();
        assertThat(new AvatarUrlResolver(BASE).resolve("  ")).isNull();
    }

    @Test
    void movingDomainsChangesTheUrlWithoutTouchingStoredData() {
        String stored = "/avatars/abc.jpg";
        assertThat(new AvatarUrlResolver("https://old.example.com").resolve(stored))
                .isEqualTo("https://old.example.com/avatars/abc.jpg");
        assertThat(new AvatarUrlResolver("https://new.example.com").resolve(stored))
                .isEqualTo("https://new.example.com/avatars/abc.jpg");
    }
}
