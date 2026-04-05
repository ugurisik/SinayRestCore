package com.sinay.core.server.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;

import java.sql.SQLException;

/**
 * Uygulama başlatılamadığında hatayı yakalayıp güzel bir mesaj gösterir.
 * <p>
 * Spring Context başarısız olduğunda (örn: veritabanı bağlantı hatası),
 * uzun stack trace yerine anlaşılır bir özet gösterir.
 *
 * @author Uğur Işık
 * @since 1.0
 */
@Slf4j
@Configuration
public class ApplicationStartupFailureLogger implements ApplicationListener<ApplicationFailedEvent> {

    @Override
    public void onApplicationEvent(ApplicationFailedEvent event) {
        Throwable exception = event.getException();
        Throwable rootCause = findRootCause(exception);

        // Veritabanı hatası mı?
        if (isDatabaseError(rootCause)) {
            printDatabaseFailure(rootCause);
        } else {
            // Genel hata
            printGeneralFailure(rootCause);
        }

        // Detaylı log (debug için)
        log.error("Uygulama başlatılamadı. Detaylı hata bilgisi:", exception);
    }

    /**
     * Root cause'u bulur.
     */
    private Throwable findRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    /**
     * Veritabanı hatası olup olmadığını kontrol eder.
     */
    private boolean isDatabaseError(Throwable throwable) {
        String className = throwable.getClass().getName();

        return className.contains("SQLException")
                || className.contains("CommunicationsException")
                || className.contains("JDBCConnectionException")
                || className.contains("DataAccessException")
                || throwable instanceof SQLException
                || throwable instanceof DataAccessException;
    }

    /**
     * Veritabanı hatası için mesaj gösterir.
     */
    private void printDatabaseFailure(Throwable rootCause) {
        System.err.println();
        System.err.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.err.println("║                    ❌ UYGULAMA BAŞLATILAMADI - VERİTABANI HATASI              ║");
        System.err.println("╠══════════════════════════════════════════════════════════════════════════════╣");
        System.err.println("║                                                                              ║");

        String errorType = identifyDatabaseError(rootCause);
        String solution = getDatabaseSolution(errorType);
        String detail = getShortErrorMessage(rootCause);

        System.err.println("║  HATA TİPİ: " + String.format("%-54s", errorType) + "║");
        System.err.println("║                                                                              ║");
        System.err.println("║  DETAY: " + String.format("%-58s", truncate(detail, 58)) + "║");
        System.err.println("║                                                                              ║");
        System.err.println("║  ÇÖZÜM: " + String.format("%-57s", solution) + "║");
        System.err.println("║                                                                              ║");
        System.err.println("║  📁 Yapılandırma: src/main/resources/application.yaml                        ║");
        System.err.println("║                                                                              ║");
        System.err.println("║  💡 İpucu: MySQL servisinin çalıştığından emin olun                           ║");
        System.err.println("║         (Windows: services.msc, Linux: sudo systemctl status mysql)          ║");
        System.err.println("║                                                                              ║");
        System.err.println("╚══════════════════════════════════════════════════════════════════════════════╝");
        System.err.println();
    }

    /**
     * Genel hata için mesaj gösterir.
     */
    private void printGeneralFailure(Throwable rootCause) {
        System.err.println();
        System.err.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.err.println("║                        ❌ UYGULAMA BAŞLATILAMADI                            ║");
        System.err.println("╠══════════════════════════════════════════════════════════════════════════════╣");
        System.err.println("║                                                                              ║");

        String errorType = rootCause.getClass().getSimpleName();
        String detail = getShortErrorMessage(rootCause);

        System.err.println("║  HATA: " + String.format("%-59s", errorType) + "║");
        System.err.println("║                                                                              ║");
        System.err.println("║  DETAY: " + String.format("%-58s", truncate(detail, 58)) + "║");
        System.err.println("║                                                                              ║");
        System.err.println("║  💡 Detaylı log için --debug parametresini kullanabilirsiniz                   ║");
        System.err.println("║                                                                              ║");
        System.err.println("╚══════════════════════════════════════════════════════════════════════════════╝");
        System.err.println();
    }

    /**
     * Veritabanı hatası tipini belirler.
     */
    private String identifyDatabaseError(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null) {
            message = throwable.getClass().getSimpleName();
        } else {
            message = message.toLowerCase();
        }

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
     * Veritabanı hatası için çözüm önerisi.
     */
    private String getDatabaseSolution(String errorType) {
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
     * Kısa hata mesajı döner.
     */
    private String getShortErrorMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null) {
            return throwable.getClass().getSimpleName();
        }
        // İlk satırı al
        String[] lines = message.split("\\n");
        return lines[0].trim();
    }

    /**
     * String'i belirtilen uzunluğa keser.
     */
    private String truncate(String str, int maxLen) {
        if (str == null) {
            return "";
        }
        if (str.length() <= maxLen) {
            return str;
        }
        return str.substring(0, maxLen - 3) + "...";
    }
}
