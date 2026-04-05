package com.sinay.core.server.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Veritabanı bağlantı hatalarını yakalayıp kullanıcı dostu mesajlar gösterir.
 * <p>
 * Uygulama başlatılırken veritabanına bağlanamazsa, uzun stack trace yerine
 * anlaşılır bir hata mesajı gösterir ve uygulama graceful şekilde kapanır.
 *
 * @author Uğur Işık
 * @since 1.0
 */
@Slf4j
@Component
public class DatabaseConnectionErrorHandler {

    private final DataSource dataSource;

    public DatabaseConnectionErrorHandler(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Uygulama başladığında veritabanı bağlantısını test eder.
     * <p>
     * Bu test, gerçek bir bağlantı kurarak veritabanının erişilebilir
     * olduğunu doğrular. Hata varsa uygulama başlamadan önce güzel bir
     * mesaj gösterir.
     */
    @PostConstruct
    public void validateDatabaseConnection() {
        try (Connection connection = dataSource.getConnection()) {
            // Bağlantı başarılı
            log.info("✅ Veritabanı bağlantısı başarılı: {}", connection.getMetaData().getURL());

            // Basit bir test sorgusu
            try (var stmt = connection.createStatement();
                 var rs = stmt.executeQuery("SELECT 1")) {
                if (rs.next()) {
                    log.info("✅ Veritabanı sorgu testi başarılı");
                }
            }
        } catch (SQLException e) {
            printDatabaseError(e);
            System.exit(1);
        }
    }

    /**
     * Veritabanı hatası olduğunda kullanıcı dostu bir mesaj yazdırır.
     *
     * @param e SQL exception
     */
    private void printDatabaseError(SQLException e) {
        System.err.println();
        System.err.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.err.println("║                        ❌ VERİTABANI BAĞLANTI HATASI                            ║");
        System.err.println("╠══════════════════════════════════════════════════════════════════════════════╣");
        System.err.println("║                                                                              ║");

        // Hata tipine göre özel mesaj
        String errorType = identifyErrorType(e);
        String solution = getSolution(errorType);

        System.err.println("║  HATA TİPİ: " + String.format("%-54s", errorType) + "║");
        System.err.println("║                                                                              ║");
        System.err.println("║  DETAY: " + String.format("%-58s", e.getMessage()) + "║");
        System.err.println("║                                                                              ║");
        System.err.println("║  ÇÖZÜM: " + String.format("%-57s", solution) + "║");
        System.err.println("║                                                                              ║");

        printConnectionInfo();

        System.err.println("║                                                                              ║");
        System.err.println("║  ℹ️  Daha detaylı log için --debug parametresini kullanabilirsiniz              ║");
        System.err.println("║                                                                              ║");
        System.err.println("╚══════════════════════════════════════════════════════════════════════════════╝");
        System.err.println();

        // Detaylı log (debug için)
        log.error("Veritabanı bağlantı hatası detayı:", e);
    }

    /**
     * Hata tipini belirler.
     */
    private String identifyErrorType(SQLException e) {
        String message = e.getMessage().toLowerCase();

        if (message.contains("connection refused") || message.contains("connectexception")) {
            return "BAĞLANTI REDDEDİLDİ";
        } else if (message.contains("access denied") || message.contains("authentication")) {
            return "YETKİLENDİRME HATASI";
        } else if (message.contains("unknown database") || message.contains("database does not exist")) {
            return "VERİTABANI BULUNAMADI";
        } else if (message.contains("timeout")) {
            return "BAĞLANTI ZAMAN AŞIMI";
        } else {
            return "GENEL BAĞLANTI HATASI";
        }
    }

    /**
     * Hata tipine göre çözüm önerisi döner.
     */
    private String getSolution(String errorType) {
        return switch (errorType) {
            case "BAĞLANTI REDDEDİLDİ" -> "MySQL'in çalıştığından ve port'un açık olduğundan emin olun";
            case "YETKİLENDİRME HATASI" -> "Kullanıcı adı ve şifreyi kontrol edin";
            case "VERİTABANI BULUNAMADI" -> "Veritabanının oluşturulduğundan emin olun";
            case "BAĞLANTI ZAMAN AŞIMI" -> "Ağ bağlantınızı ve firewall ayarlarınızı kontrol edin";
            default -> "application.yaml dosyasındaki veritabanı ayarlarını kontrol edin";
        };
    }

    /**
     * Bağlantı bilgilerini yazdırır (şifre maskelenir).
     */
    private void printConnectionInfo() {
        if (dataSource instanceof HikariDataSource hikari) {
            String jdbcUrl = hikari.getJdbcUrl();
            String username = hikari.getUsername();

            // URL'den şifre ve hassas bilgileri maskele
            String maskedUrl = maskUrl(jdbcUrl);

            System.err.println("║  BAĞLANTI BİLGİLERİ:                                                            ║");
            System.err.println("║  ┌────────────────────────────────────────────────────────────────────┐    ║");
            System.err.println("║  │  URL:      " + String.format("%-51s", maskedUrl) + "│    ║");
            System.err.println("║  │  User:     " + String.format("%-51s", username) + "│    ║");
            System.err.println("║  └────────────────────────────────────────────────────────────────────┘    ║");
        }
    }

    /**
     * URL'den şifreyi maskeler.
     */
    private String maskUrl(String url) {
        if (url == null) {
            return "(ayarlanmamış)";
        }
        // Şifre kısmını maskele: jdbc:mysql://user:password@host:port/db
        return url.replaceAll("://[^:]+:([^@]+)@", "://***:***@");
    }
}
