package com.doubleclick.wadii.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LinksDto {

    private Long id;
    private Long providerId;
    private String link;

    public boolean isNotEmpty() {
        return link != null && !link.trim().isEmpty() && providerId != null;
    }
}
