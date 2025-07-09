package me.zhengjie.modules.charts.rest;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.charts.service.JiraChartService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jira")
@RequiredArgsConstructor
public class JiraChartController {

    private final JiraChartService jiraService;

    @Data
    public class JiraLoginDTO {
        private String username;
        private String password;
    }


    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody JiraLoginDTO loginDTO) {
        System.out.println("login--------------------------");
        try {
            boolean success = jiraService.login(loginDTO.username, loginDTO.password);
            return success ?
                    ResponseEntity.ok("Login successful") :
                    ResponseEntity.badRequest().body("Login failed - check credentials");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Login error: " + e.getMessage());
        }
    }

    @GetMapping("/filter-stats")
    public ResponseEntity<String> getFilterStats(
            @RequestParam String filterId,
            @RequestParam(defaultValue = "reporter") String xstattype,
            @RequestParam(defaultValue = "project") String ystattype,
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestParam(defaultValue = "natural") String sortBy,
            @RequestParam(defaultValue = "5") int numberToShow) {

        try {
            String result = jiraService.getFilterStats(
                    filterId, xstattype, ystattype,
                    sortDirection, sortBy, numberToShow);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error getting filter stats: " + e.getMessage());
        }
    }
}
