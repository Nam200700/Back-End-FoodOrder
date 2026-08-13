package org.example.datn.domain.enums;

/**
 * Trạng thái của MỘT lần gán giao hàng (delivery) cho shipper.
 * Một đơn có thể có nhiều delivery theo thời gian (shipper bỏ đơn → shipper khác nhận lại),
 * nên trạng thái này thuộc về từng lần gán chứ không phải trạng thái đơn.
 */
public enum DeliveryStatus {
    ASSIGNED,   // đang được gán cho shipper (đang giao)
    COMPLETED,  // shipper đã giao xong
    CANCELLED   // shipper bỏ đơn (đơn được trả về pool cho shipper khác)
}
