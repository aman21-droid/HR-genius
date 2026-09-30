package com.hrgenius.employee.controller;

import com.hrgenius.common.dto.PageResponse;
import com.hrgenius.employee.dto.ProfileDtos.*;
import com.hrgenius.employee.entity.Asset.AssetCategory;
import com.hrgenius.employee.entity.Asset.AssetStatus;
import com.hrgenius.employee.service.AssetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Assets")
@RestController
@RequestMapping("/api/v1/assets")
@PreAuthorize("hasAuthority('ASSET_MANAGE')")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    public PageResponse<AssetDto> list(@RequestParam(required = false) String search,
                                       @RequestParam(required = false) AssetCategory category,
                                       @RequestParam(required = false) AssetStatus status,
                                       @PageableDefault(size = 20, sort = "assetTag", direction = Sort.Direction.ASC)
                                       Pageable pageable) {
        return assetService.list(search, category, status, pageable);
    }

    @GetMapping("/{id}")
    public AssetDto get(@PathVariable Long id) {
        return assetService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssetDto create(@Valid @RequestBody AssetRequest request) {
        return assetService.create(request);
    }

    @PutMapping("/{id}")
    public AssetDto update(@PathVariable Long id, @Valid @RequestBody AssetRequest request) {
        return assetService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        assetService.delete(id);
    }

    @PostMapping("/{id}/assign")
    public AssetDto assign(@PathVariable Long id, @Valid @RequestBody AssignAssetRequest request) {
        return assetService.assign(id, request);
    }

    @PostMapping("/{id}/return")
    public AssetDto returnAsset(@PathVariable Long id, @Valid @RequestBody ReturnAssetRequest request) {
        return assetService.returnAsset(id, request);
    }

    @GetMapping("/{id}/history")
    public List<AssetAssignmentDto> history(@PathVariable Long id) {
        return assetService.history(id);
    }
}
