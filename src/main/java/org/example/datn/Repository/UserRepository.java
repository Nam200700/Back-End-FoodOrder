package org.example.datn.Repository;

import org.example.datn.domain.User;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends BaseRepository<User, Long> {

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);
}
