package com.org.playboard.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DefaultAvatarsTest {

    @Test
    void catalogHasTwentyFiveDistinctIds() {
        assertThat(DefaultAvatars.IDS).hasSize(25).doesNotHaveDuplicates();
    }

    @Test
    void pickRandomReturnsAKnownId() {
        for (int i = 0; i < 100; i++) {
            assertThat(DefaultAvatars.isValid(DefaultAvatars.pickRandom())).isTrue();
        }
    }

    @Test
    void isValidRejectsUnknownAndNull() {
        assertThat(DefaultAvatars.isValid(null)).isFalse();
        assertThat(DefaultAvatars.isValid("")).isFalse();
        assertThat(DefaultAvatars.isValid("nope")).isFalse();
    }
}
