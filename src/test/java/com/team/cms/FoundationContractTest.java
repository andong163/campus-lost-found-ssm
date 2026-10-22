package com.team.cms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cms.common.domain.*;
import com.team.cms.common.security.PermissionCodes;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.*;
import static org.junit.Assert.*;

/** Prevent independently developed modules from drifting away from the published R1 contract. */
public class FoundationContractTest {
    private JsonNode contract() throws Exception {
        return new ObjectMapper().readTree(Files.readString(Path.of("docs/contracts/r1-contracts.json")));
    }
    private Set<String> values(JsonNode array) {
        Set<String> result = new HashSet<>();
        array.forEach(value -> assertTrue("Duplicate contract value", result.add(value.asText())));
        return result;
    }
    private Set<String> names(Enum<?>[] values) {
        Set<String> result = new HashSet<>();
        for (Enum<?> value : values) result.add(value.name());
        return result;
    }
    @Test public void permissionConstantsAndPublishedCatalogAreIdentical() throws Exception {
        Set<String> catalog = new HashSet<>();
        for (JsonNode permission : contract().get("permissions")) {
            assertTrue(catalog.add(permission.get("code").asText()));
            Field field = PermissionCodes.class.getField(permission.get("constant").asText());
            assertEquals(permission.get("code").asText(), field.get(null));
        }
        assertEquals(catalog, PermissionCodes.all());
        assertTrue(catalog.contains("category:add"));
        assertTrue(catalog.contains("category:view"));
    }
    @Test public void sharedEnumsAndPublicProjectionRemainCompatibleWithContract() throws Exception {
        JsonNode data = contract();
        assertEquals(names(RoleCode.values()), values(data.get("roles")));
        assertEquals(names(ItemType.values()), values(data.get("itemTypes")));
        assertEquals(names(ArticleStatus.values()), values(data.get("articleStatuses")));
        assertEquals(names(ResolutionStatus.values()), values(data.get("resolutionStatuses")));
        assertEquals(names(ClaimStatus.values()), values(data.get("claimStatuses")));
        Set<String> publicFields = values(data.at("/dtoContracts/PublicItemSummary/fields"));
        publicFields.addAll(values(data.at("/dtoContracts/PublicItemDetail/additionalFields")));
        assertTrue(Collections.disjoint(publicFields, values(data.get("neverPublicFields"))));
        assertEquals("EDITOR", data.at("/authorization/registrationRole").asText());
        assertFalse(data.at("/authorization/defaultGrantsImplemented").asBoolean());
    }
    @Test public void tenTableFieldInventoryMatchesActualInstallationSql() throws Exception {
        String sql = Files.readString(Path.of("sql/init.sql"));
        Matcher matcher = Pattern.compile("CREATE TABLE IF NOT EXISTS (\\w+)\\s*\\((.*?)\\) ENGINE=InnoDB;", Pattern.DOTALL).matcher(sql);
        Map<String, String> actual = new HashMap<>();
        while (matcher.find()) actual.put(matcher.group(1), matcher.group(2));
        assertEquals(10, actual.size());
        Set<String> catalogNames = new HashSet<>();
        for (JsonNode table : contract().get("schemaTables")) {
            String name = table.get("name").asText();
            assertTrue(catalogNames.add(name));
            assertNotNull("Missing SQL table: " + name, actual.get(name));
            for (JsonNode field : table.get("fields")) {
                assertTrue("Missing SQL column: " + name + "." + field.asText(),
                    Pattern.compile("(?m)^\\s*" + Pattern.quote(field.asText()) + "\\s+").matcher(actual.get(name)).find());
            }
        }
        assertEquals(actual.keySet(), catalogNames);
        for (RoleCode role : RoleCode.values()) assertTrue(sql.contains("('" + role.name() + "',"));
    }
    @Test public void mavenAndProductionCodeRemainTraditionalSsmWithoutBoot() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document pom = factory.newDocumentBuilder().parse(Path.of("pom.xml").toFile());
        assertEquals("war", pom.getElementsByTagName("packaging").item(0).getTextContent());
        assertEquals("11", pom.getElementsByTagName("maven.compiler.release").item(0).getTextContent());
        for (String tag : List.of("dependency", "plugin", "parent")) {
            NodeList nodes = pom.getElementsByTagName(tag);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element element = (Element) nodes.item(i);
                String text = element.getTextContent();
                assertFalse("Boot dependency or plugin", text.contains("org.springframework.boot") || text.contains("spring-boot"));
            }
        }
        assertNull(getClass().getClassLoader().getResource("org/springframework/boot/SpringApplication.class"));
        Pattern forbidden = Pattern.compile("org\\.springframework\\.boot|@SpringBootApplication|@EnableAutoConfiguration|SpringApplication\\s*\\.\\s*run");
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".java"))::iterator) {
                assertFalse("Boot production entry: " + file, forbidden.matcher(Files.readString(file)).find());
            }
        }
    }
}
