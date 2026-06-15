package com.aishop.infrastructure.persistence.repository;

import com.aishop.infrastructure.persistence.entity.BehaviorLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 用户行为日志数据访问层
 *
 * 提供的方法：
 * - findByUserId: 按用户查询行为记录
 * - findByUserIdAndEventType: 按用户+事件类型查询
 * - findByEventType: 按事件类型查询（如查询所有 VIEW 事件）
 * - countByEventTypeAndCreatedAtAfter: 统计某段时间内的事件数量
 * - findByCreatedAtBetween: 按时间范围查询
 */
@Repository
public interface BehaviorLogRepository extends JpaRepository<BehaviorLogEntity, Long> {

    List<BehaviorLogEntity> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    List<BehaviorLogEntity> findByUserIdAndEventTypeOrderByCreatedAtDesc(String userId, String eventType, Pageable pageable);

    List<BehaviorLogEntity> findByEventTypeOrderByCreatedAtDesc(String eventType, Pageable pageable);

    long countByEventTypeAndCreatedAtAfter(String eventType, OffsetDateTime after);

    List<BehaviorLogEntity> findByCreatedAtBetween(OffsetDateTime start, OffsetDateTime end);
}
