package com.aishop.infrastructure.persistence.repository;

import com.aishop.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户表数据访问层
 *
 * 提供的方法（Spring Data JPA 自动实现）：
 * - findByUsername(String): 根据用户名查询用户（登录时使用）
 * - findByPhone(String): 根据手机号查询用户（登录时使用）
 * - findByUserId(String): 根据业务主键查询用户
 * - existsByUsername(String): 检查用户名是否已存在（注册时校验）
 * - existsByPhone(String): 检查手机号是否已存在
 * - findMaxUserIdNumeric: 查询数据库中最大的 userId 数字部分（用于初始化计数器）
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsername(String username);

    Optional<UserEntity> findByPhone(String phone);

    Optional<UserEntity> findByUserId(String userId);

    boolean existsByUsername(String username);

    boolean existsByPhone(String phone);

    // 查询数据库中最大的 userId 数字部分（用于初始化计数器）
    // userId 格式为 m10001 或 u10001，SUBSTRING 去掉前缀字母后取数字部分
    @Query("SELECT MAX(CAST(SUBSTRING(u.userId, 2) AS long)) FROM UserEntity u")
    Long findMaxUserIdNumeric();
}
