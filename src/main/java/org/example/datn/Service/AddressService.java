package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.address.AddressRequest;
import org.example.datn.DTO.response.address.AddressResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Repository.CustomerAddressRepository;
import org.example.datn.Repository.UserRepository;
import org.example.datn.domain.CustomerAddress;
import org.example.datn.domain.User;
import org.example.datn.mapper.AddressMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AddressService {
    private final CustomerAddressRepository addressRepository;
    private final UserRepository userRepository;
    private final AddressMapper addressMapper;

    @Transactional
    public AddressResponse createAddress(Long customerId, AddressRequest request) {
        User customer = userRepository.findByIdOrThrow(customerId, ErrorCode.USER_NOT_FOUND);

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            addressRepository.resetDefaultAddressByCustomerId(customerId);
        }

        CustomerAddress address = addressMapper.toEntity(request);
        address.setCustomer(customer);

        CustomerAddress savedAddress = addressRepository.save(address);
        return addressMapper.toResponse(savedAddress);
    }

    @Transactional
    public AddressResponse updateAddress(Long customerId, Long addressId, AddressRequest request) {
        CustomerAddress addressEntity = addressRepository.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy địa chỉ!"));
        if (!addressEntity.getCustomer().getUserId().equals(customerId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            addressRepository.resetDefaultAddressByCustomerId(customerId);
        }
        addressMapper.updateEntity(request, addressEntity);

        CustomerAddress updatedAddress = addressRepository.save(addressEntity);
        return addressMapper.toResponse(updatedAddress);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(Long customerId) {
        List<CustomerAddress> addresses = addressRepository.findByCustomerUserId(customerId);
        return addresses.stream()
                .map(addressMapper::toResponse)
                .toList();
    }
}