package com.doubleclick.wadii.chat;

import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.files.FileAccessRule;
import com.doubleclick.wadii.files.FilePurpose;
import com.doubleclick.wadii.files.FileService;
import com.doubleclick.wadii.identity.AdminRole;
import com.doubleclick.wadii.identity.User;
import com.doubleclick.wadii.identity.UserRepository;
import com.doubleclick.wadii.listing.Listing;
import com.doubleclick.wadii.listing.ListingRepository;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.order.ServiceOrder;
import com.doubleclick.wadii.order.ServiceOrderRepository;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderRepository;
import com.doubleclick.wadii.request.RequestTargetRepository;
import com.doubleclick.wadii.request.ServiceRequest;
import com.doubleclick.wadii.request.ServiceRequestRepository;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Conversations between the parties of an order, a request or a listing. Support staff may read any thread. */
@Service
@RequiredArgsConstructor
public class ChatService implements FileAccessRule {

    public static final String FILE_REF = "CONVERSATION";

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ServiceOrderRepository orders;
    private final ServiceRequestRepository requests;
    private final RequestTargetRepository targets;
    private final ProviderRepository providers;
    private final ListingRepository listings;
    private final UserRepository users;
    private final FileService files;
    private final NotificationService notifications;
    private final Clock clock;

    public record OpenInput(@NotNull Conversation.Kind kind, @NotNull Long refId, Long providerId) {
    }

    public record SendInput(@Size(max = 4000) String body, String fileKey) {
    }

    public record ConversationView(Long id, Conversation.Kind kind, Long refId, String title, String counterpartName,
                                   String lastMessage, Instant lastMessageAt, long unread) {
    }

    public record MessageView(Long id, Long senderId, boolean mine, String body, String fileKey, Instant createdAt) {
    }

    @Transactional
    public ConversationView open(AuthUser user, OpenInput input) {
        Long other = counterpart(user, input);
        if (other.equals(user.id())) {
            throw BusinessException.validation("You cannot start a conversation with yourself.");
        }
        Long a = Math.min(user.id(), other);
        Long b = Math.max(user.id(), other);
        Conversation c = conversations.findByKindAndRefIdAndUserAAndUserB(input.kind(), input.refId(), a, b).orElseGet(() -> {
            Conversation fresh = new Conversation();
            fresh.setKind(input.kind());
            fresh.setRefId(input.refId());
            fresh.setUserA(a);
            fresh.setUserB(b);
            return conversations.save(fresh);
        });
        return view(c, user.id());
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationView> list(AuthUser user, Integer page, Integer size) {
        return PageResponse.of(conversations.forUser(user.id(), PageResponse.pageable(page, size)), c -> view(c, user.id()));
    }

    @Transactional
    public List<MessageView> messages(AuthUser user, Long conversationId, Long afterId, int limit) {
        Conversation c = readable(user, conversationId);
        List<Message> list = messages.after(conversationId, afterId == null ? 0 : afterId, PageRequest.of(0, Math.min(Math.max(limit, 1), 100)));
        if (!list.isEmpty() && c.hasParticipant(user.id())) {
            c.markRead(user.id(), list.get(list.size() - 1).getId());
        }
        return list.stream().map(m -> new MessageView(m.getId(), m.getSenderId(), m.getSenderId().equals(user.id()), m.getBody(),
                m.getFileKey(), m.getCreatedAt())).toList();
    }

    @Transactional
    public MessageView send(AuthUser user, Long conversationId, SendInput input) {
        Conversation c = conversations.findById(conversationId).filter(x -> x.hasParticipant(user.id()))
                .orElseThrow(() -> BusinessException.of(ErrorCode.CONVERSATION_NOT_FOUND));
        boolean hasBody = input.body() != null && !input.body().isBlank();
        if (!hasBody && input.fileKey() == null) {
            throw BusinessException.validation("A message needs text or a file.");
        }
        if (input.fileKey() != null) {
            files.attach(user.id(), List.of(input.fileKey()), FilePurpose.CHAT, FILE_REF, conversationId);
        }
        Message m = messages.save(new Message(conversationId, user.id(), hasBody ? input.body().trim() : null, input.fileKey()));
        c.setLastMessageAt(Instant.now(clock));
        c.markRead(user.id(), m.getId());
        notifications.notify(c.other(user.id()), NotificationType.NEW_MESSAGE, "New message from " + user.fullName(),
                hasBody ? preview(input.body()) : "Sent a file", "CONVERSATION", conversationId);
        return new MessageView(m.getId(), m.getSenderId(), true, m.getBody(), m.getFileKey(), m.getCreatedAt());
    }

    @Override
    public boolean supports(String refType) {
        return FILE_REF.equals(refType);
    }

    @Override
    public boolean canRead(AuthUser user, String refType, Long refId) {
        return conversations.findById(refId).map(c -> c.hasParticipant(user.id())).orElse(false);
    }

    private Conversation readable(AuthUser user, Long id) {
        Conversation c = conversations.findById(id).orElseThrow(() -> BusinessException.of(ErrorCode.CONVERSATION_NOT_FOUND));
        if (!c.hasParticipant(user.id()) && !user.hasAdminRole(AdminRole.SUPPORT_ADMIN)) {
            throw BusinessException.of(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        return c;
    }

    /** Resolves the other participant and checks the caller is allowed to talk about this subject. */
    private Long counterpart(AuthUser user, OpenInput input) {
        return switch (input.kind()) {
            case ORDER -> {
                ServiceOrder o = orders.findById(input.refId()).orElseThrow(() -> BusinessException.of(ErrorCode.ORDER_NOT_FOUND));
                if (o.getCustomerId().equals(user.id())) yield o.getProviderUserId();
                if (o.getProviderUserId().equals(user.id())) yield o.getCustomerId();
                throw BusinessException.of(ErrorCode.ORDER_NOT_FOUND);
            }
            case REQUEST -> {
                ServiceRequest r = requests.findById(input.refId()).orElseThrow(() -> BusinessException.of(ErrorCode.REQUEST_NOT_FOUND));
                if (r.getCustomerId().equals(user.id())) {
                    if (input.providerId() == null || targets.findByRequestIdAndProviderId(r.getId(), input.providerId()).isEmpty()) {
                        throw BusinessException.validation("providerId must be a provider invited to this request.");
                    }
                    yield providers.findById(input.providerId()).map(Provider::getUserId).orElseThrow();
                }
                Provider p = providers.findByUserId(user.id()).orElseThrow(() -> BusinessException.of(ErrorCode.REQUEST_NOT_FOUND));
                if (targets.findByRequestIdAndProviderId(r.getId(), p.getId()).isEmpty()) {
                    throw BusinessException.of(ErrorCode.REQUEST_NOT_FOUND);
                }
                yield r.getCustomerId();
            }
            case LISTING -> {
                Listing l = listings.findById(input.refId())
                        .filter(x -> x.getStatus() == Listing.Status.ACTIVE || x.getStatus() == Listing.Status.SOLD)
                        .orElseThrow(() -> BusinessException.of(ErrorCode.LISTING_NOT_FOUND));
                yield l.getSellerId();
            }
        };
    }

    private ConversationView view(Conversation c, Long me) {
        Long otherId = c.other(me);
        String counterpart = providers.findByUserId(otherId).filter(p -> c.getKind() != Conversation.Kind.LISTING)
                .filter(Provider::isVerified).map(Provider::getBusinessName)
                .orElseGet(() -> users.findById(otherId).map(User::getFullName).orElse(""));
        String title = switch (c.getKind()) {
            case ORDER -> "Order #" + c.getRefId();
            case REQUEST -> "Request #" + c.getRefId();
            case LISTING -> listings.findById(c.getRefId()).map(Listing::getTitle).orElse("Listing");
        };
        var last = messages.findFirstByConversationIdOrderByIdDesc(c.getId()).orElse(null);
        long unread = c.hasParticipant(me) ? messages.countUnread(c.getId(), c.lastReadBy(me), me) : 0;
        return new ConversationView(c.getId(), c.getKind(), c.getRefId(), title, counterpart,
                last == null ? null : last.getBody() != null ? preview(last.getBody()) : "File", c.getLastMessageAt(), unread);
    }

    private static String preview(String body) {
        String t = body.trim();
        return t.length() > 120 ? t.substring(0, 117) + "..." : t;
    }
}
