package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.service.CurrentUserService;
import com.doubleclick.wadii.dto.BranchDto;
import com.doubleclick.wadii.entities.Branch;
import com.doubleclick.wadii.entities.Provider;
import com.doubleclick.wadii.exception.ApiException;
import com.doubleclick.wadii.repository.BranchRepository;
import com.doubleclick.wadii.ts.Controller;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/branches")
public class BranchController extends Controller<Branch, BranchDto, Long> {

    private final BranchRepository branchRepository;
    private final CurrentUserService currentUserService;

    @Override
    public ResponseEntity<Response<Branch>> show(Long id) {
        return branchRepository.findById(id)
                .map(branch -> Response.response(branch, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no branch with this id : " + id, ResponseType.NOT_FOUND));
    }

    /** The branch is created for the authenticated provider; providerId in the body is ignored. */
    @Override
    public ResponseEntity<Response<Branch>> insert(Authentication authentication, @RequestBody BranchDto dto) {
        Provider provider = currentUserService.provider(authentication);
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw ApiException.badRequest("branch name is required");
        }
        Branch branch = new Branch();
        branch.setName(dto.getName());
        branch.setAddress(dto.getAddress());
        branch.setProvider(provider);
        return Response.response(branchRepository.save(branch), "branch saved successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<Branch>> update(Authentication authentication, @RequestBody BranchDto dto) {
        Provider provider = currentUserService.provider(authentication);
        if (dto.getId() == null) {
            throw ApiException.badRequest("branch id is required");
        }
        Branch branch = branchRepository.findById(dto.getId())
                .orElseThrow(() -> ApiException.notFound("there is no branch with this id : " + dto.getId()));
        requireOwner(branch, provider);
        if (dto.getName() != null) branch.setName(dto.getName());
        if (dto.getAddress() != null) branch.setAddress(dto.getAddress());
        return Response.response(branchRepository.save(branch), "branch updated successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<Boolean>> delete(Authentication authentication, Long id) {
        Provider provider = currentUserService.provider(authentication);
        Branch branch = branchRepository.findById(id).orElse(null);
        if (branch == null) {
            return Response.response(false, "there is no branch with this id : " + id, ResponseType.NOT_FOUND);
        }
        requireOwner(branch, provider);
        branchRepository.deleteById(id);
        return Response.response(true, "branch deleted successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<List<Branch>>> readAll() {
        return Response.response(branchRepository.findAll(), "All branches", ResponseType.SUCCESS);
    }

    @GetMapping("/by-provider/{providerId}")
    public ResponseEntity<Response<List<Branch>>> getBranchesByProvider(@PathVariable Long providerId) {
        return Response.response(branchRepository.findAllByProviderId(providerId), "Branches for provider", ResponseType.SUCCESS);
    }

    private void requireOwner(Branch branch, Provider provider) {
        if (branch.getProvider() == null || !branch.getProvider().getId().equals(provider.getId())) {
            throw ApiException.forbidden("You can only manage your own branches");
        }
    }
}
