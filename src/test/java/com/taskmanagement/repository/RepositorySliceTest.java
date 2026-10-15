package com.taskmanagement.repository;

import com.taskmanagement.entity.Category;
import com.taskmanagement.entity.Project;
import com.taskmanagement.entity.Task;
import com.taskmanagement.entity.TaskAttachment;
import com.taskmanagement.entity.User;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "RUN_DATABASE_TESTS", matches = "true")
class RepositorySliceTest {
    @Autowired EntityManager entityManager;
    @Autowired TaskRepository tasks;
    @Autowired ProjectRepository projects;
    @Autowired TaskAttachmentRepository attachments;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;

    @Test
    void cursorPaginationIsOrderedAndRestrictedToOwner() {
        User owner = owner();
        User another = owner();
        Task first = task(owner, "first");
        Task second = task(owner, "second");
        Task third = task(owner, "third");
        task(another, "someone else's task");
        entityManager.flush();
        entityManager.clear();

        assertEquals(List.of(third.getId(), second.getId()),
                tasks.findFirstPageByUserId(owner.getId(), PageRequest.of(0, 2))
                        .stream().map(Task::getId).toList());
        assertEquals(List.of(first.getId()),
                tasks.findNextPageByUserId(owner.getId(), second.getId(), PageRequest.of(0, 2))
                        .stream().map(Task::getId).toList());
        assertTrue(tasks.findNextPageByUserId(owner.getId(), first.getId(), PageRequest.of(0, 2)).isEmpty());
    }

    @Test
    void ownerFiltersApplyToTaskAndProjectRepositories() {
        User owner = owner();
        User another = owner();
        Task ownedTask = task(owner, "owned");
        task(another, "other");
        Project ownedProject = project(owner);
        project(another);
        entityManager.flush();
        entityManager.clear();

        assertEquals(List.of(ownedTask.getId()), tasks.findAllByUserId(owner.getId(), Sort.by("id"))
                .stream().map(Task::getId).toList());
        assertEquals(List.of(ownedProject.getId()), projects.findAllByUserId(owner.getId(), Sort.by("id"))
                .stream().map(Project::getId).toList());
    }

    @Test
    void attachmentLookupCannotCrossTaskBoundary() {
        User owner = owner();
        Task first = task(owner, "first");
        Task second = task(owner, "second");
        TaskAttachment earlier = attachment(first, "first.txt");
        TaskAttachment later = attachment(first, "second.txt");
        attachment(second, "other.txt");
        entityManager.flush();
        entityManager.clear();

        assertEquals(List.of(earlier.getId(), later.getId()),
                attachments.findAllByTaskIdOrderById(first.getId()).stream()
                        .map(TaskAttachment::getId).toList());
        assertTrue(attachments.findByIdAndTaskId(earlier.getId(), first.getId()).isPresent());
        assertTrue(attachments.findByIdAndTaskId(earlier.getId(), second.getId()).isEmpty());
    }

    @Test
    void userEmailAndCategoryPersistThroughRepositories() {
        User owner = owner();
        Category category = new Category();
        category.setName("category-" + UUID.randomUUID());
        categories.saveAndFlush(category);
        entityManager.clear();

        assertEquals(owner.getId(), users.findByEmail(owner.getEmail()).orElseThrow().getId());
        assertTrue(users.existsByEmail(owner.getEmail()));
        assertFalse(users.existsByEmail("missing@example.test"));
        assertEquals(category.getName(), categories.findById(category.getId()).orElseThrow().getName());
    }

    private User owner() {
        User user = new User();
        user.setEmail("slice-" + UUID.randomUUID() + "@example.test");
        user.setPasswordHash("test-only-encoded-placeholder");
        entityManager.persist(user);
        return user;
    }

    private Task task(User owner, String title) {
        Task task = new Task();
        task.setTitle(title);
        task.setUser(owner);
        entityManager.persist(task);
        return task;
    }

    private Project project(User owner) {
        Project project = new Project();
        project.setName("Project " + UUID.randomUUID());
        project.setUser(owner);
        entityManager.persist(project);
        return project;
    }

    private TaskAttachment attachment(Task task, String name) {
        TaskAttachment attachment = new TaskAttachment();
        attachment.setTask(task);
        attachment.setObjectKey(UUID.randomUUID().toString());
        attachment.setFileName(name);
        attachment.setContentType("text/plain");
        attachment.setSizeBytes(1);
        entityManager.persist(attachment);
        return attachment;
    }
}
