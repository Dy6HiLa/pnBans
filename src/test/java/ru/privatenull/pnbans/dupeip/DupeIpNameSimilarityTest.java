package ru.privatenull.pnbans.dupeip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DupeIpNameSimilarityTest {

    @Test
    void recognizesNicknameWithSharedStableRoot() {
        double score = DupeIpService.nameSimilarity("privatenull_test", "privatenull");

        assertTrue(score >= 0.95D, "shared nickname root must be recognized, actual=" + score);
    }

    @Test
    void recognizesSeparatorAndCaseVariants() {
        double score = DupeIpService.nameSimilarity("Private_Null", "privatenull");

        assertTrue(score >= 0.99D, "case and separators must not reduce similarity, actual=" + score);
    }

    @Test
    void doesNotMarkUnrelatedNamesAsSimilar() {
        double score = DupeIpService.nameSimilarity("privatenull", "diamondsteve");

        assertTrue(score < 0.50D, "unrelated nicknames must remain low, actual=" + score);
    }
}
