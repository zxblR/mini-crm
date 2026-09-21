package com.minicrm.leads;

import com.minicrm.common.CurrentUser;
import com.minicrm.common.RoleCode;
import com.minicrm.common.Roles;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/leads")
public class ExportController {
  private final ExportService exportService;

  public ExportController(ExportService exportService) {
    this.exportService = exportService;
  }

  @GetMapping("/export")
  @Roles({RoleCode.OWNER, RoleCode.ADMIN})
  public ResponseEntity<StreamingResponseBody> export(Authentication authentication,
      @RequestParam(required = false) String status, @RequestParam(required = false) String ownerId,
      @RequestParam(required = false) String keyword, @RequestParam(required = false) String source,
      @RequestParam(required = false) String from, @RequestParam(required = false) String to,
      @RequestParam(required = false) Boolean archived, jakarta.servlet.http.HttpServletRequest request) {
    ExportService.Export result = exportService.export(CurrentUser.require(authentication), status, ownerId,
        keyword, source, from, to, archived, request);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
    headers.setContentDisposition(ContentDisposition.attachment().filename(result.filename()).build());
    return ResponseEntity.ok().headers(headers).body(result.body());
  }
}
