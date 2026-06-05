package com.turkcell.product_service.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.turkcell.product_service.entity.OutboxEvent;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query(value="""
            Select * from outbox
            Where status = 'PENDING' and retry_count < 3
            ORDER BY created_at
            LIMIT :limit
            """, nativeQuery = true)
    List<OutboxEvent> findPublishable(@Param("limit") int limit);
}