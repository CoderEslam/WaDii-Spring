package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.MessageDto;
import com.doubleclick.wadii.entities.ChatContact;
import com.doubleclick.wadii.entities.Message;
import com.doubleclick.wadii.repository.ChatContactRepository;
import com.doubleclick.wadii.repository.MessageRepository;
import com.doubleclick.wadii.ts.Controller;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/messages")
public class MessageController extends Controller<Message, MessageDto, Long> {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChatContactRepository chatContactRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public ResponseEntity<Response<Message>> show(Long id) {
        return messageRepository.findById(id)
                .map(message -> Response.response(message, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no message with this id : " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<Message>> insert(Authentication authentication, @RequestBody MessageDto messageDto) {
        if (messageDto.isNotEmpty()) {
            Optional<User> fromUserOptional = userRepository.findByEmail(authentication.getName());
            if (fromUserOptional.isEmpty()) {
                return Response.response(null, "authenticated user not found", ResponseType.NOT_FOUND);
            }
            Optional<User> toUserOptional = userRepository.findById(messageDto.getToUserId());
            if (toUserOptional.isEmpty()) {
                return Response.response(null, "there is no user with this id : " + messageDto.getToUserId(), ResponseType.NOT_FOUND);
            }
            Message message = new Message();
            message.setText(messageDto.getText());
            message.setType(messageDto.getType());
            message.setFromUser(fromUserOptional.get());
            message.setToUser(toUserOptional.get());
            message = messageRepository.save(message);
            upsertChatContact(fromUserOptional.get(), toUserOptional.get(), messageDto.getText(), messageDto.getType());
            upsertChatContact(toUserOptional.get(), fromUserOptional.get(), messageDto.getText(), messageDto.getType());
            messagingTemplate.convertAndSendToUser(String.valueOf(toUserOptional.get().getId()), "/queue/messages", message);
            messagingTemplate.convertAndSendToUser(String.valueOf(fromUserOptional.get().getId()), "/queue/messages", message);
            return Response.response(message, "Message sent successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "text, type, or toUserId is empty", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<Message>> update(@RequestBody MessageDto messageDto) {
        if (messageDto.getId() == null) {
            return Response.response(null, "message id is required", ResponseType.ERROR);
        }
        Optional<Message> messageOptional = messageRepository.findById(messageDto.getId());
        if (messageOptional.isEmpty()) {
            return Response.response(null, "there is no message with this id : " + messageDto.getId(), ResponseType.NOT_FOUND);
        }
        Message message = messageOptional.get();
        if (messageDto.getText() != null && !messageDto.getText().trim().isEmpty()) {
            message.setText(messageDto.getText());
        }
        if (messageDto.getType() != null && !messageDto.getType().trim().isEmpty()) {
            message.setType(messageDto.getType());
        }
        message = messageRepository.save(message);
        return Response.response(message, "Message updated successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<Message>> delete(Long id) {
        Optional<Message> messageOptional = messageRepository.findById(id);
        if (messageOptional.isPresent()) {
            messageRepository.deleteById(id);
            return Response.response(null, "message deleted successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "there is no message with this id : " + id, ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<List<Message>>> readAll() {
        return Response.response(messageRepository.findAll(), "All messages", ResponseType.SUCCESS);
    }


    @GetMapping("/sent")
    public ResponseEntity<Response<List<Message>>> getSentMessages(Authentication authentication) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "authenticated user not found", ResponseType.NOT_FOUND);
        }
        List<Message> messages = messageRepository.findByFromUserIdOrderByCreatedAtDesc(userOptional.get().getId());
        return Response.response(messages, "Sent messages", ResponseType.SUCCESS);
    }

    @GetMapping("/received")
    public ResponseEntity<Response<List<Message>>> getReceivedMessages(Authentication authentication) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "authenticated user not found", ResponseType.NOT_FOUND);
        }
        List<Message> messages = messageRepository.findByToUserIdOrderByCreatedAtDesc(userOptional.get().getId());
        return Response.response(messages, "Received messages", ResponseType.SUCCESS);
    }

    @GetMapping("/conversation/{userId}")
    public ResponseEntity<Response<Page<Message>>> getConversation(
            Authentication authentication,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Optional<User> currentUserOptional = userRepository.findByEmail(authentication.getName());
        if (currentUserOptional.isEmpty()) {
            return Response.response(null, "authenticated user not found", ResponseType.NOT_FOUND);
        }
        Optional<User> otherUserOptional = userRepository.findById(userId);
        if (otherUserOptional.isEmpty()) {
            return Response.response(null, "there is no user with this id : " + userId, ResponseType.NOT_FOUND);
        }
        Page<Message> conversation = messageRepository.findConversation(
                currentUserOptional.get().getId(),
                userId,
                PageRequest.of(page, size)
        );
        return Response.response(conversation, "Conversation", ResponseType.SUCCESS);
    }

    @GetMapping("/chat-list")
    public ResponseEntity<Response<List<ChatContact>>> getChatList(Authentication authentication) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "authenticated user not found", ResponseType.NOT_FOUND);
        }
        List<ChatContact> contacts = chatContactRepository.findByUserIdOrderByLastMessageAtDesc(userOptional.get().getId());
        return Response.response(contacts, "Chat list", ResponseType.SUCCESS);
    }

    private void upsertChatContact(User user, User contact, String lastMessage, String messageType) {
        ChatContact chatContact = chatContactRepository
                .findByUserIdAndContactId(user.getId(), contact.getId())
                .orElseGet(() -> {
                    ChatContact c = new ChatContact();
                    c.setUser(user);
                    c.setContact(contact);
                    return c;
                });
        chatContact.setLastMessageAt(java.time.LocalDateTime.now());
        chatContact.setLastMessage(lastMessage);
        chatContact.setMessageType(messageType);
        chatContactRepository.save(chatContact);
    }
}