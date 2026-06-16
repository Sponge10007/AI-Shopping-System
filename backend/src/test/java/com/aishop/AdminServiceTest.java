package com.aishop;

import com.aishop.infrastructure.ai.AiServiceClient;
import com.aishop.modules.admin.AdminService;
import com.aishop.modules.admin.dto.AdminMetricsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private AiServiceClient aiServiceClient;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(jdbcTemplate, aiServiceClient);
    }

    @Test
    void overviewFallsBackToSampleCountsAndDownStatusesWhenDependenciesFail() {
        when(aiServiceClient.health()).thenReturn(false);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        AdminMetricsResponse response = adminService.overview();

        assertThat(response.userCount()).isEqualTo(2);
        assertThat(response.productCount()).isEqualTo(2);
        assertThat(response.orderCount()).isEqualTo(1);
        assertThat(response.aiServiceStatus()).isEqualTo("DOWN");
        assertThat(response.vectorDbStatus()).isEqualTo("UNKNOWN");
    }
}
