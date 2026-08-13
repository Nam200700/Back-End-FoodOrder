package org.example.datn.domain.enums;

public enum GroupOrderMemberStatus {
    JOINED, // đã vào phiên, chưa chắc chọn xong món
    READY,  // đã chọn xong, sẵn sàng để host chốt đơn
    LEFT    // rời phiên trước khi chốt
}
