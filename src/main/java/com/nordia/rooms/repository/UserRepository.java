package com.nordia.rooms.repository;

import java.util.Optional;

import com.nordia.rooms.model.AppUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AppUser> findByEmail(String email) {
        return jdbc.sql("""
                        SELECT email, password_hash, display_name
                        FROM app_user
                        WHERE email = :email
                        """)
                .param("email", email)
                .query((rs, rowNum) -> new AppUser(
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getString("display_name")))
                .optional();
    }

    public void upsert(AppUser user) {
        jdbc.sql("""
                        INSERT INTO app_user (email, password_hash, display_name)
                        VALUES (:email, :passwordHash, :displayName)
                        ON CONFLICT(email) DO UPDATE SET
                            password_hash = excluded.password_hash,
                            display_name = excluded.display_name
                        """)
                .param("email", user.email())
                .param("passwordHash", user.passwordHash())
                .param("displayName", user.displayName())
                .update();
    }
}
