package com.nexora.user.service;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.nexora.user.dto.SkillResponse;
import com.nexora.user.entity.Skill;
import com.nexora.user.repository.SkillRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SkillServiceTest {

    @Mock
    private SkillRepository skillRepository;

    private SkillService service;

    @BeforeEach
    void setUp() {
        service = new SkillService(skillRepository);
    }

    @Test
    @DisplayName("getActiveSkills: active=true trả về danh sách active skills")
    void getActiveSkills_activeTrue_returnsActiveList() {
        Skill s1 = new Skill("ELECTRIC", "Sửa điện");
        ReflectionTestUtils.setField(s1, "id", 1L);
        Skill s2 = new Skill("PLUMB", "Ống nước");
        ReflectionTestUtils.setField(s2, "id", 2L);

        given(skillRepository.findActiveOrderByNameAndCode()).willReturn(List.of(s1, s2));

        List<SkillResponse> result = service.getActiveSkills(true);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).code()).isEqualTo("ELECTRIC");
        assertThat(result.get(1).code()).isEqualTo("PLUMB");
    }

    @Test
    @DisplayName("getActiveSkills: active=null cũng mặc định trả về danh sách active skills")
    void getActiveSkills_activeNull_returnsActiveList() {
        Skill s1 = new Skill("ELECTRIC", "Sửa điện");
        ReflectionTestUtils.setField(s1, "id", 1L);

        given(skillRepository.findActiveOrderByNameAndCode()).willReturn(List.of(s1));

        List<SkillResponse> result = service.getActiveSkills(null);

        assertThat(result).hasSize(1);
    }
}
