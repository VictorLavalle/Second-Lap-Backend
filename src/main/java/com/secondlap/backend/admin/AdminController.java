package com.secondlap.backend.admin;

import com.secondlap.backend.admin.dto.AdminMeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @GetMapping("/me")
    public AdminMeResponse me(Principal principal) {
        return new AdminMeResponse(principal.getName());
    }
}
