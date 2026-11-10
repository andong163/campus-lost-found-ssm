package com.team.cms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cms.tools.GmyR3ApiCatalog;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class GmyR3ApiCatalogTest {
    @Test public void documentedRoutesStillExistAndMatchPermissionAndCsrfContracts() throws Exception {
        ObjectMapper json=new ObjectMapper();
        JsonNode saved=json.readTree(Path.of("docs/openapi.json").toFile());
        JsonNode actual=json.readTree(json.writeValueAsBytes(new GmyR3ApiCatalog().document()));
        // 集成基线要求新旧接口均已汇总，不能仅验证第二轮接口仍然存在。
        assertEquals("Refresh the main catalog after integration", saved.path("paths").size(), actual.path("paths").size());
        Iterator<Map.Entry<String,JsonNode>> paths=saved.path("paths").fields();
        while(paths.hasNext()) {
            var path=paths.next(); Iterator<Map.Entry<String,JsonNode>> methods=path.getValue().fields();
            assertEquals(path.getKey()+" HTTP operations", path.getValue().size(), actual.path("paths").path(path.getKey()).size());
            while(methods.hasNext()) {
                var method=methods.next(); JsonNode live=actual.path("paths").path(path.getKey()).path(method.getKey());
                assertFalse("Documented route missing: "+path.getKey(),live.isMissingNode());
                for (String contract:new String[]{"x-controller","x-permission","x-csrf-enforced","requestBody","parameters"})
                    assertEquals(path.getKey()+" "+contract,method.getValue().path(contract),live.path(contract));
            }
        }
        validateRefs(saved,saved);
        assertEquals("Old import entry must contain the same OpenAPI document",saved,json.readTree(Path.of("docs/openapi.yaml").toFile()));
    }
    private void validateRefs(JsonNode root,JsonNode node) {
        if (node.has("$ref")) {
            String ref=node.path("$ref").asText();
            assertTrue("Only portable local schema refs",ref.startsWith("#/"));
            assertFalse("Unresolved reference: "+ref,root.at(ref.substring(1)).isMissingNode());
        }
        if (node.isContainerNode()) node.forEach(child->validateRefs(root,child));
    }
}
