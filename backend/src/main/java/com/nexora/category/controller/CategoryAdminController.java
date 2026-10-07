package com.nexora.category.controller;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.nexora.category.dto.CategoryContracts.*;
import com.nexora.category.service.CategoryService;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class CategoryAdminController {
    private final CategoryService service;
    public CategoryAdminController(CategoryService service) { this.service = service; }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() { return service.categories(true); }

    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@RequestBody @Valid CategoryRequest request, @AuthenticationPrincipal Jwt actor) {
        return service.createCategory(request, Long.valueOf(actor.getSubject()));
    }

    @PutMapping("/categories/{categoryId}")
    public CategoryResponse update(@PathVariable Long categoryId, @RequestBody @Valid CategoryRequest request) {
        return service.updateCategory(categoryId, request);
    }

    @GetMapping("/categories/{categoryId}/form-versions")
    public List<VersionResponse> versions(@PathVariable Long categoryId) { return service.versions(categoryId); }

    @PostMapping("/categories/{categoryId}/form-versions/drafts") @ResponseStatus(HttpStatus.CREATED)
    public FormResponse draft(@PathVariable Long categoryId, @AuthenticationPrincipal Jwt actor) {
        return service.createDraft(categoryId, Long.valueOf(actor.getSubject()));
    }

    @GetMapping("/form-versions/{formVersionId}")
    public FormResponse form(@PathVariable Long formVersionId) { return service.form(formVersionId); }

    @PutMapping("/form-versions/{formVersionId}")
    public FormResponse updateDraft(@PathVariable Long formVersionId, @RequestBody @Valid DraftRequest request) {
        return service.updateDraft(formVersionId, request);
    }

    @PostMapping("/form-versions/{formVersionId}/publish")
    public FormResponse publish(@PathVariable Long formVersionId, @RequestBody @Valid RevisionRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return service.publish(formVersionId, request.revision(), Long.valueOf(actor.getSubject()));
    }

    @PostMapping("/form-versions/{formVersionId}/archive")
    public FormResponse archive(@PathVariable Long formVersionId, @RequestBody @Valid RevisionRequest request) {
        return service.archive(formVersionId, request.revision());
    }

    @GetMapping("/skills")
    public List<SkillResponse> skills() { return service.skills(); }
}
