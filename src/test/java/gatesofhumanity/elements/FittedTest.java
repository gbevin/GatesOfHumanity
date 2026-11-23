package gatesofhumanity.elements;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FittedTest {
    @Test
    void aTextThatFitsStaysAsItIs() {
        assertEquals(new Fitted(List.of("Insight"), 15), Fitted.of("Insight", 14, 15, 11, 2));
        assertEquals(new Fitted(List.of("Self-Expression"), 15), Fitted.of("Self-Expression", 15, 15, 11, 1));
    }

    @Test
    void aTextThatsTooLongForALineGoesOnTwoWhereThereAreTwo() {
        assertEquals(new Fitted(List.of("The Spirit of", "the Self"), 15), Fitted.of("The Spirit of the Self", 14, 15, 11, 2));
        // a word alone is longer than a line: at 14 a line has room for 15 letters
        assertEquals(new Fitted(List.of("The", "Hunter/Huntress"), 14), Fitted.of("The Hunter/Huntress", 14, 15, 11, 2));
    }

    @Test
    void lettersGetSmallerUntilTheTextFits() {
        // 14 letters at 15 are 15 at 14, 16 at 13 and 17 at 12
        assertEquals(new Fitted(List.of("Rationalization"), 14), Fitted.of("Rationalization", 14, 15, 11, 1));
        assertEquals(new Fitted(List.of("Intuitive Clarity"), 12), Fitted.of("Intuitive Clarity", 14, 15, 11, 1));
        // a line breaks between words, and the second one has 19 letters, which fit at 11
        assertEquals(new Fitted(List.of("The Role of the", "Self in Interaction"), 11), Fitted.of("The Role of the Self in Interaction", 14, 15, 11, 2));
    }

    @Test
    void whatDoesntFitAtTheSmallestSizeIsCutShort() {
        // 19 letters at 11
        assertEquals(new Fitted(List.of("The Role of the Se…"), 11), Fitted.of("The Role of the Self in Interaction", 14, 15, 11, 1));
        assertEquals(new Fitted(List.of("The Determination", "of the Self and so…"), 11), Fitted.of("The Determination of the Self and so on", 14, 15, 11, 2));
        // a word that's longer than a line goes on where the line ends
        assertEquals(new Fitted(List.of("Antidisestablishmen", "tarianism"), 11), Fitted.of("Antidisestablishmentarianism", 14, 15, 11, 2));
    }
}
