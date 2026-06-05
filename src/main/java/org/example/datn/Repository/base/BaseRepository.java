package org.example.datn.Repository.base;

import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Shared repository contract. Provides {@link #findByIdOrThrow} so services do
 * not repeat {@code orElseThrow(...)} everywhere.
 */
@NoRepositoryBean
public interface BaseRepository<T, ID> extends JpaRepository<T, ID> {

    default T findByIdOrThrow(ID id, ErrorCode errorCode) {
        return findById(id).orElseThrow(() -> new AppException(errorCode));
    }
}
