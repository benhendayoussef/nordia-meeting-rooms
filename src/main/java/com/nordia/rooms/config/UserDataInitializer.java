package com.nordia.rooms.config;

import com.nordia.rooms.model.AppUser;
import com.nordia.rooms.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserDataInitializer implements ApplicationRunner {

    private final DemoUsers demoUsers;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserDataInitializer(DemoUsers demoUsers, UserRepository users, PasswordEncoder passwordEncoder) {
        this.demoUsers = demoUsers;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        demoUsers.demoUsers().forEach(user -> users.upsert(new AppUser(
                user.email(), passwordEncoder.encode(user.password()), user.displayName())));
    }
}
