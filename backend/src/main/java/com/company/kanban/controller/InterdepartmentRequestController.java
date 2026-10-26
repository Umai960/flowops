package com.company.kanban.controller;
import com.company.kanban.dto.*;
import com.company.kanban.entity.User;
import com.company.kanban.service.InterdepartmentRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
@RestController @RequestMapping("/api/interdepartment-requests")
public class InterdepartmentRequestController {
    private final InterdepartmentRequestService service;
    public InterdepartmentRequestController(InterdepartmentRequestService service){this.service=service;}
    @GetMapping public Page<InterdepartmentRequestResponse> list(@RequestParam(defaultValue = "active") String view, @AuthenticationPrincipal User user, @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable){return service.list(user, view, pageable);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public InterdepartmentRequestResponse create(@Valid @RequestBody CreateInterdepartmentRequest request,@AuthenticationPrincipal User user){return service.create(request,user);}
    @PutMapping("/{id}/status") public InterdepartmentRequestResponse update(@PathVariable Long id,@Valid @RequestBody UpdateInterdepartmentRequestStatus request,@AuthenticationPrincipal User user){return service.update(id,request,user);}
    @PutMapping("/{id}/archive") public InterdepartmentRequestResponse archive(@PathVariable Long id,@AuthenticationPrincipal User user){return service.archive(id,user);}
    @PostMapping(path = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) public InterdepartmentRequestResponse addAttachment(@PathVariable Long id,@RequestParam("file") MultipartFile file,@AuthenticationPrincipal User user){return service.addAttachment(id,file,user);}
    @DeleteMapping("/{id}/attachments/{attachmentId}") public InterdepartmentRequestResponse deleteAttachment(@PathVariable Long id,@PathVariable Long attachmentId,@AuthenticationPrincipal User user){return service.deleteAttachment(id,attachmentId,user);}
    @GetMapping("/{id}/attachments/{attachmentId}") public ResponseEntity<byte[]> attachment(@PathVariable Long id,@PathVariable Long attachmentId,@AuthenticationPrincipal User user){
        InterdepartmentRequestService.AttachmentDownload file=service.attachment(id,attachmentId,user);
        boolean inline=file.contentType().startsWith("image/") || file.contentType().equals("application/pdf");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder(inline ? "inline" : "attachment").filename(file.name(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(file.data());
    }
}
