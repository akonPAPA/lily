package os.companion.voice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageRouterTest {

    private final LanguageRouter router = new LanguageRouter();

    @Test
    void plainEnglishIsEn() {
        assertThat(router.detect("Hello there, how are you?")).isEqualTo(LanguageRouter.Lang.EN);
    }

    @Test
    void plainRussianIsRu() {
        assertThat(router.detect("Привет, как у тебя дела сегодня?")).isEqualTo(LanguageRouter.Lang.RU);
    }

    @Test
    void mixedWithRealCyrillicPrefersRu() {
        assertThat(router.detect("ok давай попробуем")).isEqualTo(LanguageRouter.Lang.RU);
    }

    @Test
    void blankOrNullIsEn() {
        assertThat(router.detect("")).isEqualTo(LanguageRouter.Lang.EN);
        assertThat(router.detect(null)).isEqualTo(LanguageRouter.Lang.EN);
    }

    @Test
    void digitsAndPunctuationOnlyIsEn() {
        assertThat(router.detect("123 — 456!")).isEqualTo(LanguageRouter.Lang.EN);
    }
}
