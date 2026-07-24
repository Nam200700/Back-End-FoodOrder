package org.example.datn.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.address.AddressRequest;
import org.example.datn.DTO.response.address.AddressResponse;
import org.example.datn.Service.AddressService;
import org.example.datn.common.ApiResponse;
import org.example.datn.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class AddressController {
    private final AddressService addressService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getAddresses(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(addressService.getAddresses(user.getUserId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AddressResponse>> createAddress(
            @AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(addressService.createAddress(user.getUserId(), request)));
    }

    @PutMapping("/{addressId}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long addressId,
            @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(addressService.updateAddress(user.getUserId(), addressId, request)));
    }
}