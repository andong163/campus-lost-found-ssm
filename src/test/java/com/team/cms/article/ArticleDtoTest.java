package com.team.cms.article;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.team.cms.article.dto.*;
import com.team.cms.article.entity.Article;
import com.team.cms.common.domain.*;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArticleDtoTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test public void validatesRequiredLengthEnumAndFutureEventTime() throws Exception {
        CreateDraftRequest value = validRequest();
        assertTrue(validator.validate(value).toString(), validator.validate(value).isEmpty());
        value.setTitle("a"); value.setContent("short"); value.setItemType(null);
        value.setCategoryId(0L); value.setEventTime(LocalDateTime.now().plusDays(1));
        value.setLocation(" "); value.setContactInfo("");
        Set<String> fields = validator.validate(value).stream()
                .map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
        assertTrue(fields.contains("title")); assertTrue(fields.contains("content"));
        assertTrue(fields.contains("itemType")); assertTrue(fields.contains("categoryId"));
        assertTrue(fields.contains("eventTime")); assertTrue(fields.contains("location"));
        assertTrue(fields.contains("contactInfo"));
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        try { json.readValue("{\"itemType\":\"OTHER\"}", CreateDraftRequest.class); fail(); }
        catch (com.fasterxml.jackson.databind.exc.InvalidFormatException expected) { /* 只接受 LOST 或 FOUND。 */ }
        try { json.readValue("{\"authorId\":999,\"status\":\"PUBLISHED\",\"version\":8}",
                CreateDraftRequest.class); fail(); }
        catch (com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException expected) {
            assertEquals("authorId", expected.getPropertyName());
        }
    }

    @Test public void requestCannotSetAuthorStateOrVersionAndProjectionFiltersPrivateFields() throws Exception {
        CreateDraftRequest request = validRequest();
        Article value = request.toNewArticle(101L);
        assertEquals(Long.valueOf(101), value.getAuthorId());
        assertEquals(ArticleStatus.DRAFT, value.getStatus());
        assertNull(value.getVersion()); // 插入时由 BaseEntity 的公共填充机制设置初始版本。
        value.setId(7L); value.setDeleted(0); value.setVersion(0);
        assertEquals("请联系本人", PrivateArticleDetail.fromAuthorizedArticle(value).getContactInfo());
        try { PublicArticleView.fromVisibleArticle(value); fail(); }
        catch (IllegalArgumentException expected) { /* 草稿不可公开。 */ }
        value.setStatus(ArticleStatus.PUBLISHED);
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        String publicJson = json.writeValueAsString(PublicArticleView.fromVisibleArticle(value));
        assertTrue(publicJson.contains("校园遗失物品"));
        assertFalse(publicJson.contains("contactInfo"));
        assertFalse(publicJson.contains("请联系本人"));
        assertFalse(publicJson.contains("authorId"));
        assertFalse(publicJson.contains("claim"));
        assertFalse(publicJson.contains("version"));
        value.setDeleted(1);
        try { PublicArticleView.fromVisibleArticle(value); fail(); }
        catch (IllegalArgumentException expected) { /* 已删除物品不可公开。 */ }
    }

    private CreateDraftRequest validRequest() {
        CreateDraftRequest value = new CreateDraftRequest();
        value.setTitle("校园遗失物品"); value.setContent("一件蓝色外套，袖口有明显标记");
        value.setItemType(ItemType.LOST); value.setCategoryId(202L);
        value.setEventTime(LocalDateTime.now().minusDays(1));
        value.setLocation("教学楼门口"); value.setContactInfo("请联系本人");
        return value;
    }
}
