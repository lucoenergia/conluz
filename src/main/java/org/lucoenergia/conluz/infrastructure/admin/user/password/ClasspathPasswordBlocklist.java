package org.lucoenergia.conluz.infrastructure.admin.user.password;

import org.lucoenergia.conluz.domain.admin.user.password.PasswordBlocklist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The compromised-password list bundled with the application, read once from the classpath into memory. Each file
 * holds one lowercase entry per line; their format and origin are in {@code password-blocklist/README.md}. A missing
 * or empty file is a packaging error, so it fails the construction, and with it the application's startup, rather
 * than leaving the policy without its list.
 */
public class ClasspathPasswordBlocklist implements PasswordBlocklist {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClasspathPasswordBlocklist.class);

    private final Set<String> entries;

    /**
     * @param resourcePaths the classpath resources to read, each one required and not empty
     * @throws IllegalStateException if a resource is missing or has no entry
     */
    public ClasspathPasswordBlocklist(List<String> resourcePaths) {
        long start = System.nanoTime();
        Set<String> loaded = new HashSet<>();
        for (String resourcePath : resourcePaths) {
            Set<String> read = read(resourcePath);
            if (read.isEmpty()) {
                throw new IllegalStateException("The compromised-password list " + resourcePath + " is empty");
            }
            loaded.addAll(read);
        }
        entries = Set.copyOf(loaded);
        LOGGER.info("Loaded {} compromised passwords in {} ms", entries.size(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
    }

    @Override
    public boolean contains(String lowercasedPassword) {
        return entries.contains(lowercasedPassword);
    }

    private static Set<String> read(String resourcePath) {
        InputStream stream = ClasspathPasswordBlocklist.class.getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IllegalStateException("The compromised-password list " + resourcePath + " is missing");
        }
        Set<String> read = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    read.add(line);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the compromised-password list " + resourcePath, e);
        }
        return read;
    }
}
