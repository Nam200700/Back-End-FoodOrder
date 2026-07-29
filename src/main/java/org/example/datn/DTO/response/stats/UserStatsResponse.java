package org.example.datn.DTO.response.stats;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserStatsResponse {
    private long totalUser;
    private long activeUser;
    private long blockedUser;
    private long totalAdmin;
    private long totalCustomer;
    private long totalOwner;
    private long totalShipper;
}
