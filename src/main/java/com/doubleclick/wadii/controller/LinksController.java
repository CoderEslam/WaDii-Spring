package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.dto.LinksDto;
import com.doubleclick.wadii.entities.Links;
import com.doubleclick.wadii.entities.Provider;
import com.doubleclick.wadii.repository.LinksRepository;
import com.doubleclick.wadii.repository.ProviderRepository;
import com.doubleclick.wadii.ts.Controller;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/links")
public class LinksController extends Controller<Links, LinksDto, Long> {

    private final LinksRepository linksRepository;
    private final ProviderRepository providerRepository;

    @Override
    public ResponseEntity<Response<Links>> show(Long id) {
        return linksRepository.findById(id)
                .map(link -> Response.response(link, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no link with this id : " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<Links>> insert(Authentication authentication, LinksDto linksDto) {
        if (linksDto.isNotEmpty()) {
            Optional<Provider> providerOptional = providerRepository.findById(linksDto.getProviderId());
            if (providerOptional.isPresent()) {
                Optional<Links> linksOptional = linksRepository.findByLink(linksDto.getLink());
                if (linksOptional.isEmpty()) {
                    Links link = new Links();
                    link.setLink(linksDto.getLink());
                    link.setProvider(providerOptional.get());
                    link = linksRepository.save(link);
                    return Response.response(link, "Link saved successfully", ResponseType.SUCCESS);
                } else {
                    return Response.response(null, "this link is already exist : " + linksDto.getLink(), ResponseType.ERROR);
                }
            } else {
                return Response.response(null, "there is no provider with this id : " + linksDto.getProviderId(), ResponseType.NOT_FOUND);
            }
        } else {
            return Response.response(null, "name is empty", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<Links>> update(LinksDto linksDto) {
        if (linksDto.isNotEmpty()) {
            Optional<Provider> providerOptional = providerRepository.findById(linksDto.getProviderId());
            if (providerOptional.isPresent()) {
                Optional<Links> linkOptional = linksRepository.findById(linksDto.getId());
                if (linkOptional.isPresent()) {
                    Links link = linkOptional.get();
                    link.setId(link.getId());
                    link.setLink(linksDto.getLink());
                    link.setProvider(providerOptional.get());
                    link = linksRepository.save(link);
                    return Response.response(link, "Link updated successfully", ResponseType.SUCCESS);
                } else {
                    return Response.response(null, "there is no link with this id : " + linksDto.getId(), ResponseType.NOT_FOUND);
                }
            } else {
                return Response.response(null, "there is no provider with this id : " + linksDto.getProviderId(), ResponseType.NOT_FOUND);
            }
        } else {
            return Response.response(null, "name is empty", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<Links>> delete(Long id) {
        Optional<Links> cityOptional = linksRepository.findById(id);
        if (cityOptional.isPresent()) {
            linksRepository.deleteById(id);
            return Response.response(null, "link deleted successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "there is no link with this id : " + id, ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<List<Links>>> readAll() {
        return Response.response(linksRepository.findAll(), "All Links", ResponseType.SUCCESS);
    }
}
