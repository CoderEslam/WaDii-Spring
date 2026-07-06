package com.doubleclick.wadii.ts;

import com.doubleclick.wadii.utils.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

public abstract class Controller<Entity, DTO, ID> {

    @GetMapping("/show/{id}")
    public abstract ResponseEntity<Response<Entity>> show(@PathVariable ID id);

    @PostMapping("/insert")
    public abstract ResponseEntity<Response<Entity>> insert(Authentication authentication, @RequestBody DTO dto);

    @PostMapping("/update")
    public abstract ResponseEntity<Response<Entity>> update(Authentication authentication, @RequestBody DTO dto);

    @DeleteMapping("/delete/{id}")
    public abstract ResponseEntity<Response<Entity>> delete(Authentication authentication, @PathVariable ID id);

    @GetMapping("/show-all")
    public abstract ResponseEntity<Response<List<Entity>>> readAll();

}
