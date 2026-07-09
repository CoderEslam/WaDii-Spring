package com.doubleclick.wadii.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CallSignal {
    private String channelName;
    private String callType; // "VIDEO" or "AUDIO"
    private Long fromUserId;
    private Long toUserId;
    private String fromUserName;
    private String fromUserImage;
    private String provider; // "AGORA" or "LIVEKIT" — same signal shape for both, relayed opaquely
}
