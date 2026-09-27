/*
 Navicat Premium Data Transfer

 Source Server         : root
 Source Server Type    : MySQL
 Source Server Version : 80100
 Source Host           : localhost:3306
 Source Schema         : mini_calling

 Target Server Type    : MySQL
 Target Server Version : 80100
 File Encoding         : 65001

 Date: 27/09/2026 10:51:11
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for sys_permission
-- ----------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限名，如 用户管理',
  `code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限编码，如 user:delete',
  `type` tinyint(0) NULL DEFAULT NULL COMMENT '类型：1-菜单 2-按钮 3-接口',
  `url` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '关联 URL（可选）',
  `parent_id` bigint(0) NULL DEFAULT 0 COMMENT '父权限 id（树形结构）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `code`(`code`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '权限表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_permission
-- ----------------------------
INSERT INTO `sys_permission` VALUES (1, '用户列表', 'user:list', 3, NULL, 0);
INSERT INTO `sys_permission` VALUES (2, '新增用户', 'user:create', 3, NULL, 0);
INSERT INTO `sys_permission` VALUES (3, '更新用户', 'user:update', 3, NULL, 0);
INSERT INTO `sys_permission` VALUES (4, '删除用户', 'user:delete', 3, NULL, 0);
INSERT INTO `sys_permission` VALUES (5, '分配角色', 'role:assign', 3, NULL, 0);
INSERT INTO `sys_permission` VALUES (6, '角色列表', 'role:list', 3, NULL, 0);
INSERT INTO `sys_permission` VALUES (7, '创建角色', 'role:create', 3, NULL, 0);
INSERT INTO `sys_permission` VALUES (8, '编辑角色', 'role:edit', 3, NULL, 0);
INSERT INTO `sys_permission` VALUES (9, '删除角色', 'role:delete', 3, NULL, 0);
INSERT INTO `sys_permission` VALUES (10, '权限列表', 'permission:list', 3, NULL, 0);

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名，如 ADMIN/USER',
  `code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码，如 ROLE_ADMIN',
  `description` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `name`(`name`) USING BTREE,
  UNIQUE INDEX `code`(`code`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role
-- ----------------------------
INSERT INTO `sys_role` VALUES (1, '管理员', 'admin', '超级管理员', '2026-09-26 17:24:01');
INSERT INTO `sys_role` VALUES (2, '普通用户', 'ROLE_USER', '普通用户', '2026-09-25 12:03:05');
INSERT INTO `sys_role` VALUES (5, '可以删除的角色001', 'delete001', NULL, '2026-09-26 17:16:46');

-- ----------------------------
-- Table structure for sys_role_permission
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission`  (
  `role_id` bigint(0) NOT NULL,
  `permission_id` bigint(0) NOT NULL,
  PRIMARY KEY (`role_id`, `permission_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色权限关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role_permission
-- ----------------------------
INSERT INTO `sys_role_permission` VALUES (1, 1);
INSERT INTO `sys_role_permission` VALUES (1, 2);
INSERT INTO `sys_role_permission` VALUES (1, 3);
INSERT INTO `sys_role_permission` VALUES (1, 4);
INSERT INTO `sys_role_permission` VALUES (1, 5);
INSERT INTO `sys_role_permission` VALUES (1, 6);
INSERT INTO `sys_role_permission` VALUES (1, 7);
INSERT INTO `sys_role_permission` VALUES (1, 8);
INSERT INTO `sys_role_permission` VALUES (1, 9);
INSERT INTO `sys_role_permission` VALUES (1, 10);
INSERT INTO `sys_role_permission` VALUES (5, 1);
INSERT INTO `sys_role_permission` VALUES (5, 9);

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'BCrypt 加密后的密码',
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '昵称',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `enabled` tinyint(0) NULL DEFAULT 1 COMMENT '是否启用：1-是 0-否',
  `deleted` tinyint(0) NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0),
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `username`(`username`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO `sys_user` VALUES (1, 'admin', '$2a$10$QN7sOR14jlLf/OsW7isiReOTI/yiR7CWQFubZeIVaWfvqAJBlfqza', '超级管理员', NULL, NULL, 1, 0, '2026-09-23 23:22:47', '2026-09-24 14:19:28');
INSERT INTO `sys_user` VALUES (2, 'zhangsan', '$2a$10$lMFyGV9Nh8ElkNTL9h9R3OqCXLzi5WoODJQ.U2Nq2LSV4wiGjB7oS', '普通用户', NULL, NULL, 1, 0, '2026-09-25 14:20:31', '2026-09-25 14:20:31');
INSERT INTO `sys_user` VALUES (3, 'doro', '$2a$10$quJvYmDpU/YxxnwOmawll.WMiCn1FpnBlfOucziFMDSK2xw8Qgjgm', '多肉', NULL, NULL, 1, 0, '2026-09-25 14:31:19', '2026-09-25 14:31:19');
INSERT INTO `sys_user` VALUES (4, 'wangwu', '$2a$10$LxN7s/o6I3c45llyF8X3tO0t3tpYCagh64xmRwfu7GEEXyNrZ7rLa', '王五', NULL, NULL, 1, 0, '2026-09-26 17:06:31', '2026-09-26 17:06:31');

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role`  (
  `user_id` bigint(0) NOT NULL,
  `role_id` bigint(0) NOT NULL,
  PRIMARY KEY (`user_id`, `role_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户角色关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user_role
-- ----------------------------
INSERT INTO `sys_user_role` VALUES (1, 1);
INSERT INTO `sys_user_role` VALUES (2, 2);
INSERT INTO `sys_user_role` VALUES (3, 2);
INSERT INTO `sys_user_role` VALUES (4, 5);

SET FOREIGN_KEY_CHECKS = 1;
