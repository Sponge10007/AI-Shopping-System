package com.aishop.infrastructure.persistence.repository;

import com.aishop.infrastructure.persistence.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 审计日志数据访问层
 *
 * 提供的方法：
 * - findByOperatorId: 按操作人查询
 * - findByAction: 按操作类型查询
 * - findByTargetTypeAndTargetId: 按目标类型+目标ID查询
 * - findByCreatedAtBetween: 按时间范围查询
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    Page<AuditLogEntity> findByOperatorId(String operatorId, Pageable pageable);

    List<AuditLogEntity> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    List<AuditLogEntity> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, String targetId);

    List<AuditLogEntity> findByCreatedAtBetween(OffsetDateTime start, OffsetDateTime end);
}
