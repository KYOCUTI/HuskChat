package net.william278.huskchat.filter;

import net.william278.huskchat.player.Player;
import net.william278.huskchat.player.TestPlayer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ProfanityFilterTests {

    private final ProfanityFilterer filterer = new ProfanityFilterer(
            ProfanityFilterer.ProfanityFilterMode.TOLERANCE, 0.8d, null);

    @Test
    public void givenSentenceContainingProfanity_testIsProfane() {
        final Player dummyPlayer = new TestPlayer();
        Assertions.assertFalse(filterer.isAllowed(dummyPlayer, "This is a fucking test sentence"));
        Assertions.assertFalse(filterer.isAllowed(dummyPlayer, "Shit"));
    }

    @Test
    public void givenNormalSentences_testIsNotProfane() {
        final Player dummyPlayer = new TestPlayer();
        Assertions.assertTrue(filterer.isAllowed(dummyPlayer, "AHOJ"));
        Assertions.assertTrue(filterer.isAllowed(dummyPlayer, "Hello"));
    }

    @Test
    public void givenObfuscatedProfanity_testIsProfane() {
        final Player dummyPlayer = new TestPlayer();
        Assertions.assertFalse(filterer.isAllowed(dummyPlayer, "You're a fuck1ng idiot"));
        Assertions.assertFalse(filterer.isAllowed(dummyPlayer, "Shut the h3ll up"));
    }

    @Test
    public void givenScunthorpe_testIsNotProfane() {
        final Player dummyPlayer = new TestPlayer();
        Assertions.assertTrue(filterer.isAllowed(dummyPlayer, "Scunthorpe"));
    }

    @Test
    public void givenLeetSpeak_testIsNotProfane() {
        final Player dummyPlayer = new TestPlayer();
        Assertions.assertTrue(filterer.isAllowed(dummyPlayer, "1337"));
    }
}