package top.egon.cola.platform.tianji.process.server.acceptance;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import top.egon.cola.platform.tianji.process.server.TianjiProcessServerApplication;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/** Explicit E child main using the existing Process application and verified private input. */
public final class ExpenseProcessAcceptanceMain {
    private static final String FIXTURE = "tianji.expense.fixture.";

    private ExpenseProcessAcceptanceMain() { }

    public static void main(String[] args) {
        try {
            Properties settings = privateChildSettings(args, "PROCESS");
            boolean s07 = "true".equals(settings.getProperty(FIXTURE + "s07-governed-start"));
            if (!"false".equals(settings.getProperty("flowable.database-schema-update"))
                    || !Boolean.toString(!s07).equals(settings.getProperty("flowable.async-executor-activate"))
                    || !"tianji_process".equals(settings.getProperty("flowable.database-schema"))
                    || s07 && (settings.containsKey(FIXTURE + "s12-receive-link") || settings.containsKey(FIXTURE + "s12-metrics"))) {
                throw unavailable();
            }
            var required = s07 ? List.of("api", "decision-tasks")
                    : List.of("api", "worker", "human-tasks", "agent-bridge", "agent-results", "cases");
            for (String feature : required) {
                if (!"true".equals(settings.getProperty("stellar.tianji.process." + feature + ".enabled"))) throw unavailable();
            }
            installTrust(settings);
            var app = new SpringApplicationBuilder(TianjiProcessServerApplication.class).web(WebApplicationType.SERVLET);
            if ("true".equals(settings.getProperty(FIXTURE + "s12-receive-link"))) {
                app.initializers(new top.egon.cola.platform.tianji.acceptance.S12ReceiveLinkInitializer());
            } else if ("true".equals(settings.getProperty(FIXTURE + "s12-metrics"))) {
                app.initializers(new top.egon.cola.platform.tianji.acceptance.S12MetricsInitializer());
            }
            app.run(args);
        } catch (Exception failure) {
            throw unavailable();
        }
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Expense Process acceptance startup or fixture configuration failed");
    }

    private static Properties privateChildSettings(String[] args, String expectedRole) throws IOException {
        String location = "--spring.config.additional-location=";
        if (args == null || args.length != 2 || !"--spring.main.web-application-type=servlet".equals(args[0])
                || args[1] == null || !args[1].startsWith(location)
                || !"false".equals(System.getenv("ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS"))) throw unavailable();
        Path root = Path.of("").toRealPath();
        Path config = privateFile(root, Path.of(URI.create(args[1].substring(location.length()))), 64 * 1024);
        String role = expectedRole.toLowerCase(java.util.Locale.ROOT);
        if (!config.getParent().equals(root)
                || !config.getFileName().toString().matches("expense-[A-Za-z0-9_.-]+-" + role + "-[1-9][0-9]*\\.properties")) {
            throw unavailable();
        }
        var settings = new Properties();
        try (var input = Files.newInputStream(config, LinkOption.NOFOLLOW_LINKS)) {
            byte[] contents = input.readNBytes(64 * 1024 + 1);
            try {
                if (contents.length == 0 || contents.length > 64 * 1024) throw unavailable();
                settings.load(new java.io.ByteArrayInputStream(contents));
            } finally {
                Arrays.fill(contents, (byte) 0);
            }
        }
        String address = settings.getProperty("server.address");
        String port = settings.getProperty("server.port");
        String jdbc = settings.getProperty("spring.datasource.url");
        if (!"true".equals(settings.getProperty(FIXTURE + "run-authorized"))
                || !expectedRole.equals(settings.getProperty(FIXTURE + "role"))
                || !("127.0.0.1".equals(address) || "::1".equals(address))
                || port == null || !port.matches("[1-9][0-9]{0,4}") || Integer.parseInt(port) > 65_535
                || jdbc == null || !jdbc.startsWith("jdbc:postgresql://")
                || !"true".equals(settings.getProperty("server.ssl.enabled"))
                || !"false".equals(settings.getProperty("spring.flyway.enabled"))
                || !"never".equals(settings.getProperty("spring.sql.init.mode"))) throw unavailable();
        return settings;
    }

    private static Path privateFile(Path root, Path requested, long limit) throws IOException {
        if (!requested.isAbsolute() || !requested.equals(requested.normalize()) || !requested.startsWith(root)
                || !requested.equals(requested.toRealPath()) || !Files.isRegularFile(requested, LinkOption.NOFOLLOW_LINKS)
                || !Files.getOwner(root).equals(Files.getOwner(requested, LinkOption.NOFOLLOW_LINKS))
                || Files.size(requested) < 1 || Files.size(requested) > limit
                || !Files.getPosixFilePermissions(requested, LinkOption.NOFOLLOW_LINKS).stream()
                .allMatch(permission -> permission == PosixFilePermission.OWNER_READ || permission == PosixFilePermission.OWNER_WRITE)) {
            throw unavailable();
        }
        return requested;
    }

    private static void installTrust(Properties settings) throws Exception {
        Path root = Path.of("").toRealPath();
        Path trust = privateFile(root, Path.of(settings.getProperty(FIXTURE + "trust-store")), 1024 * 1024);
        Path passwordFile = privateFile(root, Path.of(settings.getProperty(FIXTURE + "trust-password-file")), 1024);
        byte[] bytes;
        try (var input = Files.newInputStream(passwordFile, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(1025);
        }
        char[] password = null;
        try {
            if (bytes.length > 1024) throw unavailable();
            var decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes));
            int length = decoded.remaining();
            if (length > 0 && decoded.get(length - 1) == '\n') length--;
            if (length == 0) throw unavailable();
            password = new char[length];
            for (int i = 0; i < length; i++) {
                char character = decoded.get(i);
                if (Character.isISOControl(character)) throw unavailable();
                password[i] = character;
            }
            KeyStore trustStore = KeyStore.getInstance(trust.toFile(), password);
            var names = java.util.Collections.list(trustStore.aliases());
            if (names.isEmpty()) throw unavailable();
            for (String name : names) if (!trustStore.isCertificateEntry(name)) throw unavailable();
            var factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(trustStore);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, factory.getTrustManagers(), null);
            SSLContext.setDefault(context);
            HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
        } finally {
            Arrays.fill(bytes, (byte) 0);
            if (password != null) Arrays.fill(password, '\0');
        }
    }
}
