package com.doubleclick.wadii.chat;

import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.order.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** One conversation per order, opened when an offer is accepted. Access: the two participants and support admins. */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public Conversation openForOrder(Order order) {
        return conversationRepository.findByOrderId(order.getId()).orElseGet(() -> {
            Conversation c = new Conversation();
            c.setOrderId(order.getId());
            c.setCustomerId(order.getCustomer().getId());
            c.setProviderId(order.getAcceptedProvider().getId());
            c.setProviderUserId(order.getAcceptedProvider().getUser().getId());
            c.setLastMessageAt(clock.instant());
            c.setLastMessagePreview("Conversation opened for order #" + order.getId());
            c = conversationRepository.save(c);
            Message m = new Message();
            m.setConversationId(c.getId());
            m.setType(MessageType.SYSTEM);
            m.setText("Offer accepted. You can now chat about order #" + order.getId() + ".");
            messageRepository.save(m);
            return c;
        });
    }

    public Conversation load(Long id) {
        return conversationRepository.findById(id).orElseThrow(() -> BusinessException.notFound(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    /** Participants only (for sending, calling). */
    public Conversation requireParticipant(AuthUser me, Long conversationId) {
        Conversation c = load(conversationId);
        if (!c.isParticipant(me.id())) throw BusinessException.forbidden("You are not a participant of this conversation");
        return c;
    }

    /** Participants or support admins (for reading). */
    public Conversation requireReader(AuthUser me, Long conversationId) {
        Conversation c = load(conversationId);
        if (!c.isParticipant(me.id()) && !me.isSupport()) throw BusinessException.forbidden("You are not allowed to read this conversation");
        return c;
    }
}
