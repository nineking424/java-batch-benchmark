package com.example.benchmark.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Database configuration and initialization.
 * Manages HikariCP DataSource and MyBatis SqlSessionFactory.
 */
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    private final Properties properties;
    private HikariDataSource dataSource;
    private SqlSessionFactory sqlSessionFactory;

    public DatabaseConfig() {
        this.properties = loadProperties();
    }

    /**
     * Load properties from application.properties file.
     */
    private Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (is != null) {
                props.load(is);
            } else {
                throw new RuntimeException("application.properties not found in classpath");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load application.properties", e);
        }
        return props;
    }

    /**
     * Initialize HikariCP DataSource.
     */
    public DataSource initDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getProperty("db.url"));
        config.setUsername(properties.getProperty("db.username"));
        config.setPassword(properties.getProperty("db.password"));

        // HikariCP settings
        config.setMaximumPoolSize(Integer.parseInt(properties.getProperty("hikari.maximumPoolSize", "10")));
        config.setMinimumIdle(Integer.parseInt(properties.getProperty("hikari.minimumIdle", "5")));
        config.setConnectionTimeout(Long.parseLong(properties.getProperty("hikari.connectionTimeout", "30000")));
        config.setIdleTimeout(Long.parseLong(properties.getProperty("hikari.idleTimeout", "600000")));
        config.setMaxLifetime(Long.parseLong(properties.getProperty("hikari.maxLifetime", "1800000")));

        // Oracle specific settings
        config.addDataSourceProperty("oracle.jdbc.defaultRowPrefetch", "100");

        this.dataSource = new HikariDataSource(config);
        logger.info("HikariCP DataSource initialized successfully");
        return dataSource;
    }

    /**
     * Initialize MyBatis SqlSessionFactory.
     */
    public SqlSessionFactory initSqlSessionFactory() {
        if (dataSource == null) {
            initDataSource();
        }

        // Create MyBatis configuration programmatically
        Configuration configuration = new Configuration();
        configuration.setCacheEnabled(false);
        configuration.setLazyLoadingEnabled(false);
        configuration.setUseGeneratedKeys(false);

        // Set up environment with our HikariCP datasource
        Environment environment = new Environment("benchmark",
                new JdbcTransactionFactory(), dataSource);
        configuration.setEnvironment(environment);

        // Register type alias
        configuration.getTypeAliasRegistry().registerAlias("Item",
                com.example.benchmark.domain.Item.class);

        // Load XML mapper resource explicitly
        try (InputStream mapperStream = Resources.getResourceAsStream("mapper/ItemMapper.xml")) {
            XMLMapperBuilder mapperBuilder = new XMLMapperBuilder(
                    mapperStream, configuration, "mapper/ItemMapper.xml", configuration.getSqlFragments());
            mapperBuilder.parse();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load ItemMapper.xml", e);
        }

        this.sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        logger.info("MyBatis SqlSessionFactory initialized successfully");
        return sqlSessionFactory;
    }

    /**
     * Initialize database schema (drop and recreate table, sequence, index).
     */
    public void initializeSchema() {
        logger.info("Initializing database schema...");

        String[] dropStatements = {
                "BEGIN EXECUTE IMMEDIATE 'DROP TABLE benchmark_items CASCADE CONSTRAINTS'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END;",
                "BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE benchmark_items_seq'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -2289 THEN RAISE; END IF; END;"
        };

        String[] createStatements = {
                "CREATE TABLE benchmark_items (" +
                    "id NUMBER(19) PRIMARY KEY, " +
                    "name VARCHAR2(100) NOT NULL, " +
                    "description VARCHAR2(500), " +
                    "quantity NUMBER(10) DEFAULT 0, " +
                    "price NUMBER(15,2) DEFAULT 0" +
                ")",
                "CREATE SEQUENCE benchmark_items_seq START WITH 1 INCREMENT BY 1 CACHE 1000",
                "CREATE INDEX idx_benchmark_items_name ON benchmark_items(name)"
        };

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Drop existing objects
            for (String sql : dropStatements) {
                stmt.execute(sql);
            }
            logger.info("Dropped existing table and sequence (if any)");

            // Create new objects
            for (String sql : createStatements) {
                stmt.execute(sql);
            }
            logger.info("Created table, sequence, and index");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }

    /**
     * Truncate the benchmark_items table.
     */
    public void truncateTable() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE benchmark_items");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to truncate table", e);
        }
    }

    /**
     * Get the record count from benchmark_items table.
     */
    public long getRecordCount() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM benchmark_items")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get record count", e);
        }
    }

    /**
     * Get benchmark settings from properties.
     */
    public int getRecordCount_Setting() {
        return Integer.parseInt(properties.getProperty("benchmark.recordCount", "10000"));
    }

    public int getBatchSize() {
        return Integer.parseInt(properties.getProperty("benchmark.batchSize", "1000"));
    }

    public int getWarmupIterations() {
        return Integer.parseInt(properties.getProperty("benchmark.warmupIterations", "2"));
    }

    public int getTestIterations() {
        return Integer.parseInt(properties.getProperty("benchmark.testIterations", "5"));
    }

    /**
     * Get the DataSource.
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Get the SqlSessionFactory.
     */
    public SqlSessionFactory getSqlSessionFactory() {
        return sqlSessionFactory;
    }

    /**
     * Shutdown and cleanup resources.
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("HikariCP DataSource closed");
        }
    }
}
