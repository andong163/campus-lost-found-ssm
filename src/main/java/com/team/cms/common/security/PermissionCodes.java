package com.team.cms.common.security;

import java.util.Set;

/** Shared permission names, not grants. Guo's real session and AOP must enforce them. */
public final class PermissionCodes {
    private PermissionCodes() { }

    public static final String USER_VIEW = "user:view";
    public static final String USER_EDIT = "user:edit";
    public static final String USER_DISABLE = "user:disable";
    public static final String USER_ROLE = "user:role";
    public static final String ROLE_VIEW = "role:view";
    public static final String ROLE_EDIT = "role:edit";
    public static final String PERMISSION_VIEW = "permission:view";
    public static final String PERMISSION_EDIT = "permission:edit";
    public static final String CATEGORY_VIEW = "category:view";
    public static final String CATEGORY_ADD = "category:add";
    public static final String CATEGORY_EDIT = "category:edit";
    public static final String CATEGORY_MOVE = "category:move";
    public static final String CATEGORY_SORT = "category:sort";
    public static final String CATEGORY_ENABLE = "category:enable";
    public static final String CATEGORY_DELETE = "category:delete";
    public static final String ITEM_CREATE = "item:create";
    public static final String ITEM_VIEW_OWN = "item:view:own";
    public static final String ITEM_VIEW_ANY = "item:view:any";
    public static final String ITEM_EDIT_OWN = "item:edit:own";
    public static final String ITEM_EDIT_ANY = "item:edit:any";
    public static final String ITEM_DELETE_OWN = "item:delete:own";
    public static final String ITEM_DELETE_ANY = "item:delete:any";
    public static final String ITEM_SUBMIT = "item:submit";
    public static final String ITEM_WITHDRAW = "item:withdraw";
    public static final String ITEM_PUBLISH = "item:publish";
    public static final String ITEM_OFFLINE_OWN = "item:offline:own";
    public static final String ITEM_OFFLINE_ANY = "item:offline:any";
    public static final String ITEM_BATCH = "item:batch";
    public static final String ITEM_CONTACT_LOST = "item:contact:lost";
    public static final String ITEM_CONTACT_FOUND = "item:contact:found";
    public static final String ITEM_RESOLVE_OWN = "item:resolve:own";
    public static final String REVIEW_VIEW = "review:view";
    public static final String REVIEW_DECIDE = "review:decide";
    public static final String CLAIM_CREATE = "claim:create";
    public static final String CLAIM_VIEW_OWN = "claim:view:own";
    public static final String CLAIM_VIEW_ANY = "claim:view:any";
    public static final String CLAIM_REVIEW = "claim:review";
    public static final String CLAIM_CANCEL_OWN = "claim:cancel:own";
    public static final String CLAIM_CANCEL_ANY = "claim:cancel:any";
    public static final String CLAIM_HANDOVER_OWN = "claim:handover:own";
    public static final String AUDIT_VIEW = "audit:view";

    public static Set<String> all() {
        return Set.of(USER_VIEW, USER_EDIT, USER_DISABLE, USER_ROLE, ROLE_VIEW, ROLE_EDIT,
            PERMISSION_VIEW, PERMISSION_EDIT, CATEGORY_VIEW, CATEGORY_ADD, CATEGORY_EDIT,
            CATEGORY_MOVE, CATEGORY_SORT, CATEGORY_ENABLE, CATEGORY_DELETE,
            ITEM_CREATE, ITEM_VIEW_OWN, ITEM_VIEW_ANY, ITEM_EDIT_OWN, ITEM_EDIT_ANY,
            ITEM_DELETE_OWN, ITEM_DELETE_ANY, ITEM_SUBMIT, ITEM_WITHDRAW, ITEM_PUBLISH,
            ITEM_OFFLINE_OWN, ITEM_OFFLINE_ANY, ITEM_BATCH, ITEM_CONTACT_LOST,
            ITEM_CONTACT_FOUND, ITEM_RESOLVE_OWN, REVIEW_VIEW, REVIEW_DECIDE,
            CLAIM_CREATE, CLAIM_VIEW_OWN, CLAIM_VIEW_ANY, CLAIM_REVIEW,
            CLAIM_CANCEL_OWN, CLAIM_CANCEL_ANY, CLAIM_HANDOVER_OWN, AUDIT_VIEW);
    }
}
