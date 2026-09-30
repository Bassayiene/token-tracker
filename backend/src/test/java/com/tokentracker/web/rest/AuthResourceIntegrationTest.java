package com.tokentracker.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tokentracker.client.WavesDataApiClient;
import com.tokentracker.client.WavesNodeClient;
import com.tokentracker.domain.Role;
import com.tokentracker.repository.AppUserRepository;

/**
 * Registration, login and real JWTs going through the security filter chain.
 * The admin account comes from {@code token-tracker.security.admin-*} of the test configuration.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthResourceIntegrationTest {

    private static final String TN = "bPWkA3MNyEr1TuDchWgdpqJZhGhfPXj7dJdr3qiW2kD";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppUserRepository userRepository;

    @MockitoBean private WavesDataApiClient dataApiClient;
    @MockitoBean private WavesNodeClient nodeClient;

    @Test
    void adminAccountIsCreatedFromTheConfiguration() {
        assertThat(userRepository.findByUsername("admin"))
                .hasValueSatisfying(u -> assertThat(u.getRole()).isEqualTo(Role.ADMIN));
    }

    @Test
    void registeredUsersReadDailyDataButCannotSync() throws Exception {
        String token = tokenOf(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("Alice", "correct-horse")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("USER"));
        mockMvc.perform(get("/api/tokens/" + TN + "/holders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/tokens/" + TN + "/sync").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminTokenCarriesTheAdminRole() throws Exception {
        String token = tokenOf(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("ADMIN", "admin-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void wrongCredentialsAreRejectedTheSameWay() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("admin", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorKey").value("badcredentials"))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("nobody", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void registrationIsValidatedAndUsernamesAreUnique() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("bob", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorKey").value("validation"));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("no spaces", "long-enough-password")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("Admin", "long-enough-password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorKey").value("usernameexists"));
    }

    @Test
    void meRequiresAToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    private String credentials(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", username, "password", password));
    }

    private String tokenOf(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return node.get("token").asText();
    }
}
