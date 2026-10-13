package com.taskmanagement;

import com.taskmanagement.dto.TaskAttachmentResponse;
import com.taskmanagement.security.SecurityTestConfig;
import com.taskmanagement.service.TaskAttachmentService;
import com.taskmanagement.service.TaskService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real PostgreSQL and S3-compatible storage; no mocked repositories or storage client. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {SecurityTestConfig.SECRET_PROPERTY,
        "app.storage.create-bucket=true", "app.storage.path-style-access=true",
        "app.storage.bucket=attachment-integration-tests"})
@EnabledIfEnvironmentVariable(named = "RUN_ATTACHMENT_TESTS", matches = "true")
class AttachmentStorageIntegrationTest {
    private static final String BUCKET = "attachment-integration-tests";
    private static final byte[] CONTENT = "MinIO thực tế\n".getBytes(StandardCharsets.UTF_8);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired TaskAttachmentService attachments;
    @Autowired TaskService tasks;
    @Autowired S3Client s3;
    @Autowired PlatformTransactionManager transactions;
    private Long actor;
    private Long task;

    @BeforeEach
    void setup() {
        actor = jdbc.queryForObject("INSERT INTO users(email,password_hash) VALUES (?, 'test') RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test");
        task = jdbc.queryForObject("INSERT INTO tasks(title,user_id) VALUES ('Attachment test', ?) RETURNING id",
                Long.class, actor);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                actor.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void cleanup() {
        try {
            for (var object : objects()) {
                s3.deleteObject(DeleteObjectRequest.builder().bucket(BUCKET).key(object.key()).build());
            }
            jdbc.update("DELETE FROM task_attachments WHERE task_id = ?", task);
            jdbc.update("DELETE FROM tasks WHERE id = ?", task);
            jdbc.update("DELETE FROM users WHERE id = ?", actor);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void httpUploadDownloadDeleteRoundTrip() throws Exception {
        String body = mvc.perform(multipart("/api/tasks/{id}/attachments", task).file(file())
                        .with(user(actor.toString()).roles("USER")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(body).get("id").asLong();
        String key = key(id);
        assertArrayEquals(CONTENT, objectBytes(key));
        byte[] downloaded = mvc.perform(get("/api/tasks/{task}/attachments/{id}/content", task, id)
                        .with(user(actor.toString()).roles("USER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(CONTENT, downloaded);
        mvc.perform(delete("/api/tasks/{task}/attachments/{id}", task, id)
                        .with(user(actor.toString()).roles("USER"))).andExpect(status().isNoContent());
        assertEquals(0, rowCount());
        assertMissing(key);
    }

    @Test
    void uploadIsRemovedWhenOuterTransactionRollsBackAfterSuccessfulFlush() {
        AtomicReference<String> key = new AtomicReference<>();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var response = attachments.upload(task, file());
            key.set(key(response.id()));
            assertArrayEquals(CONTENT, objectBytes(key.get()));
            tx.setRollbackOnly();
        });
        assertEquals(0, rowCount());
        assertMissing(key.get());
    }

    @Test
    void metadataInsertFailureRollsBackAndCleansUploadedObject() {
        AtomicReference<String> key = new AtomicReference<>();
        assertThrows(RuntimeException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            // Transactional DDL disappears on rollback; no permanent fault injection.
            jdbc.execute("""
                    CREATE FUNCTION pg_temp.reject_attachment_insert() RETURNS trigger AS $$
                    BEGIN RAISE EXCEPTION 'Injected attachment metadata failure'; END;
                    $$ LANGUAGE plpgsql
                    """);
            jdbc.execute("""
                    CREATE TRIGGER reject_attachment_insert BEFORE INSERT ON task_attachments
                    FOR EACH ROW EXECUTE FUNCTION pg_temp.reject_attachment_insert()
                    """);
            try {
                attachments.upload(task, file());
                fail("Metadata insert should fail");
            } catch (RuntimeException exception) {
                assertEquals(1, objects().size());
                key.set(objects().getFirst().key());
                throw exception;
            }
        }));
        assertNotNull(key.get());
        assertEquals(0, rowCount());
        assertMissing(key.get());
    }

    @Test
    void databaseFailureDuringCommitPreparationCleansUploadedObject() {
        AtomicReference<String> key = new AtomicReference<>();
        assertThrows(RuntimeException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            key.set(key(attachments.upload(task, file()).id()));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    // PostgreSQL CHECK violation after saveAndFlush has already succeeded.
                    jdbc.update("UPDATE tasks SET title = '' WHERE id = ?", task);
                }
            });
        }));
        assertNotNull(key.get());
        assertEquals(0, rowCount());
        assertMissing(key.get());
    }

    @Test
    void attachmentDeletePreservesObjectOnRollbackAndRemovesItAfterCommit() {
        TaskAttachmentResponse uploaded = attachments.upload(task, file());
        String key = key(uploaded.id());
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            attachments.delete(task, uploaded.id());
            assertArrayEquals(CONTENT, objectBytes(key));
            tx.setRollbackOnly();
        });
        assertEquals(1, rowCount());
        assertArrayEquals(CONTENT, attachments.download(task, uploaded.id()).bytes());
        attachments.delete(task, uploaded.id());
        assertEquals(0, rowCount());
        assertMissing(key);
    }

    @Test
    void taskDeletePreservesAllObjectsOnRollbackAndRemovesThemAfterCommit() {
        String first = key(attachments.upload(task, file()).id());
        String second = key(attachments.upload(task, file()).id());
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            tasks.delete(task);
            assertArrayEquals(CONTENT, objectBytes(first));
            assertArrayEquals(CONTENT, objectBytes(second));
            tx.setRollbackOnly();
        });
        assertEquals(2, rowCount());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tasks WHERE id = ?", Integer.class, task));
        tasks.delete(task);
        assertEquals(0, rowCount());
        assertMissing(first);
        assertMissing(second);
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "notes.txt", "text/plain", CONTENT);
    }

    private String key(long id) {
        return jdbc.queryForObject("SELECT object_key FROM task_attachments WHERE id = ?", String.class, id);
    }

    private int rowCount() {
        return jdbc.queryForObject("SELECT count(*) FROM task_attachments WHERE task_id = ?", Integer.class, task);
    }

    private byte[] objectBytes(String key) {
        return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(BUCKET).key(key).build()).asByteArray();
    }

    private void assertMissing(String key) {
        S3Exception error = assertThrows(S3Exception.class,
                () -> s3.headObject(HeadObjectRequest.builder().bucket(BUCKET).key(key).build()));
        assertEquals(404, error.statusCode());
    }

    private List<S3Object> objects() {
        return s3.listObjectsV2(ListObjectsV2Request.builder().bucket(BUCKET)
                .prefix("tasks/" + task + "/").build()).contents();
    }
}
