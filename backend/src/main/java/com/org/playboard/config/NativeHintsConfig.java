package com.org.playboard.config;

import com.org.playboard.entity.auth.AuthProvider;
import com.org.playboard.entity.group.GroupRole;
import com.org.playboard.entity.group.MemberStatus;
import com.org.playboard.entity.match.MatchAction;
import com.org.playboard.service.notification.NotificationCategory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;

/**
 * Reflection hints for the GraalVM native image (see {@code Dockerfile.native}).
 *
 * <p>Spring's AOT pass already covers our own beans, entities, repositories and
 * controller DTOs. What it can't see are third-party libraries that bind JSON or
 * load implementations reflectively:
 * <ul>
 *   <li><b>google-http-client</b> (Google ID-token verification, Firebase Admin / FCM,
 *       google-auth) maps JSON onto {@code @Key}-annotated fields of
 *       {@code GenericJson} subclasses.</li>
 *   <li><b>jjwt</b> instantiates {@code io.jsonwebtoken.impl.*} via
 *       {@code Class.forName} from the API module.</li>
 *   <li><b>Hibernate</b> reflectively creates array variants ({@code UUID[]} etc.) of
 *       every basic attribute type while bootstrapping, even with no array columns.</li>
 * </ul>
 * Rather than enumerating individual classes (and silently breaking on a library
 * upgrade), every class under those packages is registered. This runs at build time
 * only and has no effect on the JVM build.
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(NativeHintsConfig.ThirdPartyReflectionHints.class)
public class NativeHintsConfig {

    static class ThirdPartyReflectionHints implements RuntimeHintsRegistrar {

        private static final List<String> PACKAGES = List.of(
                "com.google.api.client.json",
                "com.google.api.client.util",
                "com.google.api.client.auth.oauth2",
                "com.google.api.client.googleapis.auth.oauth2",
                "com.google.api.client.googleapis.json",
                "com.google.auth.oauth2",
                "com.google.firebase.messaging",
                "com.google.firebase.internal",
                "io.jsonwebtoken.impl",
                "io.jsonwebtoken.jackson.io");

        /** Basic attribute types used across our entities; keep in sync when adding one. */
        private static final List<Class<?>> ENTITY_ATTRIBUTE_TYPES = List.of(
                UUID.class, String.class, Instant.class, LocalDate.class, LocalTime.class,
                BigDecimal.class, Integer.class, Short.class, Long.class, Boolean.class,
                AuthProvider.class, GroupRole.class, MemberStatus.class, MatchAction.class,
                NotificationCategory.class);

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            for (Class<?> type : ENTITY_ATTRIBUTE_TYPES) {
                hints.reflection().registerType(type.arrayType());
            }
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);
            MetadataReaderFactory readers = new CachingMetadataReaderFactory(resolver);
            for (String pkg : PACKAGES) {
                String pattern = "classpath*:" + pkg.replace('.', '/') + "/**/*.class";
                try {
                    for (Resource resource : resolver.getResources(pattern)) {
                        String className = readers.getMetadataReader(resource).getClassMetadata().getClassName();
                        if (className.endsWith("package-info") || className.endsWith("module-info")) {
                            continue;
                        }
                        hints.reflection().registerType(
                                TypeReference.of(className),
                                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                                MemberCategory.INVOKE_DECLARED_METHODS,
                                MemberCategory.ACCESS_DECLARED_FIELDS);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to scan " + pkg + " for native hints", e);
                }
            }
        }
    }
}
