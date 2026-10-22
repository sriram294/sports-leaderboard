package com.org.playboard.config;

import com.google.api.client.util.GenericData;
import com.google.api.client.util.Key;
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
import java.util.Arrays;
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
 * <p>Spring's AOT pass covers our own beans, entities, repositories and controller DTOs,
 * and the GraalVM reachability-metadata repository covers jjwt, google-http-client,
 * google-auth and the Apache transport. What's left:
 * <ul>
 *   <li><b>firebase-admin</b> (FCM) and <b>google-api-client</b> (Google ID-token
 *       verification) map JSON onto {@code @Key}-annotated fields of {@code GenericData}
 *       subclasses, and neither ships native metadata. Those packages are scanned and
 *       only the JSON-bound classes are registered, so a library upgrade is picked up
 *       automatically without registering (and compiling) every class in them.</li>
 *   <li>The Google libraries read their version from bundled {@code .properties}
 *       files at runtime (firebase-admin fails every send without
 *       {@code admin_sdk.properties}); a native image only embeds resources it's told to.</li>
 *   <li><b>Hibernate</b> reflectively creates array variants ({@code UUID[]} etc.) of
 *       every basic attribute type while bootstrapping, even with no array columns.</li>
 * </ul>
 * Hints are only collected at build time; they have no effect on the JVM build.
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(NativeHintsConfig.ThirdPartyReflectionHints.class)
public class NativeHintsConfig {

    static class ThirdPartyReflectionHints implements RuntimeHintsRegistrar {

        private static final List<String> JSON_BINDING_PACKAGES = List.of(
                "com.google.api.client.json.webtoken",
                "com.google.api.client.googleapis",
                "com.google.firebase.messaging",
                "com.google.firebase.internal");

        private static final List<String> RESOURCES = List.of(
                "admin_sdk.properties",
                "com/google/api/client/googleapis/google-api-client.properties",
                "com/google/api/client/http/google-http-client.properties",
                "com/google/auth/oauth2/google-auth-library.properties");

        /** Basic attribute types used across our entities; keep in sync when adding one. */
        private static final List<Class<?>> ENTITY_ATTRIBUTE_TYPES = List.of(
                UUID.class, String.class, Instant.class, LocalDate.class, LocalTime.class,
                BigDecimal.class, Integer.class, Short.class, Long.class, Boolean.class,
                AuthProvider.class, GroupRole.class, MemberStatus.class, MatchAction.class,
                NotificationCategory.class);

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            RESOURCES.forEach(hints.resources()::registerPattern);
            for (Class<?> type : ENTITY_ATTRIBUTE_TYPES) {
                hints.reflection().registerType(type.arrayType());
            }
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);
            MetadataReaderFactory readers = new CachingMetadataReaderFactory(resolver);
            for (String pkg : JSON_BINDING_PACKAGES) {
                String pattern = "classpath*:" + pkg.replace('.', '/') + "/**/*.class";
                try {
                    for (Resource resource : resolver.getResources(pattern)) {
                        String className = readers.getMetadataReader(resource).getClassMetadata().getClassName();
                        if (isJsonBound(className, classLoader)) {
                            hints.reflection().registerType(
                                    TypeReference.of(className),
                                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                                    MemberCategory.INVOKE_DECLARED_METHODS,
                                    MemberCategory.ACCESS_DECLARED_FIELDS);
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to scan " + pkg + " for native hints", e);
                }
            }
        }

        /** A {@code GenericData} subclass or a class with {@code @Key} fields. */
        private static boolean isJsonBound(String className, ClassLoader classLoader) {
            if (className.endsWith("package-info") || className.endsWith("module-info")) {
                return false;
            }
            try {
                Class<?> type = Class.forName(className, false, classLoader);
                return GenericData.class.isAssignableFrom(type)
                        || Arrays.stream(type.getDeclaredFields()).anyMatch(f -> f.isAnnotationPresent(Key.class));
            } catch (Throwable e) {
                // References an optional dependency that isn't on the classpath (e.g. the
                // Firestore/Storage clients excluded in pom.xml) — unreachable at runtime too.
                return false;
            }
        }
    }
}
