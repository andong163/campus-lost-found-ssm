-- GMY 第二轮集成：登记完整分类管理权限。先执行 003、004，再执行本脚本。
-- 仅 ADMIN 默认获得分类管理；学生和审核员不自动获得分类写权限。
INSERT INTO sys_permission(code, name) VALUES
 ('category:view', '查看分类管理树'),
 ('category:add', '新增分类'),
 ('category:edit', '修改分类名称'),
 ('category:move', '移动分类'),
 ('category:sort', '调整分类排序'),
 ('category:enable', '启停分类'),
 ('category:delete', '逻辑删除分类')
ON DUPLICATE KEY UPDATE name=VALUES(name);

INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
 ('category:view','category:add','category:edit','category:move','category:sort','category:enable','category:delete')
WHERE r.code='ADMIN'
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);
