package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.WorkTimeDto;
import com.doubleclick.wadii.entities.Branch;
import com.doubleclick.wadii.entities.Role;
import com.doubleclick.wadii.entities.WorkTime;
import com.doubleclick.wadii.repository.BranchRepository;
import com.doubleclick.wadii.repository.WorkTimeRepository;
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
@RequestMapping("/work-time")
public class WorkTimeController extends Controller<WorkTime, WorkTimeDto, Long> {

    private final WorkTimeRepository workTimeRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;

    @Override
    public ResponseEntity<Response<WorkTime>> show(Long id) {
        return workTimeRepository.findById(id)
                .map(workTime -> Response.response(workTime, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no time with this id : " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<WorkTime>> insert(Authentication authentication, @RequestBody WorkTimeDto workTimeDto) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "User not exist", ResponseType.SUCCESS);
        }
        if (userOptional.get().getRole() != Role.PROVIDER) {
            return Response.response(null, "You are not a provider to do this action", ResponseType.SUCCESS);
        }
        if (workTimeDto.isNotEmpty()) {
            Optional<Branch> branchOptional = branchRepository.findById(workTimeDto.getBranchId());
            if (branchOptional.isPresent()) {
                WorkTime workTime = new WorkTime();
                workTime.setStartTime(workTimeDto.getStartTime());
                workTime.setCloseTime(workTimeDto.getCloseTime());
                workTime.setDay(workTimeDto.getDay());
                workTime.setBranch(branchOptional.get());
                workTime = workTimeRepository.save(workTime);
                return Response.response(workTime, "Work time saved successfully", ResponseType.SUCCESS);
            } else {
                return Response.response(null, "there is no branch with this id : " + workTimeDto.getBranchId(), ResponseType.NOT_FOUND);
            }
        } else {
            return Response.response(null, "name is empty", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<WorkTime>> update(Authentication authentication, WorkTimeDto workTimeDto) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "User not exist", ResponseType.SUCCESS);
        }
        if (userOptional.get().getRole() != Role.PROVIDER) {
            return Response.response(null, "You are not a provider to do this action", ResponseType.SUCCESS);
        }
        if (workTimeDto.isNotEmpty()) {
            Optional<Branch> branchOptional = branchRepository.findById(workTimeDto.getBranchId());
            if (branchOptional.isPresent()) {
                Optional<WorkTime> workTimeOptional = workTimeRepository.findById(workTimeDto.getId());
                if (workTimeOptional.isPresent()) {
                    WorkTime workTime = workTimeOptional.get();
                    workTime.setId(workTimeDto.getId());
                    workTime.setStartTime(workTimeDto.getStartTime());
                    workTime.setCloseTime(workTimeDto.getCloseTime());
                    workTime.setDay(workTimeDto.getDay());
                    workTime.setBranch(branchOptional.get());
                    workTime = workTimeRepository.save(workTime);
                    return Response.response(workTime, "Work time updated successfully", ResponseType.SUCCESS);
                } else {
                    return Response.response(null, "there is no work time with this id : " + workTimeDto.getId(), ResponseType.NOT_FOUND);
                }
            } else {
                return Response.response(null, "there is no branch with this id : " + workTimeDto.getBranchId(), ResponseType.NOT_FOUND);
            }
        } else {
            return Response.response(null, "name is empty", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<WorkTime>> delete(Authentication authentication, Long id) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isEmpty()) {
            return Response.response(null, "User not exist", ResponseType.SUCCESS);
        }
        if (userOptional.get().getRole() != Role.PROVIDER) {
            return Response.response(null, "You are not a provider to do this action", ResponseType.SUCCESS);
        }
        Optional<WorkTime> workTimeOptional = workTimeRepository.findById(id);
        if (workTimeOptional.isPresent()) {
            workTimeRepository.deleteById(id);
            return Response.response(null, "work time deleted successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "there is no work time with this id : " + id, ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<List<WorkTime>>> readAll() {
        return Response.response(workTimeRepository.findAll(), "All times", ResponseType.SUCCESS);
    }

    @PostMapping("/get-all-work-time/{branchId}")
    public ResponseEntity<Response<List<WorkTime>>> getWorkTimeOfBranchById(@PathVariable Long branchId) {
        return Response.response(workTimeRepository.findAllByBranchId(branchId), "All times", ResponseType.SUCCESS);
    }
}
