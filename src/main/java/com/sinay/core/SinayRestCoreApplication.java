package com.sinay.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;

@SpringBootApplication
@EnableScheduling
@EnableAspectJAutoProxy
public class SinayRestCoreApplication {

    public static void main(String[] args) {
        // Logs dizinini oluştur (logback için)
        createLogsDirectory();
        DatabaseConnectionValidator.validate();
        SpringApplication.run(SinayRestCoreApplication.class, args);
    }

    private static void createLogsDirectory() {
        File logsDir = new File("logs");
        if (!logsDir.exists()) {
            boolean created = logsDir.mkdirs();
            if (created) {
                System.out.println("Logs dizini oluşturuldu: " + logsDir.getAbsolutePath());
            }
        }
    }

}
