package org.example.datn.mapper;

import org.example.datn.DTO.request.voucher.VoucherRequest;
import org.example.datn.DTO.response.voucher.VoucherResponse;
import org.example.datn.domain.Voucher;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface VoucherMapper {

    VoucherResponse toResponse(Voucher voucher);

    Voucher toEntity(VoucherRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateVoucher(@MappingTarget Voucher voucher, VoucherRequest request);
}
