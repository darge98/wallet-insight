package it.walletinsight.bff.users;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.walletinsight.core.users.application.UserService;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.platform.web.ApiPaths;
import it.walletinsight.platform.web.PageResponse;
import it.walletinsight.shared.page.PageRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/** CRUD degli utenti e delle loro impostazioni. */
@RestController
@RequestMapping(ApiPaths.API + "/users")
@Tag(name = "Users")
class UsersController {

    private final UserService service;

    UsersController(UserService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = service.createUser(
                request.firstName(), request.lastName(), request.email(), request.toSettings());
        URI location = URI.create(ApiPaths.API + "/users/" + user.id());
        return ResponseEntity.created(location).body(UserResponse.from(user));
    }

    @GetMapping
    PageResponse<UserResponse> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageRequest.DEFAULT_SIZE) int size) {
        return PageResponse.from(service.listUsers(PageRequest.of(page, size)), UserResponse::from);
    }

    @GetMapping("/{id}")
    UserResponse getUser(@PathVariable UUID id) {
        return UserResponse.from(service.getUser(UserId.of(id)));
    }

    @PutMapping("/{id}")
    UserResponse updateUser(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        User user = service.updateUser(
                UserId.of(id),
                request.firstName(), request.lastName(), request.email(), request.toSettings());
        return UserResponse.from(user);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        service.deleteUser(UserId.of(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/settings")
    UserSettingsResponse getSettings(@PathVariable UUID id) {
        return UserSettingsResponse.from(service.getSettings(UserId.of(id)));
    }

    @PutMapping("/{id}/settings")
    UserSettingsResponse updateSettings(
            @PathVariable UUID id, @Valid @RequestBody UserSettingsRequest request) {
        User user = service.updateSettings(UserId.of(id), request.toDomain());
        return UserSettingsResponse.from(user.settings());
    }
}
