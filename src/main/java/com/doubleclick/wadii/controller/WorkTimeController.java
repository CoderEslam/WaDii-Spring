package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.dto.WorkTimeDto;
import com.doubleclick.wadii.entities.Provider;
import com.doubleclick.wadii.entities.WorkTime;
import com.doubleclick.wadii.repository.ProviderRepository;
import com.doubleclick.wadii.repository.WorkTimeRepository;
import com.doubleclick.wadii.ts.Controller;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/work-time")
public class WorkTimeController extends Controller<WorkTime, WorkTimeDto, Long> {

    private final WorkTimeRepository workTimeRepository;
    private final ProviderRepository providerRepository;

    @Override
    public ResponseEntity<Response<WorkTime>> show(Long id) {
        return workTimeRepository.findById(id)
                .map(workTime -> Response.response(workTime, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no time with this id : " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<WorkTime>> insert(Authentication authentication, WorkTimeDto workTimeDto) {
        if (workTimeDto.isNotEmpty()) {
            Optional<Provider> providerOptional = providerRepository.findById(workTimeDto.getProviderId());
            if (providerOptional.isPresent()) {
                WorkTime workTime = new WorkTime();
                workTime.setStartTime(workTimeDto.getStartTime());
                workTime.setCloseTime(workTimeDto.getCloseTime());
                workTime.setDay(workTimeDto.getDay());
                workTime.setProvider(providerOptional.get());
                workTime = workTimeRepository.save(workTime);
                return Response.response(workTime, "Work time saved successfully", ResponseType.SUCCESS);
            } else {
                return Response.response(null, "there is no provider with this id : " + workTimeDto.getProviderId(), ResponseType.NOT_FOUND);
            }
        } else {
            return Response.response(null, "name is empty", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<WorkTime>> update(WorkTimeDto workTimeDto) {
        if (workTimeDto.isNotEmpty()) {
            Optional<Provider> providerOptional = providerRepository.findById(workTimeDto.getProviderId());
            if (providerOptional.isPresent()) {
                Optional<WorkTime> workTimeOptional = workTimeRepository.findById(workTimeDto.getId());
                if (workTimeOptional.isPresent()) {
                    WorkTime workTime = workTimeOptional.get();
                    workTime.setId(workTimeDto.getId());
                    workTime.setStartTime(workTimeDto.getStartTime());
                    workTime.setCloseTime(workTimeDto.getCloseTime());
                    workTime.setDay(workTimeDto.getDay());
                    workTime.setProvider(providerOptional.get());
                    workTime = workTimeRepository.save(workTime);
                    return Response.response(workTime, "Work time updated successfully", ResponseType.SUCCESS);
                } else {
                    return Response.response(null, "there is no work time with this id : " + workTimeDto.getId(), ResponseType.NOT_FOUND);
                }
            } else {
                return Response.response(null, "there is no provider with this id : " + workTimeDto.getProviderId(), ResponseType.NOT_FOUND);
            }
        } else {
            return Response.response(null, "name is empty", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<WorkTime>> delete(Long id) {
        Optional<WorkTime> carType = workTimeRepository.findById(id);
        if (carType.isPresent()) {
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

    @PostMapping("/get-all-work-time/{id}")
    public ResponseEntity<Response<List<WorkTime>>> getWorkTimeOfProviderById(@PathVariable Long id) {
        return Response.response(workTimeRepository.findAllByProviderId(id), "All times", ResponseType.SUCCESS);
    }
}
