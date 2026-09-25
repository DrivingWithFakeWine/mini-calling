-- 权限码（type=3 表示接口权限）
INSERT INTO sys_permission (name, code, type) VALUES
                                                  ('用户列表', 'user:list', 3),
                                                  ('新增用户', 'user:create', 3),
                                                  ('更新用户', 'user:update', 3),
                                                  ('删除用户', 'user:delete', 3);

-- admin 角色（id=1）全量授权
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission;

-- 建普通角色 + 绑定（为了测出 200/403 差异；user_id 按你实际账号调整）
INSERT INTO sys_role (name, code, description) VALUES ('普通用户', 'ROLE_USER', '普通用户');
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 2, id FROM sys_permission WHERE code = 'user:list';
INSERT INTO sys_user_role (user_id, role_id) VALUES (2, 2);   -- 假设普通账号 id=2
