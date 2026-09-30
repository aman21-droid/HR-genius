package com.hrgenius.recruitment.controller;

import com.hrgenius.recruitment.dto.RecruitmentDtos.CareerApplyForm;
import com.hrgenius.recruitment.dto.RecruitmentDtos.CareerJobDto;
import com.hrgenius.recruitment.service.CareersService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Public, unauthenticated careers API (permitted in SecurityConfig). Exposes only published open
 * roles and their public fields; applications are rate-limited per client in {@link CareersService}.
 */
@Tag(name = "Careers (public)")
@RestController
@RequestMapping("/api/v1/public/careers")
public class CareersController {

    private final CareersService careers;

    public CareersController(CareersService careers) {
        this.careers = careers;
    }

    @Operation(summary = "Open roles on the careers page")
    @GetMapping("/jobs")
    public List<CareerJobDto> jobs() {
        return careers.jobs();
    }

    @Operation(summary = "One open role")
    @GetMapping("/jobs/{reqCode}")
    public CareerJobDto job(@PathVariable String reqCode) {
        return careers.job(reqCode);
    }

    @Operation(summary = "Apply for a role (multipart: form fields + resume)")
    @PostMapping(value = "/jobs/{reqCode}/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> apply(@PathVariable String reqCode,
                                     @Valid @ModelAttribute CareerApplyForm form,
                                     @RequestParam(value = "resume", required = false) MultipartFile resume,
                                     HttpServletRequest request) {
        // remoteAddr is the connecting peer; behind a proxy set server.forward-headers-strategy so it is
        // resolved from trusted forwarding headers rather than trusting a client-supplied X-Forwarded-For.
        careers.apply(reqCode, form, resume, request.getRemoteAddr());
        return Map.of("message", "Thanks for applying! Our recruiting team will be in touch if there is a match.");
    }
}
