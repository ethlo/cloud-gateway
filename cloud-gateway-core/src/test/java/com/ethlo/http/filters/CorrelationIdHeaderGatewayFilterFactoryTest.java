package com.ethlo.http.mvc.filters;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CorrelationIdHeaderFilterTest {

    private MockMvc mockMvc;
    private final String headerName = "X-Custom-Correlation-Id";

    @BeforeEach
    void setUp() {
        // We set up MockMvc with our custom filter
        this.mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .addFilters(new CorrelationIdMvcFilter(headerName))
                .build();
    }

    @Test
    void shouldAddCorrelationIdHeaderToResponse() throws Exception {
        mockMvc.perform(get("/test"))
                .andExpect(status().isOk())
                // Verify the response header exists (Deterministic in MVC)
                .andExpect(header().exists(headerName));
    }
}