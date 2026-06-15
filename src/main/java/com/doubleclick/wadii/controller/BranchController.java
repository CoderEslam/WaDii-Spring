package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.dto.BranchDto;
import com.doubleclick.wadii.entities.Branch;
import com.doubleclick.wadii.repository.BranchRepository;
import com.doubleclick.wadii.repository.ProviderRepository;
import com.doubleclick.wadii.ts.Controller;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/branches")
public class BranchController extends Controller<Branch, BranchDto, Long> {

    private final BranchRepository branchRepository;
    private final ProviderRepository providerRepository;

    @Override
    public ResponseEntity<Response<Branch>> show(Long id) {
        return branchRepository.findById(id)
                .map(branch -> Response.response(branch, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no branch with this id : " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<Branch>> insert(Authentication authentication, @RequestBody BranchDto branchDto) {
        if (branchDto.getName() == null || branchDto.getName().trim().isEmpty()) {
            return Response.response(null, "branch name is required", ResponseType.ERROR);
        }
        return providerRepository.findById(branchDto.getProviderId())
                .map(provider -> {
                    Branch branch = new Branch();
                    branch.setName(branchDto.getName());
                    branch.setAddress(branchDto.getAddress());
                    branch.setProvider(provider);
                    Branch saved = branchRepository.save(branch);
                    return Response.response(saved, "branch saved successfully", ResponseType.SUCCESS);
                })
                .orElseGet(() -> Response.response(null, "there is no provider with this id : " + branchDto.getProviderId(), ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<Branch>> update(@RequestBody BranchDto branchDto) {
        if (branchDto.getId() == null) {
            return Response.response(null, "branch id is required", ResponseType.ERROR);
        }
        Optional<Branch> branchOptional = branchRepository.findById(branchDto.getId());
        if (branchOptional.isEmpty()) {
            return Response.response(null, "there is no branch with this id : " + branchDto.getId(), ResponseType.NOT_FOUND);
        }
        Branch branch = branchOptional.get();
        if (branchDto.getName() != null) branch.setName(branchDto.getName());
        if (branchDto.getAddress() != null) branch.setAddress(branchDto.getAddress());
        if (branchDto.getProviderId() != null) {
            return providerRepository.findById(branchDto.getProviderId())
                    .map(provider -> {
                        branch.setProvider(provider);
                        Branch saved = branchRepository.save(branch);
                        return Response.response(saved, "branch updated successfully", ResponseType.SUCCESS);
                    })
                    .orElseGet(() -> Response.response(null, "there is no provider with this id : " + branchDto.getProviderId(), ResponseType.NOT_FOUND));
        }
        Branch saved = branchRepository.save(branch);
        return Response.response(saved, "branch updated successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<Branch>> delete(Long id) {
        Optional<Branch> branchOptional = branchRepository.findById(id);
        if (branchOptional.isPresent()) {
            branchRepository.deleteById(id);
            return Response.response(null, "branch deleted successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "there is no branch with this id : " + id, ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<List<Branch>>> readAll() {
        return Response.response(branchRepository.findAll(), "All branches", ResponseType.SUCCESS);
    }

    @GetMapping("/by-provider/{providerId}")
    public ResponseEntity<Response<List<Branch>>> getBranchesByProvider(@PathVariable Long providerId) {
        return Response.response(branchRepository.findAllByProviderId(providerId), "Branches for provider", ResponseType.SUCCESS);
    }
}
