package com.RegalManiac.addon.utils;

import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.text.*;
import net.minecraft.text.CharacterVisitor;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.TextColor;

import java.util.Arrays;
import java.util.List;

public class TextUtils {

    public static MutableText coloredText(String text, Color color) {
        return Text.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color.getPacked())));
    }

    private static final TextColor[] RAINBOW_COLORS = new TextColor[256];
    static {
        for (int i = 0; i < 256; i++) {
            RAINBOW_COLORS[i] = TextColor.fromRgb(java.awt.Color.HSBtoRGB(i / 255.0F, 1.0F, 1.0F) & 0xFFFFFF);
        }
    }

    private static final ThreadLocal<StatefulVisitor> VISITOR_CACHE = ThreadLocal.withInitial(StatefulVisitor::new);

    public static OrderedText create(OrderedText original, double[] hueOffsets, double speedMult) {
        return new RainbowText(original, hueOffsets, speedMult);
    }

    private static class RainbowText implements OrderedText {
        private final OrderedText original;
        private final double[] hueOffsets;
        private final double speedMult;

        public RainbowText(OrderedText original, double[] hueOffsets, double speedMult) {
            this.original = original;
            this.hueOffsets = hueOffsets;
            this.speedMult = speedMult;
        }

        @Override
        public boolean accept(CharacterVisitor visitor) {
            double timeHue = (System.currentTimeMillis() * speedMult * 0.0276) % 1.0;

            StatefulVisitor statefulVisitor = VISITOR_CACHE.get();
            statefulVisitor.setup(visitor, hueOffsets, timeHue);
            return original.accept(statefulVisitor);
        }
    }

    private static class StatefulVisitor implements CharacterVisitor {
        private CharacterVisitor target;
        private double[] hueOffsets;
        private double timeHue;
        private int charIdx;

        public void setup(CharacterVisitor target, double[] hueOffsets, double timeHue) {
            this.target = target;
            this.hueOffsets = hueOffsets;
            this.timeHue = timeHue;
            this.charIdx = 0;
        }

        @Override
        public boolean accept(int index, Style style, int cp) {
            int i = charIdx;
            charIdx += Character.charCount(cp);

            if (i < hueOffsets.length && hueOffsets[i] != 999.0) {
                double hue = timeHue - hueOffsets[i];
                float normalizedHue = (float) (hue - Math.floor(hue));
                if (normalizedHue < 0) normalizedHue += 1.0f;

                int hueIndex = (int) (normalizedHue * 255);
                if (hueIndex < 0) hueIndex = 0;
                if (hueIndex > 255) hueIndex = 255;

                style = style.withColor(RAINBOW_COLORS[hueIndex]);
            }
            return target.accept(index, style, cp);
        }
    }

    public enum Fonts {
        DEFAULT("Default",
            "0|1|2|3|4|5|6|7|8|9|a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v|w|x|y|z|а|б|в|г|д|е|ё|ж|з|и|й|к|л|м|н|о|п|р|с|т|у|ф|х|ц|ч|ш|щ|ъ|ы|ь|э|ю|я"),

        KANJI("Kanji",
            "⼝|⺅|⼰|⼹|ㄐ|ㄎ|ꡥ|㇇|⽇|꡴|卂|乃|匚|ᗪ|乇|千|Ꮆ|卄|丨|ﾌ|Ҝ|ㄥ|爪|几|ㄖ|卩|Ɋ|尺|丂|ㄒ|ㄩ|ᐯ|山|乂|ㄚ|乙|丹|万|乃|ㄏ|亼|乇|乇|米|彐|仈|仈|Ҝ|几|爪|卄|ㄖ|冂|卩|匚|ㄒ|ㄚ|中|乂|凵|ㄐ|山|屮|Ъ|Ы|Ь|ヨ|Ю|牙"),

        STRIKETHROUGH("Strikethrough",
            "0̶|1̶|2̶|3̶|4̶|5̶|6̶|7̶|8̶|9̶|a̶|b̶|c̶|d̶|e̶|f̶|g̶|h̶|i̶|j̶|k̶|l̶|m̶|n̶|o̶|p̶|q̶|r̶|s̶|t̶|u̶|v̶|w̶|x̶|y̶|z̶|а̶|б̶|в̶|г̶|д̶|е̶|ё̶|ж̶|з̶|и̶|й̶|к̶|л̶|м̶|н̶|о̶|п̶|р̶|с̶|т̶|у̶|ф̶|х̶|ц̶|ч̶|ш̶|щ̶|ъ̶|ы̶|ь̶|э̶|ю̶|я̶"),

        UPSIDE_DOWN("Upside Down",
            "0|Ɩ|Շ|Ɛ|߈|ϛ|9|ㄥ|8|6|ɐ|q|ɔ|p|ǝ|ɟ|ɓ|ɥ|ᴉ|ſ|ʞ|ꞁ|ɯ|u|o|d|b|ɹ|s|ʇ|n|ʌ|ʍ|x|ʎ|z|ɐ|g|ʚ|ɹ|ɓ|ǝ|ǝ|ж|ɛ|и|n|ʞ|v|ɯ|н|о|u|d|ɔ|⊥|ʎ|ɸ|х|n|һ|ʍ|ʍ|ꟼ|ıq|q|є|oı|ʁ"),

        WAVY("Wavy",
            "0̰|1̰|2̰|3̰|4̰|5̰|6̰|7̰|8̰|9̰|a̰|b̰|c̰|d̰|ḛ|f̰|g̰|h̰|ḭ|j̰|k̰|l̰|m̰|n̰|o̰|p̰|q̰|r̰|s̰|t̰|ṵ|v̰|w̰|x̰|y̰|z̰|а̰|б|в̰|г̰|д̰|е̰|ё̰|ж̰|з̰|и̰|й̰|к̰|л̰|м̰|н̰|о̰|п̰|р̰|с̰|т̰|у̰|ф̰|х̰|ц̰|ч̰|ш̰|щ̰|ъ̰|ы̰|ь̰|э̰|ю̰|я̰"),

        SLASHED("Slashed",
            "0̷|1̷|2̷|3̷|4̷|5̷|6̷|7̷|8̷|9̷|a̷|b̷|c̷|d̷|e̷|f̷|g̷|h̷|i̷|j̷|k̷|l̷|m̷|n̷|o̷|p̷|q̷|r̷|s̷|t̷|u̷|v̷|w̷|x̷|y̷|z̷|а̷|б̷|в̷|г̷|д̷|е̷|ё̷|ж̷|з̷|и̷|й̷|к̷|л̷|м̷|н̷|о̷|п̷|р̷|с̷|т̷|у̷|ф̷|х̷|ц̷|ч̷|ш̷|щ̷|ъ̷|ы̷|ь̷|э̷|ю̷|я̷"),

        PARENTHESIZED("Parenthesized",
            "(0)|⑴|⑵|⑶|⑷|⑸|⑹|⑺|⑻|⑼|⒜|⒝|⒞|⒟|⒠|⒡|⒢|⒣|⒤|⒥|⒦|⒧|⒨|⒩|⒪|⒫|⒬|⒭|⒮|⒯|⒰|⒱|⒲|⒳|⒴|⒵|❲а❳|❲б❳|❲в❳|❲г❳|❲д❳|❲е❳|❲ё❳|❲ж❳|❲з❳|❲и❳|❲й❳|❲к❳|❲л❳|❲м❳|❲н❳|❲о❳|❲п❳|❲р❳|❲с❳|❲т❳|❲у❳|❲ф❳|❲х❳|❲ц❳|❲ч❳|❲ш❳|❲щ❳|❲ъ❳|❲ы❳|❲ь❳|❲э❳|❲ю❳|❲я❳"),

        FRAMED("Framed",
            "0|1|2|3|4|5|6|7|8|9|🄰|🄱|🄲|🄳|🄴|🄵|🄶|🄷|🄸|🄹|🄺|🄻|🄼|🄽|🄾|🄿|🅀|🅁|🅂|🅃|🅄|🅅|🅆|🅇|🅈|🅉|⦏а⦐|⦏б⦐|⦏в⦐|⦏г⦐|⦏д⦐|⦏е⦐|⦏ё⦐|⦏ж⦐|⦏з⦐|⦏и⦐|⦏й⦐|⦏к⦐|⦏л⦐|⦏м⦐|⦏н⦐|⦏о⦐|⦏п⦐|⦏р⦐|⦏с⦐|⦏т⦐|⦏у⦐|⦏ф⦐|⦏х⦐|⦏ц⦐|⦏ч⦐|⦏ш⦐|⦏щ⦐|⦏ъ⦐|⦏ы⦐|⦏ь⦐|⦏э⦐|⦏ю⦐|⦏я⦐"),

        BRAILLE("Braille",
            "⠴|⠂|⠆|⠒|⠲|⠢|⠖|⠶|⠦|⠔|⠁|⠃|⠉|⠙|⠑|⠋|⠛|⠓|⠊|⠚|⠅|⠇|⠍|⠝|⠕|⠏|⠟|⠗|⠎|⠞|⠥|⠧|⠺|⠭|⠽|⠵|⠁|⠃|⠺|⠛|⠙|⠑|⠡|⠚|⠵|⠊|⠯|⠅|⠇|⠍|⠝|⠕|⠏|⠗|⠎|⠞|⠥|⠋|⠓|⠉|⠟|⠱|⠭|⠷|⠮|⠾|⠪|⠳|⠫"),

        MORSE_CODE("Morse Code",
            "----- |.---- |..--- |...-- |....- |..... |-.... |--... |---.. |----. |.- |-... |-.-. |-.. |. |..-. |--. |.... |.. |.--- |-.- |.-.. |-- |-. |--- |.--. |--.- |.-. |... |- |..- |...- |.-- |-..- |-.-- |--.. |.- |-... |.-- |--. |-.. |. |. |...- |-..- |.. |.--- |.-.- |-.- |.-.. |-- |-. |--- |.--. |.-. |... |- |..- |..-. |.... |-.-. |---. |---- |--.- |--.-- |-.-- |-..-.. |..-.. |..-- |.-.-. "),

        BRACKETS("Brackets",
            "【0】|【1】|【2】|【3】|【4】|【5】|【6】|【7】|【8】|【9】|【a】|【b】|【c】|【d】|【e】|【f】|【g】|【h】|【i】|【j】|【k】|【l】|【m】|【n】|【o】|【p】|【q】|【r】|【s】|【t】|【u】|【v】|【w】|【x】|【y】|【z】|【а】|【б】|【в】|【г】|【д】|【е】|【ё】|【ж】|【з】|【и】|【й】|【к】|【л】|【м】|【н】|【о】|【п】|【р】|【с】|【т】|【у】|【ф】|【х】|【ц】|【ч】|【ш】|【щ】|【ъ】|【ы】|【ь】|【э】|【ю】|【я】"),

        CORNER_BRACKETS("Corner brackets",
            "『0』|『1』|『2』|『3』|『4』|『5』|『6』|『7』|『8』|『9』|『a』|『b』|『c』|『d』|『e』|『f』|『g』|『h』|『i』|『j』|『k』|『l』|『m』|『n』|『o』|『p』|『q』|『r』|『s』|『t』|『u』|『v』|『w』|『x』|『y』|『z』|『а』|『б』|『в』|『г』|『д』|『е』|『ё』|『ж』|『з』|『и』|『й』|『к』|『л』|『м』|『н』|『о』|『п』|『р』|『с』|『т』|『у』|『ф』|『х』|『ц』|『ч』|『ш』|『щ』|『ъ』|『ы』|『ь』|『э』|『ю』|『я』"),

        SCRIPT("Script",
            "𝟢|𝟣|𝟤|𝟥|𝟦|𝟧|𝟨|𝟩|𝟪|𝟫|𝒶|𝒷|𝒸|𝒹|𝑒|𝒻|𝑔|𝒽|𝒾|𝒿|𝓀|𝓁|𝓂|𝓃|𝑜|𝓅|𝓆|𝓇|𝓈|𝓉|𝓊|𝓋|𝓌|𝓍|𝓎|𝓏|𝒶|𐴳|ϐ|ꦴ|ℊ|ℯ|ℯ|⯰|𖹱|𝓊|𝓊|𝓀|႔|ⰼ|𑣅|ℴ|𝓃|𝓅|𝒸|𝓂|𝒴|ቇ|𝓍|𝓎|𐨜|𝓌|𐒁|𐔊|𝓈𝜾|𝓈|𖹲|ꩯ|𞤹"),

        GOTHIC("Gothic",
            "𝟎|𝟏|𝟐|𝟑|𝟒|𝟓|𝟔|𝟕|𝟖|𝟗|𝖆|𝖇|𝖈|𝖉|𝖊|𝖋|𝖌|𝖍|𝖎|𝖏|𝖐|𝖑|𝖒|𝖓|𝖔|𝖕|𝖖|𝖗|𝖘|𝖙|𝖚|𝖛|𝖜|𝖝|𝖞|𝖟|а|б|в|г|д|е|ё|ж|з|и|й|к|л|м|н|о|п|р|с|т|у|ф|х|ц|ч|ш|щ|ъ|ы|ь|э|ю|я");

        public final String title;
        public final String[] chars;

        private static final List<Character> DEFAULT_CHARS;
        static {
            String defaultStr = "0123456789abcdefghijklmnopqrstuvwxyzабвгдеёжзийклмнопрстуфхцчшщъыьэюя";
            Character[] charsArray = new Character[defaultStr.length()];
            for (int i = 0; i < defaultStr.length(); i++) {
                charsArray[i] = defaultStr.charAt(i);
            }
            DEFAULT_CHARS = Arrays.asList(charsArray);
        }

        Fonts(String title, String charsString) {
            this.title = title;
            this.chars = charsString.split("\\|");
        }

        public String apply(String text) {
            if (text == null) return "";
            StringBuilder translated = new StringBuilder();

            for (char letter : text.toLowerCase().toCharArray()) {
                int index = DEFAULT_CHARS.indexOf(letter);
                if (index == -1) {
                    translated.append(letter);
                } else {
                    translated.append(this.chars[index]);
                }
            }

            return translated.toString();
        }

        @Override
        public  String toString() {
            return Config.get().customFont.get() ? this.title : apply(this.title);
        }
    }
}
