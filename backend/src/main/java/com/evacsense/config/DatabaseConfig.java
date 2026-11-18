package com.evacsense.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import javax.sql.DataSource;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DatabaseConfig {
    @org.springframework.beans.factory.annotation.Value("${supabase.db.url:}")
    private String supabasePropertyUrl;

    private String getSupabaseUrl() {
        if (supabasePropertyUrl != null && !supabasePropertyUrl.isEmpty()) {
            return supabasePropertyUrl;
        }
        
        String supabaseUrl = System.getenv("SUPABASE_DB_URL");
        if (supabaseUrl != null && !supabaseUrl.isEmpty()) {
            return supabaseUrl;
        }
        try {
            java.io.File envFile = new java.io.File(".env");
            if (envFile.exists()) {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(envFile.toPath());
                for (String line : lines) {
                    line = line.trim();
                    if (line.startsWith("SUPABASE_DB_URL=")) {
                        String value = line.substring("SUPABASE_DB_URL=".length()).trim();
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        } else if (value.startsWith("'") && value.endsWith("'")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        return value;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[DATABASE] Error reading .env file: " + e.getMessage());
        }
        return null;
    }

    @Bean
    public DataSource dataSource() {
        String supabaseUrl = getSupabaseUrl();

        if (supabaseUrl != null && !supabaseUrl.isEmpty()) {
            try {
                // Parse standard URL: postgresql://username:password@host:port/database
                String cleanUrl = supabaseUrl.replace("postgresql://", "http://");
                URI uri = new URI(cleanUrl);
                String host = uri.getHost();
                int port = uri.getPort();
                String path = uri.getPath();
                String userInfo = uri.getUserInfo();

                String username = java.net.URLDecoder.decode(userInfo.split(":")[0], java.nio.charset.StandardCharsets.UTF_8);
                String password = java.net.URLDecoder.decode(userInfo.split(":")[1], java.nio.charset.StandardCharsets.UTF_8);

                String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path + "?prepareThreshold=0&sslmode=require";

                // Use HikariCP connection pool — prevents connection exhaustion on PgBouncer (pool_size: 15)
                HikariConfig hikariConfig = new HikariConfig();
                hikariConfig.setDriverClassName("org.postgresql.Driver");
                hikariConfig.setJdbcUrl(jdbcUrl);
                hikariConfig.setUsername(username);
                hikariConfig.setPassword(password);
                hikariConfig.setMaximumPoolSize(3);
                hikariConfig.setMinimumIdle(1);
                hikariConfig.setConnectionTimeout(20000);
                hikariConfig.setIdleTimeout(300000);
                hikariConfig.setMaxLifetime(600000);
                hikariConfig.setKeepaliveTime(60000);
                hikariConfig.addDataSourceProperty("prepareThreshold", "0");

                System.out.println("[DATABASE] Initializing HikariCP pool for Supabase PostgreSQL (max=3, PgBouncer-safe)...");
                return new HikariDataSource(hikariConfig);
            } catch (Exception e) {
                System.err.println("[DATABASE] Failed to parse SUPABASE_DB_URL, falling back to SQLite: " + e.getMessage());
            }
        }

        // Local SQLite Fallback (no pooling needed for file-based DB)
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:evacsense.sqlite");
        System.out.println("[DATABASE] Initializing local SQLite connection at evacsense.sqlite...");
        return dataSource;
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            EntityManagerFactoryBuilder builder, DataSource dataSource) {
        
        Map<String, Object> properties = new HashMap<>();
        String supabaseUrl = getSupabaseUrl();
        
        if (supabaseUrl != null && !supabaseUrl.isEmpty()) {
            properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        } else {
            properties.put("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
        }
        
        // Explicitly set ddl-auto and sql show properties since we override the autoconfigured EntityManager
        properties.put("hibernate.hbm2ddl.auto", "validate");
        properties.put("hibernate.show_sql", "false");
        
        return builder
                .dataSource(dataSource)
                .packages("com.evacsense.model")
                .persistenceUnit("default")
                .properties(properties)
                .build();
    }
}

