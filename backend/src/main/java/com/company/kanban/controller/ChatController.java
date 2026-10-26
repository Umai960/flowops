package com.company.kanban.controller;

import com.company.kanban.dto.ChatDtos.*;
import com.company.kanban.entity.ChatSubjectType;
import com.company.kanban.entity.User;
import com.company.kanban.service.ChatEventBroker;
import com.company.kanban.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;
    private final ChatEventBroker broker;

    public ChatController(ChatService chatService, ChatEventBroker broker) {
        this.chatService = chatService;
        this.broker = broker;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal User user) { return broker.subscribe(user); }

    @GetMapping("/threads")
    public List<ChatThreadSummary> threads(@RequestParam(defaultValue = "mine") String scope, @AuthenticationPrincipal User user) {
        return chatService.threads(scope, user);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal User user) { return Map.of("count", chatService.unreadCount(user)); }

    @GetMapping("/people")
    public List<ChatPerson> people() { return chatService.people(); }

    @GetMapping("/{type}/{subjectId}")
    public ChatView view(@PathVariable String type, @PathVariable Long subjectId, @RequestParam(required = false) Long before,
                         @AuthenticationPrincipal User user) {
        return chatService.view(subjectType(type), subjectId, before, user);
    }

    @GetMapping("/{type}/{subjectId}/messages")
    public List<ChatMessageResponse> messagesAfter(@PathVariable String type, @PathVariable Long subjectId,
                                                   @RequestParam(defaultValue = "0") Long after, @AuthenticationPrincipal User user) {
        return chatService.messagesAfter(subjectType(type), subjectId, after, user);
    }

    @PostMapping(path = "/{type}/{subjectId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse post(@PathVariable String type, @PathVariable Long subjectId,
                                    @RequestParam(required = false) String body,
                                    @RequestParam(required = false) MultipartFile file,
                                    @AuthenticationPrincipal User user) {
        return chatService.post(subjectType(type), subjectId, body, file, user);
    }

    @PostMapping("/{type}/{subjectId}/participants")
    public List<ChatPerson> addParticipant(@PathVariable String type, @PathVariable Long subjectId,
                                           @Valid @RequestBody AddParticipantRequest request, @AuthenticationPrincipal User user) {
        return chatService.addParticipant(subjectType(type), subjectId, request.userId(), user);
    }

    @PostMapping("/{type}/{subjectId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable String type, @PathVariable Long subjectId, @AuthenticationPrincipal User user) {
        chatService.markRead(subjectType(type), subjectId, user);
    }

    @DeleteMapping("/messages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal User user) { chatService.deleteMessage(id, user); }

    @GetMapping("/messages/{id}/attachment")
    public ResponseEntity<byte[]> attachment(@PathVariable Long id, @AuthenticationPrincipal User user) {
        ChatService.AttachmentDownload file = chatService.attachment(id, user);
        boolean inline = file.contentType().startsWith("image/") || file.contentType().equals("application/pdf");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder(inline ? "inline" : "attachment")
                        .filename(file.name(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(file.data());
    }

    private static ChatSubjectType subjectType(String value) {
        try { return ChatSubjectType.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found"); }
    }
}
