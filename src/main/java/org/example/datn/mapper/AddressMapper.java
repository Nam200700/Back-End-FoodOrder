package org.example.datn.mapper;

import org.example.datn.DTO.request.address.AddressRequest;
import org.example.datn.DTO.response.address.AddressResponse;
import org.example.datn.domain.CustomerAddress;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AddressMapper {

    AddressResponse toResponse(CustomerAddress customerAddress);

    @Mapping(target = "addressId", ignore = true)
    @Mapping(target = "customer", ignore = true)
    CustomerAddress toEntity(AddressRequest addressRequest);

    @Mapping(target = "addressId", ignore = true)
    @Mapping(target = "customer", ignore = true)
    void updateEntity(AddressRequest addressRequest, @MappingTarget CustomerAddress customerAddress);
}
