package com.team.cms.article;

import com.team.cms.article.dto.CreateDraftRequest;
import com.team.cms.article.entity.Article;
import com.team.cms.article.mapper.ArticleMapper;
import com.team.cms.common.domain.ArticleStatus;
import com.team.cms.common.domain.ItemType;
import com.team.cms.common.domain.ResolutionStatus;
import com.team.cms.config.SpringConfig;
import java.time.LocalDateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SpringConfig.class)
@TestPropertySource(properties = {
    "jdbc.driver=org.h2.Driver", "jdbc.url=jdbc:h2:mem:article_r1;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "jdbc.username=sa", "jdbc.password="
})
@Sql("/article-schema-test.sql")
@Transactional
public class ArticleMapperTest {
    @Autowired private ArticleMapper mapper;

    @Test public void savesAndMapsBothItemTypesWithServerOwnedFields() {
        for (ItemType type : ItemType.values()) {
            Article draft = request(type).toNewArticle(101L);
            assertEquals(1, mapper.insert(draft));
            assertNotNull(draft.getId());
            assertEquals(Integer.valueOf(0), draft.getVersion());
            assertNotNull(draft.getCreatedAt());
            Article loaded = mapper.selectById(draft.getId());
            assertEquals(type, loaded.getItemType());
            assertEquals(ArticleStatus.DRAFT, loaded.getStatus());
            assertEquals(ResolutionStatus.OPEN, loaded.getResolutionStatus());
            assertEquals(Long.valueOf(101), loaded.getAuthorId());
            assertEquals(Long.valueOf(202), loaded.getCategoryId());
            assertEquals("请联系本人", loaded.getContactInfo());
            assertEquals(draft.getEventTime(), loaded.getEventTime());
            assertEquals("教学楼门口", loaded.getLocation());
            assertNull(loaded.getResolvedAt());
            assertNull(loaded.getPublishedAt());
        }
    }

    @Test public void privateDraftQueryRequiresAuthorAndDraftState() {
        Article draft = request(ItemType.LOST).toNewArticle(101L); mapper.insert(draft);
        assertNotNull(mapper.selectDraftByIdAndAuthor(draft.getId(), 101L));
        assertNull(mapper.selectDraftByIdAndAuthor(draft.getId(), 999L));
        assertEquals(1, mapper.updateStatusIfVersion(draft.getId(), ArticleStatus.DRAFT,
                ArticleStatus.PENDING_REVIEW, 0));
        assertNull(mapper.selectDraftByIdAndAuthor(draft.getId(), 101L));
    }

    @Test public void lockedQueryAndConditionalUpdateHonorVersionAndDeletion() {
        Article draft = request(ItemType.FOUND).toNewArticle(101L); mapper.insert(draft);
        assertNotNull(mapper.selectByIdForUpdate(draft.getId()));
        assertEquals(0, mapper.updateStatusIfVersion(draft.getId(), ArticleStatus.DRAFT,
                ArticleStatus.PENDING_REVIEW, 1));
        assertEquals(1, mapper.updateStatusIfVersion(draft.getId(), ArticleStatus.DRAFT,
                ArticleStatus.PENDING_REVIEW, 0));
        assertEquals(Integer.valueOf(1), mapper.selectById(draft.getId()).getVersion());
        assertEquals(0, mapper.updateStatusIfVersion(draft.getId(), ArticleStatus.DRAFT,
                ArticleStatus.PENDING_REVIEW, 0));
        mapper.deleteById(draft.getId());
        assertNull(mapper.selectByIdForUpdate(draft.getId()));
        assertEquals(0, mapper.updateStatusIfVersion(draft.getId(), ArticleStatus.PENDING_REVIEW,
                ArticleStatus.APPROVED, 1));
    }

    private CreateDraftRequest request(ItemType type) {
        CreateDraftRequest value = new CreateDraftRequest();
        value.setTitle("校园遗失物品"); value.setContent("一件蓝色外套，袖口有明显标记");
        value.setItemType(type); value.setCategoryId(202L);
        value.setEventTime(LocalDateTime.of(2026, 10, 10, 12, 0));
        value.setLocation("教学楼门口"); value.setContactInfo("请联系本人");
        return value;
    }
}
