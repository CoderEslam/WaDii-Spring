package com.doubleclick.wadii.configuration;

import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WaDiiConfig {

    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }

}
