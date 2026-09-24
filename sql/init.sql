CREATE DATABASE IF NOT EXISTS mini_calling DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mini_calling;

-- 用户表
CREATE TABLE sys_user (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                          username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
                          password VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密后的密码',
                          nickname VARCHAR(50) COMMENT '昵称',
                          email VARCHAR(100),
                          phone VARCHAR(20),
                          enabled TINYINT DEFAULT 1 COMMENT '是否启用：1-是 0-否',
                          deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
                          create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                          update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '用户表';

-- 角色表
CREATE TABLE sys_role (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                          name VARCHAR(50) NOT NULL UNIQUE COMMENT '角色名，如 ADMIN/USER',
                          code VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码，如 ROLE_ADMIN',
                          description VARCHAR(200),
                          create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) COMMENT '角色表';

-- 权限表
CREATE TABLE sys_permission (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                name VARCHAR(50) NOT NULL COMMENT '权限名，如 用户管理',
                                code VARCHAR(100) NOT NULL UNIQUE COMMENT '权限编码，如 user:delete',
                                type TINYINT COMMENT '类型：1-菜单 2-按钮 3-接口',
                                url VARCHAR(200) COMMENT '关联 URL（可选）',
                                parent_id BIGINT DEFAULT 0 COMMENT '父权限 id（树形结构）'
) COMMENT '权限表';

-- 用户-角色 关联表
CREATE TABLE sys_user_role (
                               user_id BIGINT NOT NULL,
                               role_id BIGINT NOT NULL,
                               PRIMARY KEY (user_id, role_id)
) COMMENT '用户角色关联表';

-- 角色-权限 关联表
CREATE TABLE sys_role_permission (
                                     role_id BIGINT NOT NULL,
                                     permission_id BIGINT NOT NULL,
                                     PRIMARY KEY (role_id, permission_id)
) COMMENT '角色权限关联表';

-- 初始化一个管理员账号（密码是 admin123 的 BCrypt 加密值）
INSERT INTO sys_user (username, password, nickname)
VALUES ('admin', '$2a$10$X5wFBtLrL/kUCqFb0CAbU.FSMmEBQ0TZQrVvz3FvGz/UoTkkgA/9a', '超级管理员');

INSERT INTO sys_role (name, code, description)
VALUES ('管理员', 'ROLE_ADMIN', '系统管理员，拥有所有权限');

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
