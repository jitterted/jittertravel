package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageWindowTest {

    private static final int PAGE_SIZE = 50;

    @Nested
    class NewestFirst {

        @Test
        void firstPageIsTheLastWindowSoNewestCommandsAppearFirst() {
            PageWindow window = new PageWindow(120, PAGE_SIZE, 0, true);

            assertThat(window.offset())
                    .isEqualTo(70);
            assertThat(window.limit())
                    .isEqualTo(PAGE_SIZE);
        }

        @Test
        void middlePageCountsBackAnotherFullWindow() {
            PageWindow window = new PageWindow(120, PAGE_SIZE, 1, true);

            assertThat(window.offset())
                    .isEqualTo(20);
            assertThat(window.limit())
                    .isEqualTo(PAGE_SIZE);
        }

        @Test
        void lastPageIsTruncatedToTheRemainingOldestCommands() {
            PageWindow window = new PageWindow(120, PAGE_SIZE, 2, true);

            assertThat(window.offset())
                    .isZero();
            assertThat(window.limit())
                    .isEqualTo(20);
        }

        @Test
        void exactMultipleOfPageSizeLeavesNoPartialPage() {
            PageWindow window = new PageWindow(100, PAGE_SIZE, 1, true);

            assertThat(window.offset())
                    .isZero();
            assertThat(window.limit())
                    .isEqualTo(PAGE_SIZE);
        }

        @Test
        void singlePartialPageStartsAtTheBeginningOfTheLog() {
            PageWindow window = new PageWindow(7, PAGE_SIZE, 0, true);

            assertThat(window.offset())
                    .isZero();
            assertThat(window.limit())
                    .isEqualTo(7);
        }

        @Test
        void emptyLogRequestsNothing() {
            PageWindow window = new PageWindow(0, PAGE_SIZE, 0, true);

            assertThat(window.offset())
                    .isZero();
            assertThat(window.limit())
                    .isZero();
        }
    }

    @Nested
    class OldestFirst {

        @Test
        void firstPageStartsAtTheBeginningOfTheLog() {
            PageWindow window = new PageWindow(120, PAGE_SIZE, 0, false);

            assertThat(window.offset())
                    .isZero();
            assertThat(window.limit())
                    .isEqualTo(PAGE_SIZE);
        }

        @Test
        void laterPagesAdvanceByWholeWindows() {
            PageWindow window = new PageWindow(120, PAGE_SIZE, 2, false);

            assertThat(window.offset())
                    .isEqualTo(100);
            assertThat(window.limit())
                    .isEqualTo(PAGE_SIZE);
        }

        @Test
        void emptyLogStartsAtTheBeginningOfTheLog() {
            PageWindow window = new PageWindow(0, PAGE_SIZE, 0, false);

            assertThat(window.offset())
                    .isZero();
            assertThat(window.limit())
                    .isEqualTo(PAGE_SIZE);
        }
    }
}
