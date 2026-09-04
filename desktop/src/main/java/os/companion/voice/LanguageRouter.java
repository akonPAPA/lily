package os.companion.voice;

public final class LanguageRouter {

    public enum Lang { EN, RU }

    public Lang detect(String text) {
        if (text == null || text.isBlank()) {
            return Lang.EN;
        }
        int cyr = 0;
        int latin = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'Ѐ' && c <= 'ӿ') {
                cyr++;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                latin++;
            }
        }
        if (cyr == 0) {
            return Lang.EN;
        }

        return cyr >= latin ? Lang.RU : (cyr > 0 && cyr * 4 >= latin ? Lang.RU : Lang.EN);
    }
}
