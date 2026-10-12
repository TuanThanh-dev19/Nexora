package com.nexora.user.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.user.dto.SkillResponse;
import com.nexora.user.service.SkillService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/skills")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Skills", description = "Skill lookup APIs")
@SecurityRequirement(name = "bearerAuth")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    @Operation(summary = "Get list of active skills")
    public List<SkillResponse> list(@RequestParam(name = "active", required = false) Boolean active) {
        return skillService.getActiveSkills(active);
    }
}
