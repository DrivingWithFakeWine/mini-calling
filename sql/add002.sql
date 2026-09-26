-- 之前的管理员角色的 code错了，更换为admin
UPDATE `mini_calling`.`sys_role` SET `name` = '管理员', `code` = 'admin', `description` = '超级管理员', `create_time` = '2026-09-26 17:24:01' WHERE `id` = 1;

-- 插入新的permission权限内容
INSERT INTO `mini_calling`.`sys_permission`(`id`, `name`, `code`, `type`, `url`, `parent_id`) VALUES (5, '分配角色', 'role:assign', 3, NULL, 0);
INSERT INTO `mini_calling`.`sys_permission`(`id`, `name`, `code`, `type`, `url`, `parent_id`) VALUES (6, '角色列表', 'role:list', 3, NULL, 0);
INSERT INTO `mini_calling`.`sys_permission`(`id`, `name`, `code`, `type`, `url`, `parent_id`) VALUES (7, '创建角色', 'role:create', 3, NULL, 0);
INSERT INTO `mini_calling`.`sys_permission`(`id`, `name`, `code`, `type`, `url`, `parent_id`) VALUES (8, '编辑角色', 'role:edit', 3, NULL, 0);
INSERT INTO `mini_calling`.`sys_permission`(`id`, `name`, `code`, `type`, `url`, `parent_id`) VALUES (9, '删除角色', 'role:delete', 3, NULL, 0);

-- 给 admin角色 赋予新的权限permission
INSERT INTO `mini_calling`.`sys_role_permission`(`role_id`, `permission_id`) VALUES (1, 5);
INSERT INTO `mini_calling`.`sys_role_permission`(`role_id`, `permission_id`) VALUES (1, 6);
INSERT INTO `mini_calling`.`sys_role_permission`(`role_id`, `permission_id`) VALUES (1, 7);
INSERT INTO `mini_calling`.`sys_role_permission`(`role_id`, `permission_id`) VALUES (1, 8);
INSERT INTO `mini_calling`.`sys_role_permission`(`role_id`, `permission_id`) VALUES (1, 9);

-- 创建新的权限，id=10,name=权限列表
INSERT INTO `mini_calling`.`sys_permission`(`id`, `name`, `code`, `type`, `url`, `parent_id`) VALUES (10, '权限列表', 'permission:list', 3, NULL, 0);

-- 插入sys_role_permission表，给 admin角色 新添加 权限 id=10,name=权限列表
INSERT INTO `mini_calling`.`sys_role_permission`(`role_id`, `permission_id`) VALUES (1, 10);
