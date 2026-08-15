package org.example.datn.domain.enums;

public enum GroupOrderStatus {
    OPEN,       // đang mời thành viên chọn món
    LOCKED,     // host khóa, chờ checkout
    ORDERED,    // đã chốt thành 1 order thật
    CANCELLED,  // host hủy phiên
    EXPIRED     // quá hạn join_deadline mà chưa chốt
}
