package com.doubleclick.wadii.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProviderRequestDto {
    private String name;
    private Long userId;
    private MultipartFile frontIdImage;
    private MultipartFile backIdImage;
    private String address;
    private String phoneNumber;
    private List<Long> serviceIds;
    private List<String> links;
}