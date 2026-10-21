package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.service.CurrentUserService;
import com.doubleclick.wadii.dto.LinksDto;
import com.doubleclick.wadii.entities.Links;
import com.doubleclick.wadii.entities.Provider;
import com.doubleclick.wadii.exception.ApiException;
import com.doubleclick.wadii.repository.LinksRepository;
import com.doubleclick.wadii.ts.Controller;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/links")
public class LinksController extends Controller<Links, LinksDto, Long> {

    private final LinksRepository linksRepository;
    private final CurrentUserService currentUserService;

    @Override
    public ResponseEntity<Response<Links>> show(Long id) {
        return linksRepository.findById(id)
                .map(link -> Response.response(link, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no link with this id : " + id, ResponseType.NOT_FOUND));
    }

    /** The link is added to the authenticated provider; providerId in the body is ignored. */
    @Override
    public ResponseEntity<Response<Links>> insert(Authentication authentication, @RequestBody LinksDto dto) {
        Provider provider = currentUserService.provider(authentication);
        if (dto.getLink() == null || dto.getLink().trim().isEmpty()) {
            throw ApiException.badRequest("link is required");
        }
        if (linksRepository.findByLink(dto.getLink()).isPresent()) {
            throw ApiException.conflict("this link is already exist : " + dto.getLink());
        }
        Links link = new Links();
        link.setLink(dto.getLink());
        link.setProvider(provider);
        return Response.response(linksRepository.save(link), "Link saved successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<Links>> update(Authentication authentication, LinksDto dto) {
        Provider provider = currentUserService.provider(authentication);
        if (dto.getId() == null) {
            throw ApiException.badRequest("link id is required");
        }
        if (dto.getLink() == null || dto.getLink().trim().isEmpty()) {
            throw ApiException.badRequest("link is required");
        }
        Links link = linksRepository.findById(dto.getId())
                .orElseThrow(() -> ApiException.notFound("there is no link with this id : " + dto.getId()));
        requireOwner(link, provider);
        Optional<Links> existing = linksRepository.findByLink(dto.getLink());
        if (existing.isPresent() && !existing.get().getId().equals(link.getId())) {
            throw ApiException.conflict("this link is already exist : " + dto.getLink());
        }
        link.setLink(dto.getLink());
        return Response.response(linksRepository.save(link), "Link updated successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<Boolean>> delete(Authentication authentication, Long id) {
        Provider provider = currentUserService.provider(authentication);
        Links link = linksRepository.findById(id).orElse(null);
        if (link == null) {
            return Response.response(false, "there is no link with this id : " + id, ResponseType.NOT_FOUND);
        }
        requireOwner(link, provider);
        linksRepository.deleteById(id);
        return Response.response(true, "link deleted successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<List<Links>>> readAll() {
        return Response.response(linksRepository.findAll(), "All Links", ResponseType.SUCCESS);
    }

    private void requireOwner(Links link, Provider provider) {
        if (link.getProvider() == null || !link.getProvider().getId().equals(provider.getId())) {
            throw ApiException.forbidden("You can only manage your own links");
        }
    }
}
