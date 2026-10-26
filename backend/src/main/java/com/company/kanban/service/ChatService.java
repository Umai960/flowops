package com.company.kanban.service;

import com.company.kanban.dto.ChatDtos.*;
import com.company.kanban.entity.*;
import com.company.kanban.repository.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Chat (comment space) inside each interdepartment request. The requester, the handler and
 * everyone in the requesting and receiving departments can read and post; other people can be
 * added to a chat and then see that chat only. Administrators can read every chat.
 */
@Service
public class ChatService {
    static final int PAGE_SIZE = 50;
    static final int MAX_BODY_LENGTH = 4000;
    private static final int PREVIEW_LENGTH = 150;
    private static final int THREAD_LIST_LIMIT = 100;
    private static final Map<String, String> ALLOWED_FILE_TYPES = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"), Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"), Map.entry("webp", "image/webp"), Map.entry("bmp", "image/bmp"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("csv", "text/csv"), Map.entry("txt", "text/plain"), Map.entry("zip", "application/zip"));

    private record Subject(ChatSubjectType type, Long id, String title, Department department, Department otherDepartment, List<User> watchers) {}

    private final ChatThreadRepository threads;
    private final ChatParticipantRepository participants;
    private final ChatMessageRepository messages;
    private final ChatAttachmentRepository attachments;
    private final InterdepartmentRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher events;

    public ChatService(ChatThreadRepository threads, ChatParticipantRepository participants, ChatMessageRepository messages,
                       ChatAttachmentRepository attachments, InterdepartmentRequestRepository requestRepository, UserRepository userRepository,
                       AuthorizationService authorizationService, NotificationService notificationService,
                       ApplicationEventPublisher events) {
        this.threads = threads; this.participants = participants; this.messages = messages; this.attachments = attachments;
        this.requestRepository = requestRepository; this.userRepository = userRepository;
        this.authorizationService = authorizationService; this.notificationService = notificationService; this.events = events;
    }

    @Transactional
    public ChatView view(ChatSubjectType type, Long subjectId, Long beforeId, User user) {
        Subject subject = readableSubject(type, subjectId, user);
        ChatThread thread = threads.findBySubjectTypeAndSubjectId(type, subjectId).orElse(null);
        if (thread == null) return new ChatView(type, subjectId, subject.title(), departmentLabel(subject.department(), subject.otherDepartment()), List.of(), List.of(), false);
        refresh(thread, subject);
        List<ChatMessage> page = messages.findPageBefore(thread.getId(), beforeId == null ? Long.MAX_VALUE : beforeId, PageRequest.of(0, PAGE_SIZE + 1));
        boolean hasMore = page.size() > PAGE_SIZE;
        List<ChatMessageResponse> ordered = new ArrayList<>(page.stream().limit(PAGE_SIZE).map(this::toResponse).toList());
        Collections.reverse(ordered);
        return new ChatView(type, subjectId, thread.getTitle(), departmentLabel(thread.getDepartment(), thread.getOtherDepartment()), people(thread), ordered, hasMore);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> messagesAfter(ChatSubjectType type, Long subjectId, Long afterId, User user) {
        readableSubject(type, subjectId, user);
        return threads.findBySubjectTypeAndSubjectId(type, subjectId)
                .map(thread -> messages.findAfter(thread.getId(), afterId == null ? 0 : afterId, PageRequest.of(0, 200)).stream().map(this::toResponse).toList())
                .orElse(List.of());
    }

    @Transactional
    public ChatMessageResponse post(ChatSubjectType type, Long subjectId, String rawBody, MultipartFile file, User user) {
        Subject subject = readableSubject(type, subjectId, user);
        String body = rawBody == null || rawBody.isBlank() ? null : rawBody.strip();
        boolean hasFile = file != null && !file.isEmpty();
        if (body == null && !hasFile) throw badRequest("Write a message or attach a file");
        if (body != null && body.length() > MAX_BODY_LENGTH) throw badRequest("Message is too long (maximum " + MAX_BODY_LENGTH + " characters)");
        String fileName = null, contentType = null; byte[] data = null;
        if (hasFile) {
            if (file.getSize() > ChatAttachment.MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File is larger than 10 MB");
            fileName = cleanFileName(file.getOriginalFilename());
            contentType = ALLOWED_FILE_TYPES.get(extension(fileName));
            if (contentType == null) throw badRequest("This file type is not allowed. Use images, PDF, Word, Excel, PowerPoint, CSV, TXT or ZIP");
            try { data = file.getBytes(); } catch (IOException e) { throw badRequest("The file could not be read"); }
        }

        ChatThread thread = threadFor(subject);
        ChatMessage message = new ChatMessage(thread, user, body);
        if (hasFile) message.attach(fileName, contentType, data.length);
        messages.save(message);
        if (hasFile) attachments.save(new ChatAttachment(message, data));
        thread.recordMessage(preview(message), message.getCreatedAt());
        follow(thread, user);
        subject.watchers().forEach(watcher -> follow(thread, watcher));

        List<User> followers = participants.findByThreadIdWithUser(thread.getId()).stream().map(ChatParticipant::getUser).toList();
        String destination = destination(type, subjectId);
        followers.forEach(follower -> notificationService.notifyChatMessage(follower, user, "New message: " + thread.getTitle(),
                user.getName() + ": " + preview(message), destination));
        ChatMessageResponse response = toResponse(message);
        publish("MESSAGE", thread, response, followers);
        return response;
    }

    @Transactional
    public void deleteMessage(Long messageId, User user) {
        ChatMessage message = messages.findById(messageId).orElseThrow(() -> notFound("Message not found"));
        ChatThread thread = message.getThread();
        if (!canRead(user, thread.getDepartment(), thread.getOtherDepartment(), thread)) throw notFound("Message not found");
        if (!authorizationService.isAdmin(user) && !message.getSender().getId().equals(user.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own messages");
        if (message.isDeleted()) return;
        attachments.deleteByMessageId(messageId);
        message.markDeleted();
        if (messageId.equals(messages.findLatestId(thread.getId()))) thread.setLastMessagePreview("Message deleted");
        publish("DELETED", thread, toResponse(message), participants.findByThreadIdWithUser(thread.getId()).stream().map(ChatParticipant::getUser).toList());
    }

    public record AttachmentDownload(String name, String contentType, byte[] data) {}

    @Transactional(readOnly = true)
    public AttachmentDownload attachment(Long messageId, User user) {
        ChatMessage message = messages.findById(messageId).orElseThrow(() -> notFound("File not found"));
        ChatThread thread = message.getThread();
        if (!canRead(user, thread.getDepartment(), thread.getOtherDepartment(), thread) || !message.hasAttachment()) throw notFound("File not found");
        ChatAttachment attachment = attachments.findByMessageId(messageId).orElseThrow(() -> notFound("File not found"));
        return new AttachmentDownload(message.getAttachmentName(), message.getAttachmentContentType(), attachment.getData());
    }

    @Transactional
    public List<ChatPerson> addParticipant(ChatSubjectType type, Long subjectId, Long userId, User actor) {
        Subject subject = readableSubject(type, subjectId, actor);
        User invited = userRepository.findById(userId)
                .filter(candidate -> candidate.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> notFound("User not found"));
        ChatThread thread = threadFor(subject);
        if (follow(thread, invited)) {
            notificationService.notifyChatMessage(invited, actor, "Added to a chat",
                    actor.getName() + " added you to the chat for \"" + thread.getTitle() + "\".", destination(type, subjectId));
            List<User> followers = participants.findByThreadIdWithUser(thread.getId()).stream().map(ChatParticipant::getUser).toList();
            publish("PARTICIPANTS", thread, null, followers);
        }
        return people(thread);
    }

    @Transactional
    public void markRead(ChatSubjectType type, Long subjectId, User user) {
        threads.findBySubjectTypeAndSubjectId(type, subjectId).ifPresent(thread ->
                participants.findByThreadIdAndUserId(thread.getId(), user.getId())
                        .ifPresent(participant -> participant.markReadUpTo(messages.findLatestId(thread.getId()))));
    }

    /** scope "mine" lists chats the user follows; "all" adds every chat the user may read (own department, or everything for admins). */
    @Transactional(readOnly = true)
    public List<ChatThreadSummary> threads(String scope, User user) {
        PageRequest limit = PageRequest.of(0, THREAD_LIST_LIMIT);
        List<ChatThread> found;
        if (!"all".equalsIgnoreCase(scope)) found = threads.findFollowedBy(user.getId(), limit);
        else if (authorizationService.isAdmin(user)) found = threads.findAllActive(limit);
        else found = threads.findVisibleTo(user.getId(), user.getDepartment() == null ? -1L : user.getDepartment().getId(), limit);
        Map<Long, Long> unread = unreadByThread(user);
        // Chats left from 1.3.0 tasks and planning items can no longer be opened, so they are not listed.
        return found.stream().filter(thread -> thread.getSubjectType() == ChatSubjectType.REQUEST)
                .map(thread -> new ChatThreadSummary(thread.getSubjectType(), thread.getSubjectId(), thread.getTitle(),
                departmentLabel(thread.getDepartment(), thread.getOtherDepartment()), thread.getLastMessagePreview(), thread.getLastMessageAt(),
                unread.getOrDefault(thread.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(User user) {
        return unreadByThread(user).values().stream().mapToLong(Long::longValue).sum();
    }

    /** Everyone in the company who can be added to a chat. */
    @Transactional(readOnly = true)
    public List<ChatPerson> people() {
        return userRepository.findByStatus(AccountStatus.ACTIVE).stream()
                .sorted(Comparator.comparing(user -> Optional.ofNullable(user.getName()).orElse(""), String.CASE_INSENSITIVE_ORDER))
                .map(this::person).toList();
    }

    /** Called when a task or planning item is deleted, to remove any chat left from 1.3.0. */
    @Transactional
    public void deleteForSubject(ChatSubjectType type, Long subjectId) {
        threads.findBySubjectTypeAndSubjectId(type, subjectId).ifPresent(thread -> deleteThreads(List.of(thread.getId())));
    }

    /** Called before a disabled user is permanently deleted. */
    @Transactional
    public void removeUser(Long userId) {
        attachments.deleteBySenderId(userId);
        messages.deleteBySenderId(userId);
        participants.deleteByUserId(userId);
    }

    /** Retention: messages and files older than the cutoff are deleted, as are chats with no activity since then or whose task/item is gone. */
    @Transactional
    public int purgeOlderThan(LocalDateTime cutoff) {
        attachments.deleteForMessagesOlderThan(cutoff);
        int deleted = messages.deleteOlderThan(cutoff);
        Set<Long> stale = new LinkedHashSet<>(threads.findIdsInactiveSince(cutoff));
        stale.addAll(threads.findOrphanIds());
        deleteThreads(new ArrayList<>(stale));
        return deleted;
    }

    private void deleteThreads(List<Long> ids) {
        for (int start = 0; start < ids.size(); start += 500) {
            List<Long> chunk = ids.subList(start, Math.min(ids.size(), start + 500));
            attachments.deleteByThreadIds(chunk);
            messages.deleteByThreadIds(chunk);
            participants.deleteByThreadIds(chunk);
            threads.deleteByIds(chunk);
        }
    }

    private Subject readableSubject(ChatSubjectType type, Long subjectId, User user) {
        Subject subject = type != ChatSubjectType.REQUEST ? null : requestRepository.findById(subjectId).map(request -> new Subject(type, subjectId,
                request.getTitle(), request.getRequestingDepartment(), request.getTargetDepartment(), requestWatchers(request))).orElse(null);
        // A chat the user may not read is reported as missing so its existence is not revealed.
        if (subject == null || !canRead(user, subject.department(), subject.otherDepartment(), threads.findBySubjectTypeAndSubjectId(type, subjectId).orElse(null)))
            throw notFound("Chat not found");
        return subject;
    }

    // The requester and the handler follow the chat; until someone handles it, the receiving department's managers do.
    private List<User> requestWatchers(InterdepartmentRequest request) {
        List<User> people = new ArrayList<>(List.of(request.getCreatedBy()));
        if (request.getAssignedTo() != null) people.add(request.getAssignedTo());
        else people.addAll(userRepository.findByDepartmentIdAndRole(request.getTargetDepartment().getId(), Role.MANAGER));
        return watchers(people.toArray(User[]::new));
    }

    private boolean canRead(User user, Department department, Department otherDepartment, ChatThread thread) {
        if (authorizationService.isAdmin(user)) return true;
        if (department != null && authorizationService.canAccessDepartment(user, department.getId())) return true;
        if (otherDepartment != null && authorizationService.canAccessDepartment(user, otherDepartment.getId())) return true;
        return thread != null && thread.getId() != null && participants.existsByThreadIdAndUserId(thread.getId(), user.getId());
    }

    private ChatThread threadFor(Subject subject) {
        ChatThread thread = threads.findBySubjectTypeAndSubjectId(subject.type(), subject.id()).orElse(null);
        if (thread != null) { refresh(thread, subject); return thread; }
        thread = threads.save(new ChatThread(subject.type(), subject.id(), truncate(subject.title(), 200), subject.department(), subject.otherDepartment()));
        for (User watcher : subject.watchers()) follow(thread, watcher);
        return thread;
    }

    private void refresh(ChatThread thread, Subject subject) {
        String title = truncate(subject.title(), 200);
        if (!Objects.equals(thread.getTitle(), title)) thread.setTitle(title);
        if (subject.department() != null && (thread.getDepartment() == null || !thread.getDepartment().getId().equals(subject.department().getId())))
            thread.setDepartment(subject.department());
        if (subject.otherDepartment() != null && (thread.getOtherDepartment() == null || !thread.getOtherDepartment().getId().equals(subject.otherDepartment().getId())))
            thread.setOtherDepartment(subject.otherDepartment());
    }

    private boolean follow(ChatThread thread, User user) {
        if (user == null || participants.existsByThreadIdAndUserId(thread.getId(), user.getId())) return false;
        participants.save(new ChatParticipant(thread, user));
        return true;
    }

    private Map<Long, Long> unreadByThread(User user) {
        Map<Long, Long> unread = new HashMap<>();
        for (Object[] row : participants.countUnreadByThread(user.getId())) unread.put((Long) row[0], ((Number) row[1]).longValue());
        return unread;
    }

    private void publish(String kind, ChatThread thread, ChatMessageResponse message, List<User> followers) {
        Set<Long> ids = new HashSet<>();
        followers.forEach(follower -> ids.add(follower.getId()));
        Set<Long> departmentIds = new HashSet<>();
        if (thread.getDepartment() != null) departmentIds.add(thread.getDepartment().getId());
        if (thread.getOtherDepartment() != null) departmentIds.add(thread.getOtherDepartment().getId());
        events.publishEvent(new ChatEventBroker.Delivery(new ChatEvent(kind, thread.getSubjectType(), thread.getSubjectId(), message), departmentIds, ids));
    }

    private List<ChatPerson> people(ChatThread thread) {
        return participants.findByThreadIdWithUser(thread.getId()).stream().map(participant -> person(participant.getUser())).toList();
    }

    private ChatPerson person(User user) {
        return new ChatPerson(user.getId(), user.getName(), departmentName(user.getDepartment()), user.getRole() == null ? null : user.getRole().name());
    }

    private ChatMessageResponse toResponse(ChatMessage message) {
        User sender = message.getSender();
        ChatAttachmentInfo attachment = message.hasAttachment()
                ? new ChatAttachmentInfo(message.getAttachmentName(), message.getAttachmentContentType(), message.getAttachmentSize(),
                message.getAttachmentContentType().startsWith("image/"))
                : null;
        return new ChatMessageResponse(message.getId(), message.getThread().getSubjectType(), message.getThread().getSubjectId(),
                sender.getId(), sender.getName(), departmentName(sender.getDepartment()), message.getBody(), attachment,
                message.getCreatedAt(), message.isDeleted());
    }

    static String destination(ChatSubjectType type, Long subjectId) {
        return "/chat?type=" + type.name().toLowerCase(Locale.ROOT) + "&id=" + subjectId;
    }

    static String cleanFileName(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>|:*?]", "_").strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) name = "file";
        if (name.length() > 200) {
            String ext = extension(name);
            name = name.substring(0, 200 - ext.length() - 1) + "." + ext;
        }
        return name;
    }

    static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String preview(ChatMessage message) {
        if (message.getBody() != null) return truncate(message.getBody().replaceAll("\\s+", " "), PREVIEW_LENGTH);
        return "Sent a file: " + truncate(message.getAttachmentName(), PREVIEW_LENGTH - 13);
    }

    private static List<User> watchers(User... users) {
        List<User> found = new ArrayList<>();
        for (User user : users) if (user != null && found.stream().noneMatch(existing -> existing.getId().equals(user.getId()))) found.add(user);
        return found;
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private static String departmentName(Department department) { return department == null ? null : department.getName(); }

    private static String departmentLabel(Department department, Department otherDepartment) {
        if (otherDepartment == null || department == null) return departmentName(department == null ? otherDepartment : department);
        return department.getName() + " → " + otherDepartment.getName();
    }
    private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
