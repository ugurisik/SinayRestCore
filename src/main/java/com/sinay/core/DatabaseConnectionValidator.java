package com.sinay.core;

import com.sinay.core.server.exception.UsErrorCode;
import com.sinay.core.server.exception.UsException;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

/**
 * Veritabanı bağlantısını Spring başlamadan önce test eder.
 * <p>
 * Bu sınıf, application.yaml dosyasını okuyup veritabanı bağlantısını
 * test eder. Hata varsa güzel bir mesaj gösterir ve uygulama başlamaz.
 *
 * @author Uğur Işık
 * @since 1.0
 */
@Slf4j
public class DatabaseConnectionValidator {

    /**
     * Veritabanı bağlantısını doğrular.
     * <p>
     * application.yaml dosyasından veritabanı ayarlarını okur ve
     * bağlantı testi yapar. Hata varsa uygulama kapanır.
     */
    public static void validate() {
        try {
            DatabaseConfig config = loadConfig();

            if (config == null || config.url() == null) {
                System.out.println("  " + YELLOW + "⚠️  Veritabanı yapılandırması bulunamadı, atlanıyor..." + RESET);
                System.out.println();
                return;
            }

            testConnection(config);
            System.out.println("  " + GREEN + "✅ Veritabanı bağlantı testi başarılı" + RESET);
            System.out.println();

        } catch (Exception e) {
            // Hata yakalandı, mesaj zaten yazdırıldı
            System.exit(1);
        }
    }

    /**
     * application.yaml dosyasından veritabanı yapılandırmasını okur.
     */
    private static DatabaseConfig loadConfig() {
        try (InputStream input = new FileInputStream("src/main/resources/application.yaml")) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(input);

            // spring.datasource.url path'ini takip et
            Map<String, Object> spring = (Map<String, Object>) data.get("spring");
            if (spring == null) return null;

            Map<String, Object> datasource = (Map<String, Object>) spring.get("datasource");
            if (datasource == null) return null;

            String url = (String) datasource.get("url");
            String username = (String) datasource.get("username");
            String password = (String) datasource.get("password");

            return new DatabaseConfig(url, username, password);

        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Veritabanı bağlantısını test eder.
     */
    private static void testConnection(DatabaseConfig config) {
        try (Connection conn = DriverManager.getConnection(config.url(), config.username(), config.password())) {
            // Basit test sorgusu
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT 1")) {
                if (!rs.next()) {
                    throw new SQLException("Test sorgusu başarısız");
                }
            }
        } catch (SQLException e) {
            printDatabaseError(e, config);
            UsException.firlat(e.getMessage(), UsErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * Veritabanı hatası için mesaj gösterir.
     */
    private static void printDatabaseError(SQLException e, DatabaseConfig config) {
        String errorType = identifyErrorType(e);
        String solution = getSolution(errorType);
        String detail = getShortErrorMessage(e);

        System.err.println();
        System.err.println(RED + "  ┌─────────────────────────────────────────────────────────────────┐" + RESET);
        System.err.println(RED + "  │  ❌ VERİTABANI BAĞLANTI HATASI                                │" + RESET);
        System.err.println(RED + "  └─────────────────────────────────────────────────────────────────┘" + RESET);
        System.err.println();
        System.err.println("  " + BOLD + "Hata Türü:" + RESET + "  " + errorType);
        System.err.println("  " + BOLD + "Detay:" + RESET + "       " + truncate(detail, 55));
        System.err.println();
        System.err.println("  " + BOLD + "Çözüm:" + RESET);
        System.err.println("  " + CYAN + "  → " + RESET + solution);
        System.err.println();
        System.err.println("  " + BOLD + "Bağlantı:" + RESET);
        System.err.println("    URL:  " + maskUrl(config.url()));
        System.err.println("    User: " + config.username());
        System.err.println();
        System.err.println(GRAY + "  İpucu: --debug parametresi ile detaylı log alabilirsiniz" + RESET);
        System.err.println();
    }

    // ANSI renk kodları
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String GRAY = "\u001B[90m";
    private static final String BOLD = "\u001B[1m";

    /**
     * Hata tipini belirler.
     */
    private static String identifyErrorType(SQLException e) {
        String message = e.getMessage();
        if (message == null) {
            return "VERİTABANI BAĞLANTI HATASI";
        }
        message = message.toLowerCase();

        if (message.contains("connection refused") || message.contains("connectexception")) {
            return "BAĞLANTI REDDEDİLDİ";
        } else if (message.contains("access denied") || message.contains("authentication")) {
            return "YETKİLENDİRME HATASI";
        } else if (message.contains("unknown database") || message.contains("database does not exist")) {
            return "VERİTABANI BULUNAMADI";
        } else if (message.contains("timeout")) {
            return "BAĞLANTI ZAMAN AŞIMI";
        } else if (message.contains("communications link failure")) {
            return "VERİTABANI SUNUCUSUNA ULAŞILAMIYOR";
        } else {
            return "VERİTABANI BAĞLANTI HATASI";
        }
    }

    /**
     * Hata tipine göre çözüm önerisi.
     */
    private static String getSolution(String errorType) {
        return switch (errorType) {
            case "BAĞLANTI REDDEDİLDİ" -> "MySQL'in çalıştığından ve port 3306'nın açık olduğundan emin olun";
            case "YETKİLENDİRME HATASI" -> "Kullanıcı adı ve şifrenizi application.yaml'da kontrol edin";
            case "VERİTABANI BULUNAMADI" -> "Veritabanını oluşturun: CREATE DATABASE sinay_db;";
            case "BAĞLANTI ZAMAN AŞIMI" -> "Ağ bağlantınızı ve firewall ayarlarınızı kontrol edin";
            case "VERİTABANI SUNUCUSUNA ULAŞILAMIYOR" -> "MySQL sunucusunun başlatıldığından emin olun";
            default -> "application.yaml dosyasındaki veritabanı ayarlarını kontrol edin";
        };
    }

    /**
     * URL'den şifreyi maskeler.
     */
    private static String maskUrl(String url) {
        if (url == null) {
            return "(ayarlanmamış)";
        }
        // Şifre kısmını maskele
        return url.replaceAll("://([^:]+):([^@]+)@", "://$1:***@");
    }

    /**
     * Kısa hata mesajı döner.
     */
    private static String getShortErrorMessage(SQLException e) {
        String message = e.getMessage();
        if (message == null) {
            return e.getClass().getSimpleName();
        }
        // İlk satırı al
        String[] lines = message.split("\\n");
        return lines[0].trim();
    }

    /**
     * String'i belirtilen uzunluğa keser.
     */
    private static String truncate(String str, int maxLen) {
        if (str == null) {
            return "";
        }
        if (str.length() <= maxLen) {
            return str;
        }
        return str.substring(0, maxLen - 3) + "...";
    }

    /**
     * Veritabanı yapılandırması.
     */
    private record DatabaseConfig(String url, String username, String password) {
    }
}
