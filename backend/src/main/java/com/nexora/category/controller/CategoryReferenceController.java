package com.nexora.category.controller;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import com.nexora.category.dto.CategoryContracts.*;
import com.nexora.category.service.CategoryService;

@RestController
@RequestMapping("/api/v1")
public class CategoryReferenceController {
    private final CategoryService service;
    public CategoryReferenceController(CategoryService service) { this.service = service; }

    /** Reference categories always exclude inactive records, including active=false requests. */
    @GetMapping("/categories")
    public List<CategoryResponse> categories(@RequestParam(defaultValue = "true") boolean active) { return service.categories(false); }

    @GetMapping("/categories/{categoryId}/published-form")
    public FormResponse published(@PathVariable Long categoryId) { return service.published(categoryId); }

    @GetMapping("/sla-policies")
    public List<SlaResponse> slas(@RequestParam(defaultValue = "true") boolean active) { return service.slaPolicies(active); }
}
