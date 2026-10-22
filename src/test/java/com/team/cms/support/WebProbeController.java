package com.team.cms.support;

import com.team.cms.common.api.*;
import com.team.cms.common.exception.BusinessException;
import com.team.cms.common.domain.ItemType;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Test-only routes. Never packaged in the application WAR. */
@RestController
@RequestMapping("/__test")
public class WebProbeController {
    @GetMapping("/page") public Result<PageQuery> page(@Valid @ModelAttribute PageQuery page) { return Result.ok(page); }
    @PostMapping("/body") public Result<String> body(@Valid @RequestBody Input input) { return Result.ok(input.getName()); }
    @GetMapping("/conflict") public void conflict() { throw new BusinessException(HttpStatus.CONFLICT, "状态冲突"); }
    @GetMapping("/failure") public void failure() { throw new IllegalStateException("secret-not-for-client"); }
    @GetMapping("/denied-by-aspect") public Result<String> deniedByRootAspect() { return Result.ok("must not execute"); }
    @PostMapping("/batch") public Result<Integer> batch(@Valid @RequestBody BatchIdsRequest input) {
        return Result.ok(input.getIds().size());
    }
    @PostMapping("/version") public Result<Integer> version(@Valid @RequestBody VersionInput input) {
        return Result.ok(input.getVersion());
    }
    @GetMapping("/database-conflict") public void databaseConflict() {
        throw new org.springframework.dao.DuplicateKeyException("private-sql-marker");
    }
    @GetMapping("/dependency-failure") public void dependencyFailure() {
        throw new org.springframework.transaction.CannotCreateTransactionException("private-sql-marker");
    }
    public static class VersionInput extends VersionRequest {
        @javax.validation.constraints.NotNull private ItemType itemType;
        public ItemType getItemType() { return itemType; }
        public void setItemType(ItemType itemType) { this.itemType = itemType; }
    }
    public static class Input {
        @NotBlank(message = "名称不能为空") private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
