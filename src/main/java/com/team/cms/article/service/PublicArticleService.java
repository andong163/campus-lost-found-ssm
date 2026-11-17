package com.team.cms.article.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.team.cms.article.dto.PublicArticleQuery;
import com.team.cms.article.dto.PublicArticleView;
import com.team.cms.article.entity.Article;
import com.team.cms.article.mapper.ArticleMapper;
import com.team.cms.category.dto.CategoryOption;
import com.team.cms.category.service.CategoryAccessService;
import com.team.cms.common.api.PageResult;
import com.team.cms.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicArticleService {
    private final ArticleMapper articles;
    private final CategoryAccessService categories;
    public PublicArticleService(ArticleMapper articles, CategoryAccessService categories) {
        this.articles=articles; this.categories=categories;
    }
    @Transactional(readOnly=true)
    public PageResult<PublicArticleView> list(PublicArticleQuery query) {
        query.validate();
        List<CategoryOption> tree=categories.publicTree();
        Map<Long,String> paths=paths(tree);
        List<Long> ids=validIds(tree,query.getCategoryId());
        if(ids.isEmpty()) return new PageResult<>(query.getPage(),query.getSize(),0,List.of());
        Page<Article> page=new Page<>(query.getPage(),query.getSize());
        articles.selectPublic(page,query,ids);
        return new PageResult<>(page.getCurrent(),page.getSize(),page.getTotal(),
            page.getRecords().stream().map(a->view(a,paths)).collect(Collectors.toList()));
    }
    @Transactional(readOnly=true)
    public PublicArticleView detail(long id) {
        if(id<=0) throw new BusinessException(HttpStatus.NOT_FOUND,"公开信息不存在");
        Map<Long,String> paths=paths(categories.publicTree());
        List<Long> ids=new ArrayList<>(paths.keySet());
        Article article=ids.isEmpty()?null:articles.selectPublicDetail(id,ids);
        if(article==null) throw new BusinessException(HttpStatus.NOT_FOUND,"公开信息不存在");
        return view(article,paths);
    }
    private List<Long> validIds(List<CategoryOption> tree, Long selected) {
        List<Long> ids=new ArrayList<>();
        for(CategoryOption root:tree) collect(root, selected==null, selected, ids);
        return ids;
    }
    private void collect(CategoryOption node, boolean included, Long selected, List<Long> ids) {
        boolean take=included || node.getId().equals(selected);
        if(take) ids.add(node.getId());
        for(CategoryOption child:node.getChildren()) collect(child,take,selected,ids);
    }
    private Map<Long,String> paths(List<CategoryOption> tree) {
        Map<Long,String> result=new HashMap<>();
        for(CategoryOption root:tree) path(root,"",result);
        return result;
    }
    private void path(CategoryOption node,String parent,Map<Long,String> result) {
        String value=parent.isEmpty()?node.getName():parent+" / "+node.getName();
        result.put(node.getId(),value);
        for(CategoryOption child:node.getChildren()) path(child,value,result);
    }
    private PublicArticleView view(Article article,Map<Long,String> paths) {
        String path=paths.get(article.getCategoryId());
        int separator=path==null?-1:path.lastIndexOf(" / ");
        return PublicArticleView.fromVisibleArticle(article,
            path==null?null:path.substring(separator<0?0:separator+3),path);
    }
}
