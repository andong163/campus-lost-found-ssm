package com.team.cms.claim.controller;

import com.team.cms.audit.service.AuditTemplate;
import com.team.cms.auth.security.RequirePermission;
import com.team.cms.claim.ClaimPermissions;
import com.team.cms.claim.dto.ClaimCancelRequest;
import com.team.cms.claim.dto.ClaimCreateRequest;
import com.team.cms.claim.dto.ClaimDecisionRequest;
import com.team.cms.claim.dto.ClaimQuery;
import com.team.cms.claim.dto.ClaimView;
import com.team.cms.claim.dto.FoundContactView;
import com.team.cms.claim.dto.HandoverView;
import com.team.cms.claim.dto.ResolveRequest;
import com.team.cms.claim.service.ClaimReadPolicy;
import com.team.cms.claim.service.ClaimService;
import com.team.cms.common.api.PageResult;
import com.team.cms.common.api.Result;
import com.team.cms.review.service.ItemWorkflowService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认领与结案接口。
 *
 * <p>POST /items/{id}/resolve 同时承担 LOST 作者确认找回和 FOUND 发布者确认交接；
 * 具体分支由 ItemWorkflowService 按 itemType 判断，客户端不能自行指定物品类型或申请人。</p>
 */
@RestController
@RequestMapping("/api")
public class ClaimController {

    private final ClaimService claimService;
    private final ClaimReadPolicy claimReadPolicy;
    private final ItemWorkflowService itemWorkflowService;
    private final AuditTemplate auditTemplate;

    public ClaimController(ClaimService claimService, ClaimReadPolicy claimReadPolicy,
            ItemWorkflowService itemWorkflowService, AuditTemplate auditTemplate) {
        this.claimService = claimService;
        this.claimReadPolicy = claimReadPolicy;
        this.itemWorkflowService = itemWorkflowService;
        this.auditTemplate = auditTemplate;
    }

    @PostMapping("/items/{itemId}/claims")
    public Result<ClaimView> apply(@PathVariable Long itemId,
            @Valid @RequestBody ClaimCreateRequest request) {
        ClaimView view = auditTemplate.<ClaimView>execute("claim", "CLAIM_APPLY", itemId,
            () -> claimService.apply(itemId, request));
        return Result.ok(view);
    }

    @GetMapping("/claims/mine")
    public Result<PageResult<ClaimView>> mine(@Valid @ModelAttribute ClaimQuery query) {
        return Result.ok(claimService.mine(query));
    }

    @GetMapping("/claims")
    @RequirePermission(ClaimPermissions.CLAIM_HANDLE)
    public Result<PageResult<ClaimView>> queue(@Valid @ModelAttribute ClaimQuery query) {
        return Result.ok(claimService.queue(query));
    }

    @GetMapping("/items/{itemId}/claims")
    public Result<List<ClaimView>> forItem(@PathVariable Long itemId) {
        return Result.ok(claimService.forItem(itemId));
    }

    @PostMapping("/claims/{claimId}/decision")
    @RequirePermission(ClaimPermissions.CLAIM_HANDLE)
    public Result<Void> decide(@PathVariable Long claimId,
            @Valid @RequestBody ClaimDecisionRequest request) {
        auditTemplate.run("claim", "CLAIM_DECIDE", claimId,
            () -> claimService.decide(claimId, request));
        return Result.ok(null);
    }

    @PostMapping("/claims/{claimId}/cancel")
    public Result<Void> cancel(@PathVariable Long claimId,
            @Valid @RequestBody ClaimCancelRequest request) {
        auditTemplate.run("claim", "CLAIM_CANCEL", claimId,
            () -> claimService.cancel(claimId, request));
        return Result.ok(null);
    }

    @PostMapping("/items/{itemId}/resolve")
    public Result<Void> resolve(@PathVariable Long itemId,
            @Valid @RequestBody ResolveRequest request) {
        auditTemplate.run("claim", "CLAIM_RESOLVE", itemId,
            () -> itemWorkflowService.resolve(itemId, request.getVersion(), request.getClaimId()));
        return Result.ok(null);
    }

    /** 发布者交接入口的只读上下文；不返回联系方式或证据。 */
    @GetMapping("/items/{itemId}/handover")
    public Result<HandoverView> handover(@PathVariable Long itemId) {
        return Result.ok(claimService.handoverContext(itemId));
    }

    /** FOUND 联系方式单条入口：通过 ClaimReadPolicy 判定，并记录一次读取审计。 */
    @GetMapping("/items/{itemId}/found-contact")
    public Result<FoundContactView> foundContact(@PathVariable Long itemId) {
        FoundContactView contact = auditTemplate.<FoundContactView>execute("claim",
            "FOUND_CONTACT_READ", itemId, () -> claimReadPolicy.readFoundContact(itemId));
        return Result.ok(contact);
    }
}
