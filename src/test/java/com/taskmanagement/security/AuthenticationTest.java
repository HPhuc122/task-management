package com.taskmanagement.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.taskmanagement.controller.AuthController;
import com.taskmanagement.controller.TaskController;
import com.taskmanagement.entity.User;
import com.taskmanagement.entity.UserRole;
import com.taskmanagement.repository.UserRepository;
import com.taskmanagement.service.AuthService;
import com.taskmanagement.service.TaskService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthController.class, TaskController.class})
@Import({SecurityTestConfig.class, AuthService.class})
@TestPropertySource(properties = {SecurityTestConfig.SECRET_PROPERTY, "app.cors.allowed-origins=http://localhost:3000"})
class AuthenticationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;
    @MockitoBean UserRepository users;
    @MockitoBean TaskService tasks;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setEmail("user@example.test");
        user.setPasswordHash(passwords.encode("password123"));
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @Test
    void registerHashesPasswordForcesUserRoleAndIssuesUsableToken() throws Exception {
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            assertEquals(UserRole.USER, saved.getRole());
            assertTrue(passwords.matches("password123", saved.getPasswordHash()));
            assertNotEquals("password123", saved.getPasswordHash());
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.test\",\"password\":\"password123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(body).get("accessToken").asText();
        assertEquals("7", decoder.decode(token).getSubject());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void loginTokenWorksWithoutSessionAndRoleIsReloadedFromDatabase() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", user.getEmail(), "password", "password123"))))
                .andExpect(status().isOk()).andExpect(cookie().doesNotExist("JSESSIONID"))
                .andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(body).get("accessToken").asText();
        mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        user.setRole(UserRole.ADMIN);
        // No user CRUD endpoint exists yet; passing authorization reaches MVC's 404.
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        when(users.findById(7L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-password", "password123"})
    void wrongPasswordAndUnknownEmailHaveSameSafeError(String password) throws Exception {
        String email = password.equals("password123") ? "missing@example.test" : user.getEmail();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.detail").value("Invalid credentials or authentication required"));
    }

    @Test
    void rejectsDuplicateEmailAndInvalidInputs() throws Exception {
        when(users.existsByEmail(user.getEmail())).thenReturn(true);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", user.getEmail(), "password", "password123"))))
                .andExpect(status().isConflict());
        for (String password : new String[]{"short", " ", "a".repeat(73), "ệ".repeat(25)}) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("email", "invalid", "password", password))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.password").exists())
                    .andExpect(jsonPath("$.errors.email").exists())
                    .andExpect(jsonPath("$.password").doesNotExist());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void unauthenticatedMalformedExpiredAndTamperedTokensReturnProblem401() throws Exception {
        mvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.instance").value("/api/tasks"));
        Instant now = Instant.now();
        String valid = token(encoder, "task-management", "7", now.minusSeconds(1), now.plusSeconds(60));
        JwtEncoder otherSigner = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(new byte[32], "HmacSHA256")));
        String[] invalid = {
                "garbage", valid.substring(0, valid.lastIndexOf('.') + 1) + "invalid",
                token(encoder, "task-management", "7", now.minusSeconds(120), now.minusSeconds(60)),
                token(encoder, "other-issuer", "7", now, now.plusSeconds(60)),
                token(encoder, "task-management", "not-an-id", now, now.plusSeconds(60)),
                token(encoder, "task-management", "0", now, now.plusSeconds(60)),
                token(encoder, "task-management", "7", now, null),
                token(otherSigner, "task-management", "7", now, now.plusSeconds(60))
        };
        for (String invalidToken : invalid) {
            mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + invalidToken))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401))
                    .andExpect(header().string("WWW-Authenticate", "Bearer"));
        }
        verifyNoInteractions(tasks);
    }

    @Test
    void corsPreflightAllowsConfiguredOriginWithoutAuthentication() throws Exception {
        mvc.perform(options("/api/tasks").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mvc.perform(options("/api/tasks").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private String token(JwtEncoder signer, String issuer, String subject, Instant issued, Instant expires) {
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(subject).issuedAt(issued);
        if (expires != null) claims.expiresAt(expires);
        return signer.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }
}
