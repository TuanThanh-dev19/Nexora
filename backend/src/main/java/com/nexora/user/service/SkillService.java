package com.nexora.user.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.user.dto.SkillResponse;
import com.nexora.user.entity.Skill;
import com.nexora.user.repository.SkillRepository;

@Service
@Transactional(readOnly = true)
public class SkillService {

    private final SkillRepository skillRepository;

    public SkillService(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    public List<SkillResponse> getActiveSkills(Boolean active) {
        List<Skill> skills;
        if (active == null || Boolean.TRUE.equals(active)) {
            skills = skillRepository.findActiveOrderByNameAndCode();
        } else {
            skills = skillRepository.findAll();
        }

        return skills.stream()
                .map(SkillResponse::from)
                .toList();
    }
}
