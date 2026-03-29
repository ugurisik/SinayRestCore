package com.sinay.core.utils;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * String işlemleri için yardımcı metodlar.
 */
public class StringUtils {

    // Path traversal için pattern
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile("(\\.\\.)|(~)|(\\.\\/)|(\\.\\\\)");

    // Tehlikeli uzantılar - double extension kontrolü için
    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
        "exe", "bat", "cmd", "com", "pif", "scr", "vbs", "js", "jar",
        "msi", "dll", "sys", "cpl", "drv", "lnk"
    );

    /**
     * Türkçe karakterleri İngilizce karşılıklarına dönüştürür.
     * <p>
     * Dönüştürme tablosu:
     * <ul>
     *   <li>ç → c, Ç → C</li>
     *   <li>ş → s, Ş → S</li>
     *   <li>ı → i, İ → I</li>
     *   <li>ğ → g, Ğ → G</li>
     *   <li>ö → o, Ö → O</li>
     *   <li>ü → u, Ü → U</li>
     * </ul>
     *
     * @param input Dönüştürülecek metin
     * @return Türkçe karakterlerden arındırılmış metin
     */
    public static String normalizeTurkishChars(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input
                .replace("ç", "c")
                .replace("Ç", "C")
                .replace("ş", "s")
                .replace("Ş", "S")
                .replace("ı", "i")
                .replace("İ", "I")
                .replace("ğ", "g")
                .replace("Ğ", "G")
                .replace("ö", "o")
                .replace("Ö", "O")
                .replace("ü", "u")
                .replace("Ü", "U");
    }

    /**
     * Dosya adında path traversal olup olmadığını kontrol eder.
     * <p>
     * Path traversal saldırıları hedef dizin dışındaki dosyalara erişmek için
     * "../", "..\", "~", veya "./" pattern'lerini kullanır.
     *
     * @param filename Kontrol edilecek dosya adı
     * @return Path traversal tespit edildiyse true, aksi halde false
     */
    public static boolean hasPathTraversal(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        return PATH_TRAVERSAL_PATTERN.matcher(filename).find();
    }

    /**
     * Dosya adında tehlikeli çift uzantı olup olmadığını kontrol eder.
     * <p>
     * Çift uzantı (örn: "photo.jpg.exe"), zararlı dosyaları masum göstermek için
     * kullanılan bir tekniktir. Sadece bilinen tehlikeli uzantı kombinasyonlarını kontrol eder.
     * <p>
     * Not: Normal metin içindeki noktalar (örn: "com.sinay.us") yoksayılır.
     *
     * @param filename Kontrol edilecek dosya adı
     * @return Çift uzantı tespit edildiyse true, aksi halde false
     */
    public static boolean hasDoubleExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }

        // Tüm noktaları bul
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex <= 0) {
            return false;
        }

        // Son uzantıyı al
        String lastExtension = filename.substring(lastDotIndex + 1).toLowerCase();

        // Önceki noktayı bul
        int previousDotIndex = filename.lastIndexOf('.', lastDotIndex - 1);
        if (previousDotIndex <= 0) {
            return false;
        }

        // Önceki uzantıyı al
        String previousExtension = filename.substring(previousDotIndex + 1, lastDotIndex).toLowerCase();

        // Önceki uzantı tehlikeli mi ve sonrasında başka bir uzantı var mı?
        return DANGEROUS_EXTENSIONS.contains(previousExtension);
    }

    /**
     * Dosya adından uzantıyı çıkarır.
     *
     * @param filename Uzantısı çıkarılacak dosya adı
     * @return Uzantı (nokta olmadan), uzantı yoksa boş string
     */
    public static String getFileExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }

        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex <= 0 || lastDotIndex == filename.length() - 1) {
            return "";
        }

        return filename.substring(lastDotIndex + 1);
    }

    /**
     * String'in boş veya null olup olmadığını kontrol eder.
     *
     * @param str Kontrol edilecek string
     * @return Boş veya null ise true
     */
    public static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    /**
     * String'in boş/null olmadığını kontrol eder.
     *
     * @param str Kontrol edilecek string
     * @return Boş/null değilse true
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    private StringUtils() {
        // Utility class - private constructor
    }
}
