package org.enerscope.version.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.persistence.EntityManager;
import org.enerscope.jwt.JwtService;
import org.enerscope.node.model.NodeChange;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.enums.ChangeTypeEnum;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.version.model.Version;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class VersionJsonGolden {

    record Created(UUID versionId, String bearer) {
    }

    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern INSTANT_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
    private static final Path GOLDEN = Path.of("src/test/resources/golden/version-get.json");
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private VersionJsonGolden() {
    }

    static Created createChildWithParentNodeConnectionAndChange(EntityManager em, JwtService jwt, TransactionTemplate transaction) {
        return transaction.execute(status -> {
            AccessHttpFixture fixture = new AccessHttpFixture(em, jwt);
            var node = fixture.newNode(NodeTypeEnum.WELL);
            NodeConnection connection = new NodeConnection(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            em.persist(connection);
            NodeChange change = new NodeChange(ChangeTypeEnum.ADD, null, node);
            Version child = new Version("Golden child", fixture.version(), new ArrayList<>(List.of(node)),
                    new ArrayList<>(List.of(connection)), new ArrayList<>(), new ArrayList<>(List.of(change)));
            em.persist(child);
            fixture.project().addVersion(child);
            em.flush();
            return new Created(child.getId(), fixture.bearer(fixture.projectMember(ProjectMemberType.EDITOR)));
        });
    }

    static void assertMatchesGolden(String body) throws IOException {
        var actualJson = MAPPER.readTree(normalize(body));
        String actual = MAPPER.writeValueAsString(actualJson) + "\n";
        if (Boolean.getBoolean("golden.write")) {
            Files.createDirectories(GOLDEN.getParent());
            Files.writeString(GOLDEN, actual, StandardCharsets.UTF_8);
        }
        assertEquals(MAPPER.readTree(Files.readString(GOLDEN, StandardCharsets.UTF_8)), actualJson);
    }

    static String normalize(String body) {
        Map<String, String> ids = new HashMap<>();
        Matcher matcher = UUID_PATTERN.matcher(body);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String id = ids.computeIfAbsent(matcher.group(), key -> "<uuid-" + (ids.size() + 1) + ">");
            matcher.appendReplacement(out, Matcher.quoteReplacement(id));
        }
        matcher.appendTail(out);
        return INSTANT_PATTERN.matcher(out).replaceAll("<instant>");
    }
}
