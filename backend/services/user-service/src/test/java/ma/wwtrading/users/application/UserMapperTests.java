package ma.wwtrading.users.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import ma.wwtrading.users.api.UpdateUserProfileRequest;
import ma.wwtrading.users.domain.UserEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class UserMapperTests {

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Test
    void mapsEntityAndRolesToResponse() {
        UserEntity user = UserEntity.fromIdentity("subject-1", "client@example.test", "Ali", "Test", "fr_MA");
        user.setPhone("+212600000000");

        var response = mapper.toResponse(user, Set.of("CLIENT"));

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.email()).isEqualTo("client@example.test");
        assertThat(response.phone()).isEqualTo("+212600000000");
        assertThat(response.roles()).containsExactly("CLIENT");
    }

    @Test
    void patchIgnoresNullValuesAndUpdatePhone() {
        UserEntity user = UserEntity.fromIdentity("subject-1", "client@example.test", "Ali", "Test", "fr_MA");

        mapper.update(new UpdateUserProfileRequest("Amina", null, null, " +212611112233 "), user);

        assertThat(user.getFirstName()).isEqualTo("Amina");
        assertThat(user.getLastName()).isEqualTo("Test");
        assertThat(user.getEmail()).isEqualTo("client@example.test");
        assertThat(user.getPhone()).isEqualTo("+212611112233");
    }
}
