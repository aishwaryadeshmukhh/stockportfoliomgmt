package com.portfolio;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getPortfolio_returnsOkWithList() throws Exception {
        mockMvc.perform(get("/api/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getSummary_returnsOkWithExpectedFields() throws Exception {
        mockMvc.perform(get("/api/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInvested").exists())
                .andExpect(jsonPath("$.totalCurrentValue").exists())
                .andExpect(jsonPath("$.holdingsCount").exists());
    }

    @Test
    void addStock_withMissingFields_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/portfolio/add")
                        .contentType("application/json")
                        .content("{\"symbol\":\"NVDA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removeStock_withUnknownSymbol_returnsBadRequest() throws Exception {
        mockMvc.perform(delete("/api/portfolio/UNKNOWN"))
                .andExpect(status().isBadRequest());
    }
}
