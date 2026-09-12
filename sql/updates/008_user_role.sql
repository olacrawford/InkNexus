-- 008: 增加用户角色字段，图书管理接口的增删改只允许 ADMIN 角色调用（接口安全收敛）
-- 适用：已经按 sql/sql.txt 或 001~007 初始化过的存量环境。
-- 全新环境不需要执行本脚本，直接运行 sql/sql.txt 初始化即可（已包含该字段）。

ALTER TABLE t_user
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：USER普通用户 ADMIN管理员';

-- 提升管理员账号：默认把 id=1 的用户设为管理员，按需改成自己的账号后重新执行
UPDATE t_user SET role = 'ADMIN' WHERE id = 1;

-- 说明：角色随登录写入 JWT 的 role claim，由网关解析后以 X-User-Role 透传给下游；
-- 旧签发的 token 不携带 role，网关会按 USER 处理，管理员重新登录后生效。
