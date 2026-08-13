package org.example.datn.DTO.request.grouporder;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateGroupOrderItemRequest {

    @NotNull
    @Min(value = 1, message = "Số lượng tối thiểu là 1")
    private Integer quantity;

    private String note;
}
