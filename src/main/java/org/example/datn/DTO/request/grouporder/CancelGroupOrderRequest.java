package org.example.datn.DTO.request.grouporder;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelGroupOrderRequest {

    @NotBlank(message = "Vui lòng nhập lý do hủy")
    private String reason;
}
