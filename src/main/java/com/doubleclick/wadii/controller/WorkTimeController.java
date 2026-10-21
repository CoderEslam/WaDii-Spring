package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.service.CurrentUserService;
import com.doubleclick.wadii.dto.WorkTimeDto;
import com.doubleclick.wadii.entities.Branch;
import com.doubleclick.wadii.entities.Provider;
import com.doubleclick.wadii.entities.WorkTime;
import com.doubleclick.wadii.exception.ApiException;
import com.doubleclick.wadii.repository.BranchRepository;
import com.doubleclick.wadii.repository.WorkTimeRepository;
import com.doubleclick.wadii.ts.Controller;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/work-time")
public class WorkTimeController extends Controller<WorkTime, WorkTimeDto, Long> {

    private final WorkTimeRepository workTimeRepository;
    private final BranchRepository branchRepository;
    private final CurrentUserService currentUserService;

    @Override
    public ResponseEntity<Response<WorkTime>> show(Long id) {
        return workTimeRepository.findById(id)
                .map(workTime -> Response.response(workTime, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no time with this id : " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<WorkTime>> insert(Authentication authentication, @RequestBody WorkTimeDto dto) {
        Provider provider = currentUserService.provider(authentication);
        requireFields(dto);
        Branch branch = ownedBranch(dto.getBranchId(), provider);
        WorkTime workTime = new WorkTime();
        workTime.setStartTime(dto.getStartTime());
        workTime.setCloseTime(dto.getCloseTime());
        workTime.setDay(dto.getDay());
        workTime.setBranch(branch);
        return Response.response(workTimeRepository.save(workTime), "Work time saved successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<WorkTime>> update(Authentication authentication, WorkTimeDto dto) {
        Provider provider = currentUserService.provider(authentication);
        if (dto.getId() == null) {
            throw ApiException.badRequest("work time id is required");
        }
        requireFields(dto);
        WorkTime workTime = workTimeRepository.findById(dto.getId())
                .orElseThrow(() -> ApiException.notFound("there is no work time with this id : " + dto.getId()));
        requireOwner(workTime, provider);
        // Moving a work time to another branch is only allowed between the provider's own branches.
        workTime.setBranch(ownedBranch(dto.getBranchId(), provider));
        workTime.setStartTime(dto.getStartTime());
        workTime.setCloseTime(dto.getCloseTime());
        workTime.setDay(dto.getDay());
        return Response.response(workTimeRepository.save(workTime), "Work time updated successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<Boolean>> delete(Authentication authentication, Long id) {
        Provider provider = currentUserService.provider(authentication);
        WorkTime workTime = workTimeRepository.findById(id).orElse(null);
        if (workTime == null) {
            return Response.response(false, "there is no work time with this id : " + id, ResponseType.NOT_FOUND);
        }
        requireOwner(workTime, provider);
        workTimeRepository.deleteById(id);
        return Response.response(true, "work time deleted successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<List<WorkTime>>> readAll() {
        return Response.response(workTimeRepository.findAll(), "All times", ResponseType.SUCCESS);
    }

    @PostMapping("/get-all-work-time/{branchId}")
    public ResponseEntity<Response<List<WorkTime>>> getWorkTimeOfBranchById(@PathVariable Long branchId) {
        return Response.response(workTimeRepository.findAllByBranchId(branchId), "All times", ResponseType.SUCCESS);
    }

    private void requireFields(WorkTimeDto dto) {
        if (!dto.isNotEmpty() || dto.getBranchId() == null) {
            throw ApiException.badRequest("branchId, startTime, closeTime and day are required");
        }
    }

    private Branch ownedBranch(Long branchId, Provider provider) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> ApiException.notFound("there is no branch with this id : " + branchId));
        if (branch.getProvider() == null || !branch.getProvider().getId().equals(provider.getId())) {
            throw ApiException.forbidden("You can only manage your own branches");
        }
        return branch;
    }

    private void requireOwner(WorkTime workTime, Provider provider) {
        if (workTime.getBranch() == null || workTime.getBranch().getProvider() == null
                || !workTime.getBranch().getProvider().getId().equals(provider.getId())) {
            throw ApiException.forbidden("You can only manage your own work times");
        }
    }
}
