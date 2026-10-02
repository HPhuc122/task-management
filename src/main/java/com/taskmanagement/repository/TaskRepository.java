package com.taskmanagement.repository;

import com.taskmanagement.entity.Task;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("SELECT t FROM Task t WHERE t.user.id = :userId ORDER BY t.id DESC")
    List<Task> findFirstPageByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.user.id = :userId AND t.id < :cursor ORDER BY t.id DESC")
    List<Task> findNextPageByUserId(@Param("userId") Long userId,
            @Param("cursor") Long cursor, Pageable pageable);
}
