package com.example.library.service;

import java.text.Normalizer;
import java.util.Locale;

/** Makes Persian/Arabic spelling and digit variations searchable without changing stored data. */
public final class PersianSearch {
    private PersianSearch() { }

    public static String normalize(String value) {
        if (value == null) return "";
        String text = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
                .replace("\u200c", "").replace("\u200d", "")
                .replaceAll("[\\u064B-\\u065F\\u0670\\u0640]", "");
        StringBuilder result = new StringBuilder();
        for (char ch : text.toCharArray()) {
            if (ch >= '۰' && ch <= '۹') result.append((char) ('0' + ch - '۰'));
            else if (ch >= '٠' && ch <= '٩') result.append((char) ('0' + ch - '٠'));
            else result.append(ch);
        }
        return result.toString().trim();
    }

    public static boolean matches(String query, String... fields) {
        StringBuilder haystack = new StringBuilder();
        for (String field : fields) haystack.append(normalize(field)).append(' ');
        for (String word : normalize(query).split("\\s+")) {
            if (!haystack.toString().contains(word)) return false;
        }
        return true;
    }
}
