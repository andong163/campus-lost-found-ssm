package com.team.cms.review.service;

/**
 * 统一内容状态服务（唯一维护人：杨程）。
 *
 * <p>袁运轩的 ArticleService 必须调用本接口完成提审、撤回、转草稿、发布、下架和删除前校验；
 * 工作流只依赖 ArticleMapper / ClaimRequestMapper，不反向依赖 ArticleService，避免循环注入
 * 与两套状态规则。所有方法均为公开 Service 代理进入事务，内容字段与状态变化同事务提交。</p>
 */
public interface ItemWorkflowService {

    /** 作者提交：DRAFT/REJECTED → PENDING_REVIEW，要求内容完整且分类可绑定。 */
    void submit(Long itemId, int version);

    /** 作者撤回：PENDING_REVIEW → DRAFT，尚未被审核处理。 */
    void withdraw(Long itemId, int version);

    /** 作者开始修改：APPROVED/OFFLINE → DRAFT；存在未完成认领时拒绝。 */
    void toDraft(Long itemId, int version);

    /** 授权人员发布：APPROVED/OFFLINE → PUBLISHED，要求分类有效且未结案。 */
    void publish(Long itemId, int version);

    /** 作者或发布权限人员下架：PUBLISHED → OFFLINE，并同事务关闭待核实申请。 */
    void offline(Long itemId, int version, String reason);

    /** 删除前校验：待审核或存在 PENDING/APPROVED 认领时拒绝。由 ArticleService 调用。 */
    void assertDeletable(Long itemId);

    /**
     * 结案：LOST 由作者确认找回；FOUND 由发布者确认实际交接。
     * FOUND 必须存在 APPROVED 申请，成功后申请 COMPLETED、物品 RESOLVED、其他 PENDING 关闭。
     */
    void resolve(Long itemId, int version, Long expectedClaimId);
}
