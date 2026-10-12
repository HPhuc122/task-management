package com.taskmanagement.repository;

import com.taskmanagement.entity.TaskAttachment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {
    List<TaskAttachment> findAllByTaskIdOrderById(Long taskId);
    Optional<TaskAttachment> findByIdAndTaskId(Long id, Long taskId);
}
